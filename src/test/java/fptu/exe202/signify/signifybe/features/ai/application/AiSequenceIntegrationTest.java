package fptu.exe202.signify.signifybe.features.ai.application;

import fptu.exe202.signify.signifybe.features.ai.api.dto.AiPredictionResponse;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.Role;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiSequenceIntegrationTest {
    private static final CurrentUser VIEWER = new CurrentUser(10, 1, Role.USER);
    private static final CurrentUser SIGNER = new CurrentUser(20, 2, Role.USER);
    private static final byte[] IMAGE = {1, 2, 3};

    private AiService ai;
    private SimpMessagingTemplate messaging;
    private AiPredictionService predictions;

    @BeforeEach
    void setUp() {
        var repository = mock(JpaVideoCallRepository.class);
        var call = new VideoCall(7, VIEWER.userId(), SIGNER.userId(), 100);
        call.accept(200);
        when(repository.findById(42L)).thenReturn(Optional.of(call));
        var calls = new VideoCallService(repository, mock(PrivateChatAccessService.class),
                Clock.systemUTC(), mock(ApplicationEventPublisher.class));
        ai = mock(AiService.class);
        messaging = mock(SimpMessagingTemplate.class);
        var text = new AiTextService(calls, messaging, Clock.fixed(Instant.ofEpochMilli(1234), ZoneOffset.UTC));
        predictions = new AiPredictionService(calls, ai, new StableLetterTracker(), text, messaging);
    }

    @Test
    void onlyAcceptedLetterPublishesBothCompatibleEventTypes() {
        when(ai.predict(IMAGE, MediaType.IMAGE_JPEG))
                .thenReturn(new AiPredictionResponse("A", 0.91, 1720000000000L));
        assertNull(predict(SIGNER));
        assertNull(predict(SIGNER));
        AiPredictionEvent prediction = predict(SIGNER);
        assertNotNull(prediction);
        assertEquals(AiPredictionEvent.TYPE, prediction.type());
        assertEquals("A", prediction.letter());
        assertEquals(0.91, prediction.confidence());
        assertEquals(1720000000000L, prediction.timestamp());
        verify(messaging).convertAndSendToUser("10", "/queue/calls/42", prediction);
        verify(messaging).convertAndSendToUser("20", "/queue/calls/42", prediction);
        verify(messaging, times(1)).convertAndSendToUser(eq("10"), eq("/queue/calls/42"),
                argThat(payload -> payload instanceof AiTextUpdateEvent update
                        && update.type().equals(AiTextUpdateEvent.TYPE) && update.text().equals("A")));
        verify(messaging, times(1)).convertAndSendToUser(eq("20"), eq("/queue/calls/42"),
                argThat(payload -> payload instanceof AiTextUpdateEvent update && update.text().equals("A")));
        clearInvocations(messaging);
        for (int index = 0; index < 10; index++) assertNull(predict(SIGNER));
        verifyNoInteractions(messaging);
    }

    @Test
    void viewerCannotSubmitFramesOrChangeSequence() {
        assertThrows(VideoCallException.class, () -> predict(VIEWER));
        verifyNoInteractions(ai, messaging);
    }

    private AiPredictionEvent predict(CurrentUser actor) {
        return predictions.predict(42, actor, IMAGE, MediaType.IMAGE_JPEG);
    }
}
