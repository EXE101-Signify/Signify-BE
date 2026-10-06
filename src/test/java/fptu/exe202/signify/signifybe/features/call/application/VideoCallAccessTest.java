package fptu.exe202.signify.signifybe.features.call.application;

import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.Role;
import fptu.exe202.signify.signifybe.features.auth.domain.exception.AuthException;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCall;
import fptu.exe202.signify.signifybe.features.call.domain.exception.VideoCallException;
import fptu.exe202.signify.signifybe.features.call.infrastructure.persistence.JpaVideoCallRepository;
import fptu.exe202.signify.signifybe.features.chat.application.PrivateChatAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VideoCallAccessTest {
    private static final CurrentUser CALLER = new CurrentUser(10, 1, Role.USER);
    private static final CurrentUser RECEIVER = new CurrentUser(20, 2, Role.USER);
    private static final CurrentUser STRANGER = new CurrentUser(30, 3, Role.USER);

    private JpaVideoCallRepository calls;
    private VideoCallService service;
    private VideoCall call;

    @BeforeEach
    void setUp() {
        calls = mock(JpaVideoCallRepository.class);
        service = new VideoCallService(calls, mock(PrivateChatAccessService.class), Clock.systemUTC(),
                mock(ApplicationEventPublisher.class));
        call = new VideoCall(7, CALLER.userId(), RECEIVER.userId(), 100);
        when(calls.findById(42L)).thenReturn(Optional.of(call));
    }

    @Test
    void activeCallerIsAllowed() {
        call.accept(200);
        assertSame(call, service.validateActiveCall(42L, CALLER));
    }

    @Test
    void activeReceiverIsAllowed() {
        call.accept(200);
        assertSame(call, service.validateActiveCall(42L, RECEIVER));
    }

    @Test
    void nonparticipantIsRejectedBeforeStatusCheck() {
        call.accept(200);
        assertEquals("Call access denied", assertThrows(VideoCallException.class,
                () -> service.validateActiveCall(42L, STRANGER)).getMessage());
    }

    @Test
    void nonexistentCallIsRejected() {
        assertEquals("Call not found", assertThrows(VideoCallException.class,
                () -> service.validateActiveCall(999L, CALLER)).getMessage());
    }

    @Test
    void callingCallIsRejected() { assertInactive(); }

    @Test
    void rejectedCallIsRejected() {
        call.reject();
        assertInactive();
    }

    @Test
    void missedCallIsRejected() {
        call.miss();
        assertInactive();
    }

    @Test
    void busyCallIsRejected() {
        call.busy();
        assertInactive();
    }

    @Test
    void completedCallIsRejected() {
        call.accept(200);
        call.complete(300);
        assertInactive();
    }

    @Test
    void unauthenticatedRequestIsRejectedBeforeLookup() {
        assertThrows(AuthException.class, () -> service.validateActiveCall(42L, null));
        verify(calls, never()).findById(anyLong());
    }

    @Test
    void changingCallIdCannotAccessAnotherUsersCall() {
        call.accept(200);
        VideoCall other = new VideoCall(8, STRANGER.userId(), 40, 100);
        other.accept(200);
        when(calls.findById(43L)).thenReturn(Optional.of(other));
        assertSame(call, service.validateActiveCall(42L, CALLER));
        assertThrows(VideoCallException.class, () -> service.validateActiveCall(43L, CALLER));
    }

    @Test
    void nullAndInvalidCallIdsAreRejectedBeforeLookup() {
        for (Long callId : new Long[]{null, 0L, -1L}) {
            assertEquals("Invalid call ID", assertThrows(VideoCallException.class,
                    () -> service.validateActiveCall(callId, CALLER)).getMessage());
        }
        verify(calls, never()).findById(anyLong());
    }

    private void assertInactive() {
        assertEquals("Call is not active", assertThrows(VideoCallException.class,
                () -> service.validateActiveCall(42L, CALLER)).getMessage());
    }
}
