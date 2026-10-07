package fptu.exe202.signify.signifybe.features.call.application;

import tools.jackson.databind.JsonNode;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCall;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

@Service
public class WebRtcSignalingService {
    private static final Set<String> TYPES = Set.of("WEBRTC_READY", "WEBRTC_OFFER", "WEBRTC_ANSWER", "WEBRTC_ICE_CANDIDATE");
    private final VideoCallService calls;
    private final SimpMessagingTemplate messaging;
    private final Clock clock;

    public WebRtcSignalingService(VideoCallService calls, SimpMessagingTemplate messaging, Clock clock) {
        this.calls = calls;
        this.messaging = messaging;
        this.clock = clock;
    }

    public void relay(long callId, CurrentUser actor, JsonNode request) {
        VideoCall call = calls.validateActiveCall(callId, actor);
        if (request == null || !request.isObject() || request.size() != 2
                || !request.has("type") || !request.has("payload"))
            throw new IllegalArgumentException("Invalid WebRTC signal");
        String type = request.path("type").asText(null);
        JsonNode payload = request.path("payload");
        if (type == null || !TYPES.contains(type) || !validPayload(type, payload))
            throw new IllegalArgumentException("Invalid WebRTC signal");
        long senderId = actor.userId();
        boolean caller = senderId == call.getCallerId();
        if (("WEBRTC_OFFER".equals(type) && !caller)
                || (("WEBRTC_READY".equals(type) || "WEBRTC_ANSWER".equals(type)) && caller))
            throw new IllegalArgumentException("Invalid WebRTC signaling role");
        long receiverId = caller ? call.getReceiverId() : call.getCallerId();
        // A call can end after the first authorization check and before forwarding.
        calls.validateActiveCall(callId, actor);
        WebRtcSignalEvent event = new WebRtcSignalEvent(UUID.randomUUID().toString(), type, callId,
                senderId, receiverId, payload, clock.millis());
        messaging.convertAndSendToUser(Long.toString(receiverId), "/queue/calls/" + callId + "/webrtc", event);
    }

    private static boolean validPayload(String type, JsonNode payload) {
        if (!payload.isObject()) return false;
        if ("WEBRTC_READY".equals(type)) return payload.size() == 0;
        if ("WEBRTC_OFFER".equals(type) || "WEBRTC_ANSWER".equals(type)) {
            return payload.size() == 2 && type.substring(7).toLowerCase().equals(payload.path("type").asText())
                    && isText(payload.path("sdp"), 48_000);
        }
        if (payload.size() < 3 || payload.size() > 4 || !isText(payload.path("candidate"), 4096)
                || !payload.has("sdpMid") || !payload.has("sdpMLineIndex")) return false;
        for (String name : payload.propertyNames()) {
            if (!Set.of("candidate", "sdpMid", "sdpMLineIndex", "usernameFragment").contains(name)) {
                return false;
            }
        }
        if (!payload.path("sdpMid").isNull() && !payload.path("sdpMid").isTextual()) return false;
        if (!payload.path("sdpMLineIndex").isNull()
                && (!payload.path("sdpMLineIndex").canConvertToInt() || payload.path("sdpMLineIndex").asInt() < 0)) return false;
        return !payload.has("usernameFragment") || payload.path("usernameFragment").isNull()
                || payload.path("usernameFragment").isTextual();
    }

    private static boolean isText(JsonNode node, int maxLength) {
        return node.isTextual() && !node.asText().isBlank() && node.asText().length() <= maxLength;
    }
}
