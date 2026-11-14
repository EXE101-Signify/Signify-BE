package fptu.exe202.signify.signifybe.features.ai.api;

import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.Role;
import fptu.exe202.signify.signifybe.features.call.application.VideoCallService;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCall;
import fptu.exe202.signify.signifybe.features.call.domain.exception.VideoCallException;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VslStreamingControllerTest {
    private final VideoCallService calls = mock(VideoCallService.class);
    private final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC);
    private final String secret = "test-vsl-service-secret-with-32-plus-bytes";
    private final VslStreamingController controller = new VslStreamingController(calls, messaging, clock,
            secret, "ws://127.0.0.1:8001/ws/vsl/stream");
    private final VideoCall call = new VideoCall(7, 10, 20, 100);
    private final CurrentUser signer = new CurrentUser(20, 2, Role.USER);

    @Test
    void signerCanIssueAndRevokeWhileViewerCannotIssue() {
        stubSigner();
        var viewer = new CurrentUser(10, 1, Role.USER);
        assertThrows(VideoCallException.class, () -> controller.start(viewer, 42));
        var ticket = controller.start(signer, 42).getBody().getData();
        assertNotNull(ticket.token());
        assertEquals("ws://127.0.0.1:8001/ws/vsl/stream", ticket.wsUrl());
        assertTrue(controller.validate(secret, new VslStreamingController.ValidationRequest(ticket.sessionId(), 42, 20)).active());
        assertThrows(ResponseStatusException.class,
                () -> controller.validate("wrong-secret", new VslStreamingController.ValidationRequest(ticket.sessionId(), 42, 20)));
        controller.stop(signer, 42, ticket.sessionId());
        assertThrows(ResponseStatusException.class,
                () -> controller.validate(secret, new VslStreamingController.ValidationRequest(ticket.sessionId(), 42, 20)));
    }

    @Test
    void partialThenFinalCanShareSequenceButReplayDoesNotBroadcastTwice() {
        stubSigner();
        var session = controller.start(signer, 42).getBody().getData().sessionId();
        var partial = new VslStreamingController.StreamingPrediction(UUID.randomUUID().toString(), session,
                42, 20, 8, "PARTIAL", "NƯỚC", .91, 20, clock.millis());
        var finalEvent = new VslStreamingController.StreamingPrediction(UUID.randomUUID().toString(), session,
                42, 20, 8, "FINAL", "NƯỚC", .92, 21, clock.millis());
        controller.prediction(secret, partial);
        controller.prediction(secret, finalEvent);
        controller.prediction(secret, finalEvent);
        verify(messaging, times(2)).convertAndSendToUser(eq("10"), eq("/queue/calls/42"),
                any(VslStreamingController.StreamingEvent.class));
        verify(messaging, times(2)).convertAndSendToUser(eq("20"), eq("/queue/calls/42"),
                any(VslStreamingController.StreamingEvent.class));
        var stale = new VslStreamingController.StreamingPrediction(UUID.randomUUID().toString(), session,
                42, 20, 7, "FINAL", "NƯỚC", .92, 21, clock.millis());
        assertThrows(ResponseStatusException.class, () -> controller.prediction(secret, stale));
    }
    private void stubSigner() {
        when(calls.requireSigner(eq(42L), any(CurrentUser.class))).thenAnswer(invocation -> {
            CurrentUser actor = invocation.getArgument(1);
            if (actor.userId() != signer.userId()) throw VideoCallException.forbidden();
            return call;
        });
    }
}
