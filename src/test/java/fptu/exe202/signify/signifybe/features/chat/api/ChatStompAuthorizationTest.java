package fptu.exe202.signify.signifybe.features.chat.api;

import fptu.exe202.signify.signifybe.features.auth.application.*;
import fptu.exe202.signify.signifybe.features.auth.domain.*;
import fptu.exe202.signify.signifybe.features.chat.application.PrivateChatAccessService;
import fptu.exe202.signify.signifybe.features.chat.domain.exception.ConversationException;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatStompAuthorizationTest {
    private final JwtService jwt = mock(JwtService.class);
    private final SessionAccessService sessions = mock(SessionAccessService.class);
    private final AccountAccessService accounts = mock(AccountAccessService.class);
    private final PrivateChatAccessService access = mock(PrivateChatAccessService.class);
    private final ChatStompAuthorization interceptor = new ChatStompAuthorization(jwt, sessions, accounts, access);
    private final CurrentUser user = new CurrentUser(1, 9, Role.USER);

    private Message<byte[]> frame(StompCommand command, String destination, CurrentUser principal) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(command);
        if (destination != null) headers.setDestination(destination);
        if (principal != null) headers.setUser(principal);
        headers.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    @Test void connectRequiresValidBearerAndActiveAccount() {
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(frame(StompCommand.CONNECT, null, null), null));
        when(jwt.validateAccessToken("valid")).thenReturn(user);
        when(accounts.requireActiveUser(1, 9)).thenReturn(user);
        var frame = frame(StompCommand.CONNECT, null, null);
        StompHeaderAccessor.getAccessor(frame, StompHeaderAccessor.class).setNativeHeader("Authorization", "Bearer valid");
        var accepted = interceptor.preSend(frame, null);
        assertEquals(user, StompHeaderAccessor.getAccessor(accepted, StompHeaderAccessor.class).getUser());
        verify(sessions).requireActiveSession(9, 1);
    }

    @Test void subscriptionRequiresMembershipAndExactUserDestination() {
        when(accounts.requireActiveUser(1, 9)).thenReturn(user);
        when(access.activeParticipantIds(7, 1)).thenReturn(List.of(1L, 2L));
        assertDoesNotThrow(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE,
                "/user/queue/conversations/7", user), null));
        when(access.activeParticipantIds(8, 1)).thenThrow(ConversationException.accessDenied());
        assertThrows(ConversationException.class, () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE,
                "/user/queue/conversations/8", user), null));
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE,
                "/queue/conversations/7", user), null));
    }

    @Test void sendCannotPublishToBrokerOrOtherApplicationDestinations() {
        when(accounts.requireActiveUser(1, 9)).thenReturn(user);
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(frame(StompCommand.SEND,
                "/queue/conversations/7", user), null));
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(frame(StompCommand.SEND,
                "/app/conversations/7/messages", user), null));
        when(access.unblockedPeer(7, 1)).thenReturn(2L);
        assertDoesNotThrow(() -> interceptor.preSend(frame(StompCommand.SEND,
                "/app/conversations/7/typing", user), null));
        verify(access).unblockedPeer(7, 1);
    }
}
