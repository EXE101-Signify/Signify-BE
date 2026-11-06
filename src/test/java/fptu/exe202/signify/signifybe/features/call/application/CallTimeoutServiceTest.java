package fptu.exe202.signify.signifybe.features.call.application;

import fptu.exe202.signify.signifybe.features.call.domain.VideoCall;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCallStatus;
import fptu.exe202.signify.signifybe.features.call.infrastructure.persistence.JpaVideoCallRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CallTimeoutServiceTest {
    private final JpaVideoCallRepository repository = mock(JpaVideoCallRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final CallTimeoutService service = new CallTimeoutService(repository,
            Clock.fixed(Instant.ofEpochMilli(100_000), ZoneOffset.UTC), events, 60_000, 90_000);

    @Test void unansweredCallBecomesMissedAndNotifiesParticipants() {
        VideoCall call = call(10_000);
        when(repository.findLockedById(99)).thenReturn(Optional.of(call));

        assertTrue(service.closeIfTimedOut(99));
        assertEquals(VideoCallStatus.MISSED, call.getStatus());
        assertEquals(100_000L, call.getEndedAt());
        verify(events).publishEvent(any(VideoCallEvent.class));
    }

    @Test void connectedCallPastLimitCompletes() {
        VideoCall call = call(1_000);
        call.accept(5_000);
        when(repository.findLockedById(99)).thenReturn(Optional.of(call));

        assertTrue(service.closeIfTimedOut(99));
        assertEquals(VideoCallStatus.COMPLETED, call.getStatus());
        assertEquals(100_000L, call.getEndedAt());
        verify(events).publishEvent(any(VideoCallEvent.class));
    }

    @Test void legacyConnectedCallWithoutStartTimeUsesCreationTime() {
        VideoCall call = call(1_000);
        call.accept(5_000);
        ReflectionTestUtils.setField(call, "startedAt", null);
        when(repository.findLockedById(99)).thenReturn(Optional.of(call));

        assertTrue(service.closeIfTimedOut(99));
        assertEquals(VideoCallStatus.COMPLETED, call.getStatus());
    }

    @Test void rechecksStatusAndAgeUnderLockBeforeClosing() {
        VideoCall call = call(50_000);
        when(repository.findLockedById(99)).thenReturn(Optional.of(call));

        assertFalse(service.closeIfTimedOut(99));
        assertEquals(VideoCallStatus.CALLING, call.getStatus());
        call.accept(80_000);
        assertFalse(service.closeIfTimedOut(99));
        call.complete(90_000);
        assertFalse(service.closeIfTimedOut(99));
        verifyNoInteractions(events);
    }

    private static VideoCall call(long createdAt) {
        VideoCall call = new VideoCall(5, 10, 20, createdAt);
        ReflectionTestUtils.setField(call, "id", 99L);
        return call;
    }
}
