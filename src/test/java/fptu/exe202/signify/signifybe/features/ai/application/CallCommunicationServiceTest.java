package fptu.exe202.signify.signifybe.features.ai.application;

import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.Role;
import fptu.exe202.signify.signifybe.features.call.application.VideoCallService;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCall;
import fptu.exe202.signify.signifybe.features.call.domain.exception.VideoCallException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CallCommunicationServiceTest {
    private final CurrentUser signer = new CurrentUser(20, 2, Role.USER);
    private final CurrentUser speaker = new CurrentUser(10, 1, Role.USER);
    private final VideoCallService calls = mock(VideoCallService.class);
    private final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    private final Clock clock = Clock.fixed(Instant.ofEpochMilli(1234), ZoneOffset.UTC);
    private final VideoCall call = new VideoCall(7, 10, 20, 100);

    @BeforeEach void setUp() {
        when(calls.validateActiveCall(42L, speaker)).thenReturn(call);
        when(calls.requireSignerAccount(42, signer)).thenReturn(call);
        when(calls.requireNonSigner(42, speaker)).thenReturn(call);
    }

    @Test void signerModeIsBroadcastAndSpeakerCannotChangeIt() {
        AiRecognitionModeService modes = new AiRecognitionModeService(calls, messaging, clock);
        assertEquals(AiRecognitionModeService.Mode.ALPHABET, modes.current(42, speaker).mode());
        var update = modes.change(42, signer, AiRecognitionModeService.Mode.VSL_WORDS);
        assertEquals(AiRecognitionModeService.Mode.VSL_WORDS, update.mode());
        assertEquals(1234, update.timestamp());
        assertEquals(update, modes.current(42, speaker));
        verify(messaging).convertAndSendToUser("10", "/queue/calls/42", update);
        verify(messaging).convertAndSendToUser("20", "/queue/calls/42", update);
        when(calls.requireSignerAccount(42, speaker)).thenThrow(VideoCallException.forbidden());
        assertThrows(VideoCallException.class,
                () -> modes.change(42, speaker, AiRecognitionModeService.Mode.VSL_STREAMING));
    }

    @Test void nonSignerSpeechIsSentToBothParticipants() {
        CallSpeechService speech = new CallSpeechService(calls, messaging, clock);
        var event = speech.publish(42, speaker, "  Xin chào  ");
        assertEquals("Xin chào", event.text());
        assertEquals(speaker.userId(), event.speakerId());
        verify(messaging).convertAndSendToUser("10", "/queue/calls/42", event);
        verify(messaging).convertAndSendToUser("20", "/queue/calls/42", event);
        when(calls.requireNonSigner(42, signer)).thenThrow(VideoCallException.forbidden());
        assertThrows(VideoCallException.class, () -> speech.publish(42, signer, "Không được phép"));
        assertThrows(IllegalArgumentException.class, () -> speech.publish(42, speaker, "   "));
    }
}
