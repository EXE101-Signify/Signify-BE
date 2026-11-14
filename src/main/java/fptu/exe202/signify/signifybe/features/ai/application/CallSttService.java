package fptu.exe202.signify.signifybe.features.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.call.application.VideoCallService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Relays call audio to Vietnamese-STT without exposing its API key to the browser. */
@Service
public class CallSttService {
    private static final Logger log = LoggerFactory.getLogger(CallSttService.class);
    private static final int MAX_FRAME_BYTES = 32_768;
    private static final int MAX_PENDING_BYTES = 512 * 1024;
    private final VideoCallService calls;
    private final CallSpeechService speech;
    private final SimpMessagingTemplate messaging;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final Map<SessionId, StreamSession> sessions = new ConcurrentHashMap<>();
    private final URI wsUrl;
    private final String apiKey;

    public CallSttService(VideoCallService calls, CallSpeechService speech, SimpMessagingTemplate messaging,
                          @Value("${signify.stt.ws-url}") String wsUrl,
                          @Value("${signify.stt.api-key:}") String apiKey) {
        this.calls = calls;
        this.speech = speech;
        this.messaging = messaging;
        this.wsUrl = URI.create(wsUrl);
        if (!"ws".equals(this.wsUrl.getScheme()) && !"wss".equals(this.wsUrl.getScheme()))
            throw new IllegalArgumentException("STT URL must use ws or wss");
        this.apiKey = apiKey;
    }

    public void handle(long callId, CurrentUser user, String stompSessionId, tools.jackson.databind.JsonNode message) {
        if (stompSessionId == null || stompSessionId.isBlank() || message == null || !message.isObject()) return;
        String clientSessionId = message.path("sessionId").asText("");
        if (!clientSessionId.matches("[A-Za-z0-9_-]{8,64}")) return;
        SessionId id = new SessionId(stompSessionId, callId);
        String type = message.path("type").asText("");
        switch (type) {
            case "start" -> start(id, user, clientSessionId, message);
            case "audio" -> audio(id, clientSessionId, message);
            case "stop" -> stop(id, clientSessionId);
            case "ping" -> ping(id, clientSessionId);
            default -> status(user.userId(), callId, clientSessionId, "STT_ERROR", "Lệnh STT không hợp lệ.");
        }
    }

