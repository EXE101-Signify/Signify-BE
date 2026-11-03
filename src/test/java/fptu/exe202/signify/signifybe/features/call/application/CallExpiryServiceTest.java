package fptu.exe202.signify.signifybe.features.call.application;

import fptu.exe202.signify.signifybe.features.call.domain.VideoCall;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCallStatus;
import fptu.exe202.signify.signifybe.features.call.infrastructure.persistence.JpaVideoCallRepository;
import fptu.exe202.signify.signifybe.features.payment.application.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CallExpiryServiceTest {
    @Test void closesCallAndPublishesStatusWhenOneParticipantExpires() {
        JpaVideoCallRepository repository = mock(JpaVideoCallRepository.class);
        SubscriptionService subscriptions = mock(SubscriptionService.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        VideoCall call = new VideoCall(5, 10, 20, 100);
        ReflectionTestUtils.setField(call, "id", 99L);
        call.accept(200);
        when(repository.findLockedById(99)).thenReturn(Optional.of(call));
        when(subscriptions.hasActiveSubscription(10)).thenReturn(true);
        CallExpiryService service = new CallExpiryService(repository, subscriptions,
                Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC), events);

        assertTrue(service.closeIfExpired(99));
        assertEquals(VideoCallStatus.COMPLETED, call.getStatus());
        assertEquals(1_000L, call.getEndedAt());
        verify(events).publishEvent(any(VideoCallEvent.class));
    }
}
