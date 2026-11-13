package fptu.exe202.signify.signifybe.features.ai.api;

import fptu.exe202.signify.apiresponse.response.ApiResponse;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.Role;
import fptu.exe202.signify.signifybe.features.call.application.VideoCallEvent;
import fptu.exe202.signify.signifybe.features.call.application.VideoCallService;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCall;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCallStatus;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.http.HttpStatus.*;

/**
 * Experimental single-instance streaming control plane; clip inference remains available.
 */
@RestController
public class VslStreamingController {
    private static final long TOKEN_TTL_MS = 300_000;
    private final VideoCallService calls;
    private final SimpMessagingTemplate messaging;
    private final Clock clock;
    private final String serviceSecret;
    private final String wsUrl;
    private final ConcurrentHashMap<String, SessionState> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> byCall = new ConcurrentHashMap<>();

    public VslStreamingController(VideoCallService calls, SimpMessagingTemplate messaging, Clock clock,
                                  @Value("${signify.vsl.streaming-secret:}") String serviceSecret,
                                  @Value("${signify.vsl.ws-url:ws://127.0.0.1:8001/ws/vsl/stream}") String wsUrl) {
        this.calls = calls;
        this.messaging = messaging;
        this.clock = clock;
        this.serviceSecret = serviceSecret;
        this.wsUrl = wsUrl;
    }

