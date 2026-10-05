package fptu.exe202.signify.signifybe.features.chat.api;

import fptu.exe202.signify.signifybe.features.auth.application.AccountAccessService;
import fptu.exe202.signify.signifybe.features.auth.application.JwtService;
import fptu.exe202.signify.signifybe.features.auth.application.SessionAccessService;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.Role;
import fptu.exe202.signify.signifybe.features.call.application.VideoCallService;
import fptu.exe202.signify.signifybe.features.call.domain.exception.VideoCallException;
import fptu.exe202.signify.signifybe.features.chat.application.PrivateChatAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VideoCallStompAuthorizationTest {
    private final CurrentUser user = new CurrentUser(10, 1, Role.USER);
    private VideoCallService calls;
    private ChatStompAuthorization authorization;

    @BeforeEach
    void setUp() {
        calls = mock(VideoCallService.class);
        authorization = new ChatStompAuthorization(mock(JwtService.class),
                mock(SessionAccessService.class), mock(AccountAccessService.class),
                mock(PrivateChatAccessService.class), calls);
    }

    @Test
    void activeParticipantMaySubscribeToCallQueue() {
        Message<?> message = subscription("/user/queue/calls/42", user);
        assertSame(message, authorization.preSend(message, null));
        verify(calls).validateActiveCall(42L, user);
    }

    @Test
    void nonparticipantCannotSubscribeToCallQueue() {
        doThrow(VideoCallException.forbidden()).when(calls).validateActiveCall(42L, user);
        assertThrows(AccessDeniedException.class,
                () -> authorization.preSend(subscription("/user/queue/calls/42", user), null));
    }

    @Test
    void unauthenticatedUserCannotSubscribeToCallQueue() {
        assertThrows(AccessDeniedException.class,
                () -> authorization.preSend(subscription("/user/queue/calls/42", null), null));
        verifyNoInteractions(calls);
    }

    @Test
    void directBrokerSubscriptionIsDenied() {
        assertThrows(AccessDeniedException.class,
                () -> authorization.preSend(subscription("/queue/calls/42", user), null));
        verifyNoInteractions(calls);
    }

    private static Message<?> subscription(String destination, CurrentUser user) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setDestination(destination);
        headers.setUser(user);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
