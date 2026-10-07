package fptu.exe202.signify.signifybe.features.call.application;

import fptu.exe202.signify.signifybe.features.call.domain.VideoCall;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCallStatus;

import java.util.UUID;

/** Public call fields captured at the successful lifecycle transition. */
public record VideoCallEvent(String eventId, String type, long callId, long conversationId,
                             long callerId, long receiverId, VideoCallStatus status, long timestamp) {
    public static final String INCOMING_CALL = "INCOMING_CALL";
    public static final String CALL_STATUS_CHANGED = "CALL_STATUS_CHANGED";

    public static VideoCallEvent incoming(VideoCall call, long timestamp) {
        return from(INCOMING_CALL, call, timestamp);
    }

    public static VideoCallEvent statusChanged(VideoCall call, long timestamp) {
        return from(CALL_STATUS_CHANGED, call, timestamp);
    }

    private static VideoCallEvent from(String type, VideoCall call, long timestamp) {
        return new VideoCallEvent(UUID.randomUUID().toString(), type, call.getId(),
                call.getConversationId(), call.getCallerId(), call.getReceiverId(),
                call.getStatus(), timestamp);
    }
}