    @PostMapping("/api/calls/{callId}/vsl/stream-session")
    public synchronized ResponseEntity<ApiResponse<SessionTicket>> start(@AuthenticationPrincipal CurrentUser actor,
                                                                         @PathVariable long callId) {
        SecretKey key = key();
        VideoCall call = requireSigner(callId, actor);
        sessions.values().removeIf(state -> clock.millis() >= state.expiresAt);
        byCall.entrySet().removeIf(entry -> !sessions.containsKey(entry.getValue()));
        if (sessions.size() >= 2 && !byCall.containsKey(callId))
            throw new ResponseStatusException(TOO_MANY_REQUESTS, "VSL streaming capacity reached");
        String id = UUID.randomUUID().toString();
        long now = clock.millis();
        long expiry = now + TOKEN_TTL_MS;
        var state = new SessionState(id, callId, call.getReceiverId(), call.getCallerId(),
                call.getConversationId(), expiry);
        String old = byCall.put(callId, id);
        if (old != null) sessions.remove(old);
        sessions.put(id, state);
        String token = Jwts.builder().issuer("signify-be-vsl").subject(Long.toString(state.signerId))
                .audience().add("vsl-net").and().issuedAt(new Date(now)).expiration(new Date(expiry))
                .claim("callId", callId).claim("sessionId", id).claim("scope", "vsl:stream")
                .id(UUID.randomUUID().toString()).signWith(key, Jwts.SIG.HS256).compact();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(new SessionTicket(id, token, expiry, wsUrl)));
    }

    @DeleteMapping("/api/calls/{callId}/vsl/stream-session/{sessionId}")
    public ResponseEntity<Void> stop(@AuthenticationPrincipal CurrentUser actor, @PathVariable long callId,
                                     @PathVariable String sessionId) {
        SessionState state = sessions.get(sessionId);
        if (state == null || state.callId != callId || actor == null || actor.userId() != state.signerId)
            throw new ResponseStatusException(FORBIDDEN, "VSL session unavailable");
        sessions.remove(sessionId, state);
        byCall.remove(callId, sessionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/internal/vsl/session/validate")
    public ValidationResponse validate(@RequestHeader("X-VSL-Service-Token") String serviceToken,
                                       @RequestBody ValidationRequest request) {
        requireService(serviceToken);
        SessionState state = active(request.sessionId(), request.callId(), request.signerId());
        return new ValidationResponse(true, state.expiresAt);
    }

    @PostMapping("/api/internal/vsl/predictions")
    public ResponseEntity<Void> prediction(@RequestHeader("X-VSL-Service-Token") String serviceToken,
                                           @RequestBody StreamingPrediction prediction) {
        requireService(serviceToken);
        SessionState state = active(prediction.sessionId(), prediction.callId(), prediction.signerId());
        String status = prediction.status();
        if (!"PARTIAL".equals(status) && !"FINAL".equals(status) && !"NO_SIGN".equals(status)
                && !"GAP".equals(status) && !"ERROR".equals(status))
            throw new ResponseStatusException(BAD_REQUEST, "Invalid VSL status");
        if (prediction.sequenceNumber() <= 0 || prediction.eventId() == null
                || prediction.eventId().isBlank() || prediction.timestamp() <= 0)
            throw new ResponseStatusException(BAD_REQUEST, "Invalid VSL event metadata");
        if (("FINAL".equals(status) || "PARTIAL".equals(status))
                && (prediction.action() == null || prediction.action().isBlank()
                || !Double.isFinite(prediction.confidence()) || prediction.confidence() < 0.8
                || prediction.confidence() > 1))
            throw new ResponseStatusException(BAD_REQUEST, "Untrusted VSL prediction");
        synchronized (state) {
            if (state.events.contains(prediction.eventId())) return ResponseEntity.noContent().build();
            if (!"GAP".equals(status) && !"ERROR".equals(status)
                    && (prediction.sequenceNumber() < state.lastSequence
                    || (prediction.sequenceNumber() == state.lastSequence && !"FINAL".equals(status))))
                throw new ResponseStatusException(CONFLICT, "Stale VSL event");
            if ("FINAL".equals(status) && prediction.sequenceNumber() <= state.lastFinalSequence)
                throw new ResponseStatusException(CONFLICT, "Duplicate VSL FINAL");
            state.lastSequence = Math.max(state.lastSequence, prediction.sequenceNumber());
            if ("FINAL".equals(status)) state.lastFinalSequence = prediction.sequenceNumber();
            state.events.add(prediction.eventId());
            if (state.events.size() > 256) state.events.remove(state.events.iterator().next());
            var event = new StreamingEvent(prediction.eventId(), "VSL_" + status, status,
                    state.callId, state.conversationId, state.id, prediction.sequenceNumber(),
                    "FINAL".equals(status) || "PARTIAL".equals(status) ? prediction.action() : "",
                    prediction.confidence(), prediction.processingMs(), prediction.timestamp());
            String destination = "/queue/calls/" + state.callId;
            messaging.convertAndSendToUser(Long.toString(state.callerId), destination, event);
            if (state.signerId != state.callerId)
                messaging.convertAndSendToUser(Long.toString(state.signerId), destination, event);
        }
        return ResponseEntity.noContent().build();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCallStatus(VideoCallEvent event) {
        if (VideoCallEvent.CALL_STATUS_CHANGED.equals(event.type())
                && event.status() != VideoCallStatus.ACCEPTED) {
            String id = byCall.remove(event.callId());
            if (id != null) sessions.remove(id);
        }
    }

    private VideoCall requireSigner(long callId, CurrentUser actor) {
        VideoCall call = calls.validateActiveCall(callId, actor);
        if (actor.userId() != call.getReceiverId()) throw new ResponseStatusException(FORBIDDEN, "Signer required");
        return call;
    }

    private SessionState active(String id, long callId, long signerId) {
        SessionState state = sessions.get(id);
        if (state == null || state.callId != callId || state.signerId != signerId
                || !id.equals(byCall.get(callId)) || clock.millis() >= state.expiresAt)
            throw new ResponseStatusException(FORBIDDEN, "VSL session expired or revoked");
        requireSigner(callId, new CurrentUser(signerId, 1, Role.USER));
        return state;
    }

    private SecretKey key() {
        if (serviceSecret == null || serviceSecret.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "VSL streaming is not configured");
        return Keys.hmacShaKeyFor(serviceSecret.getBytes(StandardCharsets.UTF_8));
    }

    private void requireService(String provided) {
        key();
        if (provided == null || !MessageDigest.isEqual(serviceSecret.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(FORBIDDEN, "VSL service authentication failed");
    }

    private static final class SessionState {
        final String id;
        final long callId, signerId, callerId, conversationId, expiresAt;
        long lastSequence, lastFinalSequence;
        final LinkedHashSet<String> events = new LinkedHashSet<>();

        SessionState(String id, long callId, long signerId, long callerId, long conversationId, long expiresAt) {
            this.id = id;
            this.callId = callId;
            this.signerId = signerId;
            this.callerId = callerId;
            this.conversationId = conversationId;
            this.expiresAt = expiresAt;
        }
    }

    public record SessionTicket(String sessionId, String token, long expiresAt, String wsUrl) {
    }

    public record ValidationRequest(String sessionId, long callId, long signerId) {
    }

    public record ValidationResponse(boolean active, long expiresAt) {
    }

    public record StreamingPrediction(String eventId, String sessionId, long callId, long signerId,
                                      long sequenceNumber, String status, String action,
                                      double confidence, double processingMs, long timestamp) {
    }

    public record StreamingEvent(String eventId, String type, String status, long callId, long conversationId,
                                 String sessionId, long sequenceNumber, String action,
                                 double confidence, double processingMs, long timestamp) {
    }
}
