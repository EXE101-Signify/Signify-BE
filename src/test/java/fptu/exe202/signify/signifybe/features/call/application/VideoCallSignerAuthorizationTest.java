package fptu.exe202.signify.signifybe.features.call.application;

import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.Role;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCall;
import fptu.exe202.signify.signifybe.features.call.domain.exception.VideoCallException;
import fptu.exe202.signify.signifybe.features.call.infrastructure.persistence.JpaVideoCallRepository;
import fptu.exe202.signify.signifybe.features.chat.application.PrivateChatAccessService;
import fptu.exe202.signify.signifybe.features.payment.application.SubscriptionService;
import fptu.exe202.signify.signifybe.features.user.application.port.out.UserRepository;
import fptu.exe202.signify.signifybe.features.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VideoCallSignerAuthorizationTest {
    private final CurrentUser caller = new CurrentUser(10, 1, Role.USER);
    private final CurrentUser receiver = new CurrentUser(20, 2, Role.USER);
    private final JpaVideoCallRepository repository = mock(JpaVideoCallRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final VideoCallService calls = new VideoCallService(repository, mock(PrivateChatAccessService.class),
            Clock.systemUTC(), mock(ApplicationEventPublisher.class), mock(SubscriptionService.class),
            mock(CallExpiryService.class), users);
    private final VideoCall call = activeCall();

    private VideoCall activeCall() {
        VideoCall value = new VideoCall(7, caller.userId(), receiver.userId(), 100);
        value.accept(200);
        when(repository.findById(42L)).thenReturn(Optional.of(value));
        return value;
    }

    private void signer(long userId, boolean enabled) {
        User user = new User("person@example.com", "Person", "One", 100);
        user.setSigner(enabled);
        when(users.findUserById(userId)).thenReturn(Optional.of(user));
    }

    @Test void callerMayCaptureWhenOnlyCallerIsSigner() {
        signer(10, true);
        signer(20, false);
        assertEquals(10L, calls.signerId(call));
        assertSame(call, calls.requireSigner(42, caller));
        assertThrows(VideoCallException.class, () -> calls.requireSigner(42, receiver));
        assertSame(call, calls.requireNonSigner(42, receiver));
    }

    @Test void receiverCapturesWhenBothUseSignsAndBothMayChooseMode() {
        signer(10, true);
        signer(20, true);
        assertEquals(20L, calls.signerId(call));
        assertSame(call, calls.requireSigner(42, receiver));
        assertThrows(VideoCallException.class, () -> calls.requireSigner(42, caller));
        assertSame(call, calls.requireSignerAccount(42, caller));
        assertThrows(VideoCallException.class, () -> calls.requireNonSigner(42, caller));
    }

    @Test void neitherSignerMeansNoCameraCaptureAndBothMaySpeak() {
        signer(10, false);
        signer(20, false);
        assertNull(calls.signerId(call));
        assertThrows(VideoCallException.class, () -> calls.requireSigner(42, receiver));
        assertThrows(VideoCallException.class, () -> calls.requireSignerAccount(42, caller));
        assertSame(call, calls.requireNonSigner(42, caller));
    }
}
