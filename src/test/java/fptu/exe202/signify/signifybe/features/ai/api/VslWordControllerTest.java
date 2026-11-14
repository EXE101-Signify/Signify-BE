package fptu.exe202.signify.signifybe.features.ai.api;

import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.Role;
import fptu.exe202.signify.signifybe.features.call.application.VideoCallService;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCall;
import fptu.exe202.signify.signifybe.features.call.domain.exception.VideoCallException;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class VslWordControllerTest {
    private final VideoCallService calls = mock(VideoCallService.class);
    private final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    private final VslWordController controller = new VslWordController(calls, messaging, "http://127.0.0.1:8001");
    private final VideoCall call = new VideoCall(7, 10, 20, 100);

    @Test
    void viewerCannotSubmitClip() throws Exception {
        var viewer = new CurrentUser(10, 1, Role.USER);
        when(calls.requireSigner(42L, viewer)).thenThrow(VideoCallException.forbidden());
        var clip = new MockMultipartFile("file", "clip.webm", "video/webm", new byte[]{1});
        assertThrows(VideoCallException.class, () -> controller.clip(viewer, 42L, clip, null, null, null, null));
        verifyNoInteractions(messaging);
    }

    @Test
    void oversizedClipIsRejectedBeforeCallingAi() throws Exception {
        var signer = new CurrentUser(20, 2, Role.USER);
        when(calls.requireSigner(42L, signer)).thenReturn(call);
        var clip = new MockMultipartFile("file", "clip.webm", "video/webm", new byte[5_000_001]);
        assertThrows(ResponseStatusException.class, () -> controller.clip(signer, 42L, clip, null, null, null, null));
        verifyNoInteractions(messaging);
    }

    @Test
    void forwardsWebmAndRoutesFinalToCallParticipants() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var received = new AtomicReference<String>();
        server.createContext("/predict", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            byte[] response = "{\"status\":\"success\",\"action\":\"NƯỚC\",\"confidence\":0.91,\"processing_ms\":123}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var signer = new CurrentUser(20, 2, Role.USER);
            when(calls.requireSigner(42L, signer)).thenReturn(call);
            var localController = new VslWordController(calls, messaging,
                    "http://127.0.0.1:" + server.getAddress().getPort());
            var clip = new MockMultipartFile("file", "clip.webm", "video/webm", "webm-test".getBytes());
            var event = localController.clip(signer, 42L, clip, null, null, null, null).getBody().getData();
            assertEquals("FINAL", event.status());
            assertEquals("NƯỚC", event.action());
            assertTrue(received.get().contains("webm-test"));
            verify(messaging).convertAndSendToUser(eq("10"), eq("/queue/calls/42"), any(VslWordController.WordEvent.class));
            verify(messaging).convertAndSendToUser(eq("20"), eq("/queue/calls/42"), any(VslWordController.WordEvent.class));
        } finally { server.stop(0); }
    }

    @Test
    void lowConfidencePublishesNoSignWithoutGloss() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/predict", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] response = "{\"status\":\"success\",\"action\":\"Xôi\",\"confidence\":0.2,\"processing_ms\":12}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var signer = new CurrentUser(20, 2, Role.USER);
            when(calls.requireSigner(42L, signer)).thenReturn(call);
            var localController = new VslWordController(calls, messaging,
                    "http://127.0.0.1:" + server.getAddress().getPort());
            var clip = new MockMultipartFile("file", "clip.webm", "video/webm", new byte[]{1});
            var event = localController.clip(signer, 42L, clip, null, null, null, null).getBody().getData();
            assertEquals("NO_SIGN", event.status());
            assertEquals("", event.action());
            verify(messaging).convertAndSendToUser(eq("10"), eq("/queue/calls/42"), any(VslWordController.WordEvent.class));
            verify(messaging).convertAndSendToUser(eq("20"), eq("/queue/calls/42"), any(VslWordController.WordEvent.class));
        } finally { server.stop(0); }
    }

    @Test
    void retryUsesCachedEventAndMissingSequencePublishesGap() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var forwards = new AtomicInteger();
        server.createContext("/predict", exchange -> {
            forwards.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] response = "{\"status\":\"success\",\"action\":\"NƯỚC\",\"confidence\":0.91,\"processing_ms\":12}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var signer = new CurrentUser(20, 2, Role.USER);
            when(calls.requireSigner(42L, signer)).thenReturn(call);
            var localController = new VslWordController(calls, messaging,
                    "http://127.0.0.1:" + server.getAddress().getPort());
            var clip = new MockMultipartFile("file", "clip.webm", "video/webm", new byte[]{1});
            String session = UUID.randomUUID().toString();
            long now = System.currentTimeMillis();
            var first = localController.clip(signer, 42L, clip, session, 1L, now, now + 2500).getBody().getData();
            var retry = localController.clip(signer, 42L, clip, session, 1L, now, now + 2500).getBody().getData();
            assertEquals(first.eventId(), retry.eventId());
            var third = localController.clip(signer, 42L, clip, session, 3L, now + 5000, now + 7500).getBody().getData();
            assertEquals(3L, third.sequenceNumber());
            assertEquals(2, forwards.get());
            verify(messaging).convertAndSendToUser(eq("10"), eq("/queue/calls/42"), any(VslWordController.GapEvent.class));
            verify(messaging).convertAndSendToUser(eq("20"), eq("/queue/calls/42"), any(VslWordController.GapEvent.class));
            verify(messaging, times(2)).convertAndSendToUser(eq("10"), eq("/queue/calls/42"), any(VslWordController.WordEvent.class));
        } finally { server.stop(0); }
    }
}
