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
    void activeParticipantMaySubscribeToWebRtcQueueAndSendSignals() {
        Message<?> subscription = subscription("/user/queue/calls/42/webrtc", user);
        Message<?> send = signal("/app/calls/42/webrtc", user);
        assertSame(subscription, authorization.preSend(subscription, null));
        assertSame(send, authorization.preSend(send, null));
        verify(calls, times(2)).validateActiveCall(42L, user);
    }

    @Test
    void inactiveOrNonparticipantCannotSendOrSubscribe() {
        for (VideoCallException failure : new VideoCallException[]{VideoCallException.forbidden(),
                VideoCallException.notFound(), VideoCallException.notActive()}) {
            reset(calls);
            doThrow(failure).when(calls).validateActiveCall(42L, user);
            assertThrows(AccessDeniedException.class,
                    () -> authorization.preSend(subscription("/user/queue/calls/42/webrtc", user), null));
            assertThrows(AccessDeniedException.class,
                    () -> authorization.preSend(signal("/app/calls/42/webrtc", user), null));
        }
    }

    @Test
    void unauthenticatedAndUnscopedSignalingIsDenied() {
        assertThrows(AccessDeniedException.class,
                () -> authorization.preSend(signal("/app/calls/42/webrtc", null), null));
        for (String destination : new String[]{"/queue/calls/42/webrtc", "/user/20/queue/calls/42/webrtc",
                "/app/calls/42/webrtc/other", "/app/calls/0/webrtc"}) {
            assertThrows(AccessDeniedException.class,
                    () -> authorization.preSend(signal(destination, user), null));
        }
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

    @Test
    void authenticatedUserMaySubscribeToOwnIncomingAndStatusQueues() {
        for (String destination : new String[]{"/user/queue/calls/incoming", "/user/queue/calls/status"}) {
            Message<?> message = subscription(destination, user);
            assertSame(message, authorization.preSend(message, null));
        }
        verifyNoInteractions(calls);
    }

    @Test
    void unauthenticatedUserCannotSubscribeToIncomingOrStatus() {
        for (String destination : new String[]{"/user/queue/calls/incoming", "/user/queue/calls/status"}) {
            assertThrows(AccessDeniedException.class,
                    () -> authorization.preSend(subscription(destination, null), null));
        }
    }

    @Test
    void directBrokerAndForeignUserDestinationsAreDenied() {
        for (String destination : new String[]{"/queue/calls/incoming", "/queue/calls/status",
                "/user/20/queue/calls/incoming", "/user/20/queue/calls/status"}) {
            assertThrows(AccessDeniedException.class,
                    () -> authorization.preSend(subscription(destination, user), null));
        }
    }

    @Test
    void clientsCannotSendCallEvents() {
        for (String destination : new String[]{"/user/queue/calls/incoming", "/user/queue/calls/status"}) {
            StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SEND);
            headers.setDestination(destination);
            headers.setUser(user);
            Message<?> message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
            assertThrows(AccessDeniedException.class, () -> authorization.preSend(message, null));
        }
    }

    private static Message<?> subscription(String destination, CurrentUser user) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setDestination(destination);
        headers.setUser(user);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    private static Message<?> signal(String destination, CurrentUser user) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SEND);
        headers.setDestination(destination);
        headers.setUser(user);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
