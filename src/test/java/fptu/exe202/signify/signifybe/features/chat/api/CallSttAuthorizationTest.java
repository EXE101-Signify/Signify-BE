package fptu.exe202.signify.signifybe.features.chat.api;

import fptu.exe202.signify.signifybe.features.auth.application.AccountAccessService;
import fptu.exe202.signify.signifybe.features.auth.application.JwtService;
import fptu.exe202.signify.signifybe.features.auth.application.SessionAccessService;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.Role;
import fptu.exe202.signify.signifybe.features.call.application.VideoCallService;
import fptu.exe202.signify.signifybe.features.call.domain.exception.VideoCallException;
import fptu.exe202.signify.signifybe.features.chat.application.PrivateChatAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class CallSttAuthorizationTest {
    private final VideoCallService calls = mock(VideoCallService.class);
    private final ChatStompAuthorization authorization = new ChatStompAuthorization(
            mock(JwtService.class), mock(SessionAccessService.class), mock(AccountAccessService.class),
            mock(PrivateChatAccessService.class), calls);
    private final CurrentUser speaker = new CurrentUser(10, 1, Role.USER);

    @Test void onlyNonSignerCanSendAudioOrSubscribeToSttStatus() {
        Message<?> send = message(StompCommand.SEND, "/app/calls/42/stt");
        Message<?> subscribe = message(StompCommand.SUBSCRIBE, "/user/queue/calls/42/stt");
        assertDoesNotThrow(() -> authorization.preSend(send, null));
        assertDoesNotThrow(() -> authorization.preSend(subscribe, null));
        verify(calls, times(2)).requireNonSigner(42, speaker);

        when(calls.requireNonSigner(42, speaker)).thenThrow(VideoCallException.forbidden());
        assertThrows(AccessDeniedException.class, () -> authorization.preSend(send, null));
        assertThrows(AccessDeniedException.class, () -> authorization.preSend(subscribe, null));
    }

    private Message<?> message(StompCommand command, String destination) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(command);
        headers.setUser(speaker);
        headers.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
