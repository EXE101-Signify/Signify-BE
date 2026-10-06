package fptu.exe202.signify.signifybe.features.ai.application;

import fptu.exe202.signify.signifybe.features.ai.api.dto.AiPredictionResponse;
import fptu.exe202.signify.signifybe.features.ai.domain.exception.AiException;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.Role;
import fptu.exe202.signify.signifybe.features.auth.domain.exception.AuthException;
import fptu.exe202.signify.signifybe.features.call.application.VideoCallService;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCall;
import fptu.exe202.signify.signifybe.features.call.domain.exception.VideoCallException;
import fptu.exe202.signify.signifybe.features.call.infrastructure.persistence.JpaVideoCallRepository;
import fptu.exe202.signify.signifybe.features.chat.application.PrivateChatAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Clock;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VideoCallAiPredictionTest {
    private static final CurrentUser CALLER = new CurrentUser(10, 1, Role.USER);
    private static final CurrentUser RECEIVER = new CurrentUser(20, 2, Role.USER);
    private static final CurrentUser STRANGER = new CurrentUser(30, 3, Role.USER);
    private static final byte[] IMAGE = {1, 2, 3};
    private static final AiPredictionResponse VALID = new AiPredictionResponse("A", 0.96, 1720000000000L);

    private JpaVideoCallRepository repository;
    private AiService ai;
    private SimpMessagingTemplate messaging;
    private AiPredictionService predictions;
    private VideoCall call;

    @BeforeEach
    void setUp() {
        repository = mock(JpaVideoCallRepository.class);
        ai = mock(AiService.class);
        messaging = mock(SimpMessagingTemplate.class);
        var calls = new VideoCallService(repository, mock(PrivateChatAccessService.class), Clock.systemUTC(),
                mock(ApplicationEventPublisher.class));
        predictions = new AiPredictionService(calls, ai, messaging);
        call = new VideoCall(7, CALLER.userId(), RECEIVER.userId(), 100);
        when(repository.findById(42L)).thenReturn(Optional.of(call));
    }

    @Test
    void acceptedCallerInvokesAiAndPublishesToBothParticipants() {
        call.accept(200);
        when(ai.predict(IMAGE, MediaType.IMAGE_JPEG)).thenReturn(VALID);
        AiPredictionEvent event = predict(CALLER);
        assertPayload(event);
        verify(ai, times(1)).predict(IMAGE, MediaType.IMAGE_JPEG);
        verifyRecipients(event);
    }

    @Test
    void acceptedReceiverInvokesAiAndPublishesToBothParticipants() {
        call.accept(200);
        when(ai.predict(IMAGE, MediaType.IMAGE_JPEG)).thenReturn(VALID);
        AiPredictionEvent event = predict(RECEIVER);
        assertPayload(event);
        verify(ai, times(1)).predict(IMAGE, MediaType.IMAGE_JPEG);
        verifyRecipients(event);
    }

    @Test
    void nonparticipantNeverInvokesAiOrPublishes() {
        call.accept(200);
        assertRejected(STRANGER);
    }

    @Test
    void nonexistentCallNeverInvokesAiOrPublishes() {
        assertThrows(VideoCallException.class,
                () -> predictions.predict(999, CALLER, IMAGE, MediaType.IMAGE_JPEG));
        verifyNoInteractions(ai, messaging);
    }

    @Test
    void callingCallNeverInvokesAiOrPublishes() { assertRejected(CALLER); }

    @Test
    void rejectedCallNeverInvokesAiOrPublishes() {
        call.reject();
        assertRejected(CALLER);
    }

    @Test
    void missedCallNeverInvokesAiOrPublishes() {
        call.miss();
        assertRejected(CALLER);
    }

    @Test
    void busyCallNeverInvokesAiOrPublishes() {
        call.busy();
        assertRejected(CALLER);
    }

    @Test
    void completedCallNeverInvokesAiOrPublishes() {
        call.accept(200);
        call.complete(300);
        assertRejected(CALLER);
    }

    @Test
    void unauthenticatedRequestNeverInvokesAiOrPublishes() {
        assertThrows(AuthException.class, () -> predict(null));
        verifyNoInteractions(ai, messaging);
    }

    @Test
    void changingCallIdCannotInvokeAiForAnotherCall() {
        VideoCall other = new VideoCall(8, STRANGER.userId(), 40, 100);
        other.accept(200);
        when(repository.findById(43L)).thenReturn(Optional.of(other));
        assertThrows(VideoCallException.class,
                () -> predictions.predict(43, CALLER, IMAGE, MediaType.IMAGE_JPEG));
        verifyNoInteractions(ai, messaging);
    }

    @Test
    void unavailableAiDoesNotEndCallOrPublish() {
        call.accept(200);
        when(ai.predict(IMAGE, MediaType.IMAGE_JPEG)).thenThrow(AiException.unavailable());
        assertThrows(AiException.class, () -> predict(CALLER));
        assertEquals(fptu.exe202.signify.signifybe.features.call.domain.VideoCallStatus.ACCEPTED, call.getStatus());
        verifyNoInteractions(messaging);
    }

    @Test
    void timeoutDoesNotEndCallOrPublish() {
        call.accept(200);
        when(ai.predict(IMAGE, MediaType.IMAGE_JPEG)).thenThrow(AiException.timeout());
        assertThrows(AiException.class, () -> predict(CALLER));
        assertEquals(fptu.exe202.signify.signifybe.features.call.domain.VideoCallStatus.ACCEPTED, call.getStatus());
        verifyNoInteractions(messaging);
    }

    @Test
    void invalidAiResponseIsNotPublished() {
        call.accept(200);
        when(ai.predict(IMAGE, MediaType.IMAGE_JPEG)).thenReturn(new AiPredictionResponse("?", 2.0, null));
        assertEquals("Invalid AI service response", assertThrows(AiException.class,
                () -> predict(CALLER)).getMessage());
        verifyNoInteractions(messaging);
    }

    @Test
    void endingDuringAiRequestPreventsLatePrediction() {
        call.accept(200);
        when(ai.predict(IMAGE, MediaType.IMAGE_JPEG)).thenAnswer(invocation -> {
            call.complete(300);
            return VALID;
        });
        assertThrows(VideoCallException.class, () -> predict(CALLER));
        verifyNoInteractions(messaging);
    }

    private AiPredictionEvent predict(CurrentUser actor) {
        return predictions.predict(42, actor, IMAGE, MediaType.IMAGE_JPEG);
    }

    private void assertRejected(CurrentUser actor) {
        assertThrows(VideoCallException.class, () -> predict(actor));
        verifyNoInteractions(ai, messaging);
    }

    private void assertPayload(AiPredictionEvent event) {
        assertEquals(AiPredictionEvent.TYPE, event.type());
        assertEquals(42, event.callId());
        assertEquals(7, event.conversationId());
        assertEquals("A", event.letter());
        assertEquals(0.96, event.confidence());
        assertEquals(1720000000000L, event.timestamp());
        assertNotNull(event.eventId());
    }

    private void verifyRecipients(AiPredictionEvent event) {
        verify(messaging).convertAndSendToUser("10", "/queue/calls/42", event);
        verify(messaging).convertAndSendToUser("20", "/queue/calls/42", event);
        verifyNoMoreInteractions(messaging);
    }
}
