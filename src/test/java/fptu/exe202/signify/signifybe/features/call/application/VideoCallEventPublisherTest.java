package fptu.exe202.signify.signifybe.features.call.application;

import fptu.exe202.signify.signifybe.features.call.domain.VideoCallStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class VideoCallEventPublisherTest {
    private SimpMessagingTemplate messaging;
    private VideoCallEventPublisher publisher;

    @BeforeEach
    void setUp() {
        messaging = mock(SimpMessagingTemplate.class);
        publisher = new VideoCallEventPublisher(messaging);
    }

    @Test
    void incomingCallingEventGoesOnlyToPersistedReceiver() {
        VideoCallEvent event = event(VideoCallEvent.INCOMING_CALL, VideoCallStatus.CALLING);
        publisher.afterCommit(event);
        verify(messaging).convertAndSendToUser("20", "/queue/calls/incoming", event);
        verifyNoMoreInteractions(messaging);
    }

    @Test
    void acceptedStatusGoesToBothParticipants() {
        assertBothParticipants(VideoCallStatus.ACCEPTED);
    }

    @Test
    void rejectedStatusGoesToBothParticipants() {
        assertBothParticipants(VideoCallStatus.REJECTED);
    }

    @Test
    void completedStatusGoesToBothParticipants() {
        assertBothParticipants(VideoCallStatus.COMPLETED);
    }

    @Test
    void oneFailedDeliveryDoesNotPreventTheOtherParticipant() {
        VideoCallEvent event = event(VideoCallEvent.CALL_STATUS_CHANGED, VideoCallStatus.ACCEPTED);
        doThrow(new IllegalStateException("broker unavailable"))
                .when(messaging).convertAndSendToUser("10", "/queue/calls/status", event);
        publisher.afterCommit(event);
        verify(messaging).convertAndSendToUser("20", "/queue/calls/status", event);
    }

    private void assertBothParticipants(VideoCallStatus status) {
        VideoCallEvent event = event(VideoCallEvent.CALL_STATUS_CHANGED, status);
        publisher.afterCommit(event);
        verify(messaging).convertAndSendToUser("10", "/queue/calls/status", event);
        verify(messaging).convertAndSendToUser("20", "/queue/calls/status", event);
        verifyNoMoreInteractions(messaging);
        assertEquals(status, event.status());
    }

    private static VideoCallEvent event(String type, VideoCallStatus status) {
        return new VideoCallEvent("event-1", type, 42, 7, 10, 20, status, 1720000000000L);
    }
}
