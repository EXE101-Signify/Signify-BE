package fptu.exe202.signify.signifybe.features.ai.application;

import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.Role;
import fptu.exe202.signify.signifybe.features.call.application.VideoCallService;
import fptu.exe202.signify.signifybe.features.call.domain.exception.VideoCallException;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class CallSttServiceTest {
    private final VideoCallService calls = mock(VideoCallService.class);
    private final CallSpeechService speech = mock(CallSpeechService.class);
    private final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    private final CallSttService service = new CallSttService(calls, speech, messaging,
            "ws://127.0.0.1:8002/api/v1/stt/stream", "server-secret");
    private final ObjectMapper json = new ObjectMapper();

    @Test void rejectsInvalidAudioRateBeforeOpeningSttConnection() {
        CurrentUser speaker = new CurrentUser(10, 1, Role.USER);
        service.handle(42, speaker, "socket-1", json.readTree("""
                {"type":"start","sessionId":"session-123","sampleRate":96000}
                """));
        verify(calls).requireNonSigner(42, speaker);
        verify(messaging).convertAndSendToUser("10", "/queue/calls/42/stt",
                new CallSttService.SttStatus("STT_ERROR", 42, "session-123", "Tần số âm thanh không hợp lệ."));
        verifyNoInteractions(speech);
    }

    @Test void signerCannotStartSpeechSession() {
        CurrentUser signer = new CurrentUser(20, 2, Role.USER);
        when(calls.requireNonSigner(42, signer)).thenThrow(VideoCallException.forbidden());
        assertThrows(VideoCallException.class, () -> service.handle(42, signer, "socket-2",
                json.readTree("""
                        {"type":"start","sessionId":"session-456","sampleRate":48000}
                        """)));
        verifyNoInteractions(speech, messaging);
    }
}
