package fptu.exe202.signify.signifybe.features.ai.api;

import fptu.exe202.signify.apiresponse.response.ApiResponse;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.call.application.VideoCallService;
import fptu.exe202.signify.signifybe.features.call.application.VideoCallEvent;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCall;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCallStatus;
import fptu.exe202.signify.signifybe.features.call.domain.exception.VideoCallException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import java.io.IOException;
import java.time.Instant;
import java.time.Duration;
import java.net.http.HttpClient;
import java.net.SocketTimeoutException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/calls/{callId}/vsl")
public class VslWordController {
    private static final int MAX_CLIP_BYTES = 5_000_000;
    private final VideoCallService calls;
    private final SimpMessagingTemplate messaging;
    private final RestClient vsl;
    private final ConcurrentHashMap<Long, Semaphore> active = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, SessionProgress> progress = new ConcurrentHashMap<>();
    private final Semaphore globalCapacity = new Semaphore(4);

    public VslWordController(VideoCallService calls, SimpMessagingTemplate messaging,
                             @Value("${signify.vsl.base-url:http://127.0.0.1:8001}") String baseUrl) {
        this.calls = calls;
        this.messaging = messaging;
        var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(65));
        this.vsl = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @PostMapping(value = "/clip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<WordEvent>> clip(@AuthenticationPrincipal CurrentUser actor,
                                          @PathVariable long callId,
                                          @RequestPart("file") MultipartFile file,
                                          @RequestParam(required = false) String sessionId,
                                          @RequestParam(required = false) Long sequenceNumber,
                                          @RequestParam(required = false) Long startedAt,
                                          @RequestParam(required = false) Long endedAt) throws IOException {
        VideoCall call = requireSigner(callId, actor);
        boolean sequenced = sessionId != null || sequenceNumber != null || startedAt != null || endedAt != null;
        if (sequenced && (sessionId == null || sequenceNumber == null || startedAt == null || endedAt == null
                || sequenceNumber <= 0 || startedAt <= 0 || endedAt < startedAt || endedAt - startedAt > 10_000)) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid VSL segment metadata");
        }
        if (sequenced) {
            try { UUID.fromString(sessionId); }
            catch (IllegalArgumentException ex) { throw new ResponseStatusException(BAD_REQUEST, "Invalid VSL session ID"); }
        }
        if (file.isEmpty() || file.getSize() > MAX_CLIP_BYTES ||
                file.getContentType() == null || !file.getContentType().startsWith("video/")) {
            throw new ResponseStatusException(BAD_REQUEST, "Expected video clip up to 5 MB");
        }
        Semaphore guard = active.computeIfAbsent(callId, ignored -> new Semaphore(1));
        if (!guard.tryAcquire()) throw new ResponseStatusException(TOO_MANY_REQUESTS, "Clip already processing");
        if (!globalCapacity.tryAcquire()) {
            guard.release();
            throw new ResponseStatusException(TOO_MANY_REQUESTS, "VSL inference capacity reached");
        }
        try {
            SessionProgress previous = sequenced ? progress.get(callId) : null;
            if (previous != null) {
                if (previous.sessionId().equals(sessionId)) {
                    if (sequenceNumber == previous.sequenceNumber())
                        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(previous.event()));
                    if (sequenceNumber < previous.sequenceNumber())
                        throw new ResponseStatusException(CONFLICT, "Stale VSL segment");
                } else if (startedAt <= previous.startedAt()) {
                    throw new ResponseStatusException(CONFLICT, "Closed VSL session");
                }
            }
            byte[] bytes = file.getBytes();
            var resource = new ByteArrayResource(bytes) {
                @Override public String getFilename() { return "clip.webm"; }
            };
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(file.getContentType()));
            var body = new LinkedMultiValueMap<String, Object>();
            body.add("file", new HttpEntity<>(resource, headers));
            WordPrediction prediction;
            long forwardStarted = System.nanoTime();
            try {
                prediction = vsl.post().uri("/predict").contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(body).retrieve().body(WordPrediction.class);
            } catch (RestClientResponseException ex) {
                if (ex.getStatusCode().value() == 429) throw new ResponseStatusException(TOO_MANY_REQUESTS, "VSLNet busy", ex);
                if (ex.getStatusCode().value() == 504) throw new ResponseStatusException(GATEWAY_TIMEOUT, "VSLNet timeout", ex);
                if (ex.getStatusCode().value() == 413 || ex.getStatusCode().value() == 422)
                    throw new ResponseStatusException(BAD_REQUEST, "Invalid VSL clip", ex);
                throw new ResponseStatusException(BAD_GATEWAY, "VSLNet error", ex);
            } catch (ResourceAccessException ex) {
                Throwable cause = ex;
                while (cause != null && !(cause instanceof SocketTimeoutException)) cause = cause.getCause();
                if (cause != null) throw new ResponseStatusException(GATEWAY_TIMEOUT, "VSLNet timeout", ex);
                throw new ResponseStatusException(BAD_GATEWAY, "VSLNet unavailable", ex);
            } catch (RestClientException ex) {
                throw new ResponseStatusException(BAD_GATEWAY, "VSLNet unavailable", ex);
            }
            double javaForwardMs = (System.nanoTime() - forwardStarted) / 1_000_000.0;
            call = requireSigner(callId, actor);
            if (prediction == null || !"success".equals(prediction.status()) ||
                    prediction.action() == null || prediction.action().isBlank() ||
                    prediction.confidence() == null || !Double.isFinite(prediction.confidence()) ||
                    prediction.confidence() < 0 || prediction.confidence() > 1) {
                throw new ResponseStatusException(BAD_GATEWAY, "Invalid VSLNet response");
            }
            String status = prediction.confidence() >= 0.8 && !"...".equals(prediction.action()) ? "FINAL" : "NO_SIGN";
            WordEvent event = new WordEvent(UUID.randomUUID().toString(), "VSL_WORD", callId,
                    call.getConversationId(), status, "FINAL".equals(status) ? prediction.action() : "",
                    prediction.confidence(), prediction.processing_ms(), Instant.now().toEpochMilli(),
                    sessionId, sequenceNumber, startedAt, endedAt, javaForwardMs);
            String destination = "/queue/calls/" + callId;
            long firstMissing = previous != null && previous.sessionId().equals(sessionId)
                    ? previous.sequenceNumber() + 1 : 1;
            if (sequenced && sequenceNumber > firstMissing) {
                var gap = new GapEvent(UUID.randomUUID().toString(), "VSL_GAP", callId, sessionId,
                        firstMissing, sequenceNumber - 1, "RECOGNITION_GAP", Instant.now().toEpochMilli());
                messaging.convertAndSendToUser(Long.toString(call.getCallerId()), destination, gap);
                if (!call.getReceiverId().equals(call.getCallerId()))
                    messaging.convertAndSendToUser(Long.toString(call.getReceiverId()), destination, gap);
            }
            if (sequenced) progress.put(callId, new SessionProgress(sessionId, sequenceNumber, startedAt, event));
            messaging.convertAndSendToUser(Long.toString(call.getCallerId()), destination, event);
            if (!call.getReceiverId().equals(call.getCallerId()))
                messaging.convertAndSendToUser(Long.toString(call.getReceiverId()), destination, event);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(event));
        } finally {
            globalCapacity.release();
            guard.release();
        }
    }

    private VideoCall requireSigner(long callId, CurrentUser actor) {
        VideoCall call = calls.validateActiveCall(callId, actor);
        if (actor.userId() != call.getReceiverId()) throw VideoCallException.forbidden();
        return call;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCallStatus(VideoCallEvent event) {
        if (VideoCallEvent.CALL_STATUS_CHANGED.equals(event.type())
                && event.status() != VideoCallStatus.ACCEPTED) {
            active.remove(event.callId());
            progress.remove(event.callId());
        }
    }

    public record WordPrediction(String status, String action, Double confidence, Double processing_ms) {}
    public record WordEvent(String eventId, String type, long callId, long conversationId,
                            String status, String action, double confidence, Double processingMs, long timestamp,
                            String sessionId, Long sequenceNumber, Long startedAt, Long endedAt, double javaForwardMs) {}
    public record GapEvent(String eventId, String type, long callId, String sessionId,
                           long sequenceNumber, long throughSequenceNumber, String status, long timestamp) {}
    private record SessionProgress(String sessionId, long sequenceNumber, long startedAt, WordEvent event) {}
}