    private void start(SessionId id, CurrentUser user, String clientSessionId, tools.jackson.databind.JsonNode message) {
        calls.requireNonSigner(id.callId(), user);
        int sampleRate = message.path("sampleRate").asInt(0);
        if (sampleRate < 8000 || sampleRate > 48000) {
            status(user.userId(), id.callId(), clientSessionId, "STT_ERROR", "Tần số âm thanh không hợp lệ.");
            return;
        }
        StreamSession session = new StreamSession(id, user, clientSessionId);
        StreamSession previous = sessions.putIfAbsent(id, session);
        if (previous != null) {
            status(user.userId(), id.callId(), clientSessionId, "STT_ERROR", "Phiên nhận dạng đang hoạt động.");
            return;
        }
        Map<String, Object> start = new HashMap<>();
        start.put("type", "start");
        start.put("sample_rate", sampleRate);
        start.put("channels", 1);
        start.put("encoding", "pcm_s16le");
        start.put("language", "vi");
        if (!apiKey.isBlank()) start.put("api_key", apiKey);
        try {
            String body = json.writeValueAsString(start);
            http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(8)).buildAsync(wsUrl, new SttListener(session, body))
                    .whenComplete((socket, failure) -> {
                        if (failure != null) fail(session, "Không kết nối được dịch vụ nhận dạng giọng nói.", failure);
                        else if (sessions.get(id) != session) socket.abort();
                    });
        } catch (Exception ex) {
            fail(session, "Không khởi tạo được nhận dạng giọng nói.", ex);
        }
    }

    private void audio(SessionId id, String clientSessionId, tools.jackson.databind.JsonNode message) {
        StreamSession session = sessions.get(id);
        if (session == null || !session.clientSessionId.equals(clientSessionId) || !session.ready || session.stopping) return;
        String value = message.path("data").asText("");
        if (value.length() > 43_692 || value.isBlank()) {
            fail(session, "Khung âm thanh không hợp lệ.", null);
            return;
        }
        byte[] pcm;
        try { pcm = Base64.getDecoder().decode(value); }
        catch (IllegalArgumentException ex) {
            fail(session, "Khung âm thanh không hợp lệ.", ex);
            return;
        }
        if (pcm.length == 0 || pcm.length > MAX_FRAME_BYTES || pcm.length % 2 != 0
                || session.pendingBytes.addAndGet(pcm.length) > MAX_PENDING_BYTES) {
            session.pendingBytes.addAndGet(-pcm.length);
            fail(session, "Truyền âm thanh quá chậm hoặc khung âm thanh không hợp lệ.", null);
            return;
        }
        WebSocket socket = session.socket;
        if (socket == null) { session.pendingBytes.addAndGet(-pcm.length); return; }
        session.sendBinary(pcm).whenComplete((ignored, failure) -> {
            session.pendingBytes.addAndGet(-pcm.length);
            if (failure != null) fail(session, "Mất kết nối với dịch vụ nhận dạng giọng nói.", failure);
        });
    }

    private void stop(SessionId id, String clientSessionId) {
        StreamSession session = sessions.get(id);
        if (session == null || !session.clientSessionId.equals(clientSessionId) || session.stopping) return;
        session.stopping = true;
        if (session.ready && session.socket != null) sendCommand(session, "stop");
    }

    private void ping(SessionId id, String clientSessionId) {
        StreamSession session = sessions.get(id);
        if (session != null && session.clientSessionId.equals(clientSessionId) && session.ready && !session.stopping)
            sendCommand(session, "ping");
    }

    private void sendCommand(StreamSession session, String type) {
        session.sendText("{\"type\":\"" + type + "\"}").whenComplete((ignored, failure) -> {
            if (failure != null) fail(session, "Mất kết nối với dịch vụ nhận dạng giọng nói.", failure);
        });
    }

    private void status(long userId, long callId, String clientSessionId, String type, String message) {
        messaging.convertAndSendToUser(Long.toString(userId), "/queue/calls/" + callId + "/stt",
                new SttStatus(type, callId, clientSessionId, message));
    }

    private void fail(StreamSession session, String message, Throwable cause) {
        if (!sessions.remove(session.id, session)) return;
        if (cause != null) log.warn("Call STT session failed", cause);
        if (session.socket != null) session.socket.abort();
        status(session.user.userId(), session.id.callId(), session.clientSessionId, "STT_ERROR", message);
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        String stompSessionId = event.getSessionId();
        sessions.forEach((id, session) -> {
            if (id.stompSessionId().equals(stompSessionId) && sessions.remove(id, session) && session.socket != null)
                session.socket.abort();
        });
    }

    private final class SttListener implements WebSocket.Listener {
        private final StreamSession session;
        private final String startBody;
        private final StringBuilder input = new StringBuilder();

        private SttListener(StreamSession session, String startBody) {
            this.session = session;
            this.startBody = startBody;
        }

        @Override public void onOpen(WebSocket socket) {
            session.socket = socket;
            socket.request(1);
            if (sessions.get(session.id) != session) { socket.abort(); return; }
            socket.sendText(startBody, true).whenComplete((ignored, failure) -> {
                if (failure != null) fail(session, "Không khởi tạo được phiên nhận dạng giọng nói.", failure);
            });
        }

        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            if (input.length() + data.length() > 16_384) {
                fail(session, "Phản hồi STT không hợp lệ.", null);
                return CompletableFuture.completedFuture(null);
            }
            input.append(data);
            if (last) {
                String body = input.toString();
                input.setLength(0);
                receive(body);
            }
            if (sessions.get(session.id) == session) socket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        private void receive(String body) {
            if (sessions.get(session.id) != session) return;
            try {
                com.fasterxml.jackson.databind.JsonNode event = json.readTree(body);
                switch (event.path("type").asText("")) {
                    case "ready" -> {
                        session.ready = true;
                        status(session.user.userId(), session.id.callId(), session.clientSessionId, "STT_READY", "");
                        if (session.stopping) sendCommand(session, "stop");
                    }
                    case "transcript.final" -> {
                        String text = event.path("text").asText("").strip();
                        if (!text.isEmpty() && text.length() <= 2000)
                            speech.publish(session.id.callId(), session.user, text);
                    }
                    case "session.completed" -> {
                        sessions.remove(session.id, session);
                        status(session.user.userId(), session.id.callId(), session.clientSessionId, "STT_COMPLETED", "");
                        session.socket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
                    }
                    case "error" -> fail(session, event.path("message").asText("Nhận dạng giọng nói thất bại."), null);
                    default -> { /* pong and future optional events */ }
                }
            } catch (Exception ex) {
                fail(session, "Không xử lý được phản hồi STT.", ex);
            }
        }

        @Override public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
            if (sessions.remove(session.id, session))
                status(session.user.userId(), session.id.callId(), session.clientSessionId, "STT_ERROR", "Dịch vụ nhận dạng giọng nói đã ngắt kết nối.");
            return CompletableFuture.completedFuture(null);
        }

        @Override public void onError(WebSocket socket, Throwable error) {
            fail(session, "Mất kết nối với dịch vụ nhận dạng giọng nói.", error);
        }
    }

    private record SessionId(String stompSessionId, long callId) { }
    public record SttStatus(String type, long callId, String sessionId, String message) { }

    private static final class StreamSession {
        private final SessionId id;
        private final CurrentUser user;
        private final String clientSessionId;
        private final AtomicInteger pendingBytes = new AtomicInteger();
        private CompletableFuture<Void> sendTail = CompletableFuture.completedFuture(null);
        private volatile WebSocket socket;
        private volatile boolean ready;
        private volatile boolean stopping;

        private StreamSession(SessionId id, CurrentUser user, String clientSessionId) {
            this.id = id;
            this.user = user;
            this.clientSessionId = clientSessionId;
        }

        private synchronized CompletableFuture<Void> sendBinary(byte[] pcm) {
            sendTail = sendTail.thenCompose(ignored ->
                    socket.sendBinary(ByteBuffer.wrap(pcm), true).thenApply(sent -> (Void) null));
            return sendTail;
        }

        private synchronized CompletableFuture<Void> sendText(String text) {
            sendTail = sendTail.thenCompose(ignored -> socket.sendText(text, true).thenApply(sent -> (Void) null));
            return sendTail;
        }
    }
}
