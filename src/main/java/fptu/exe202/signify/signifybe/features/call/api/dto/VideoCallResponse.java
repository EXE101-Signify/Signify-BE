package fptu.exe202.signify.signifybe.features.call.api.dto;

import fptu.exe202.signify.signifybe.features.call.domain.VideoCall;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCallStatus;

public record VideoCallResponse(long id, long conversationId, long callerId, long receiverId, Long signerId,
                                VideoCallStatus status, Long startedAt, Long endedAt, long createdAt) {
    public static VideoCallResponse from(VideoCall call, Long signerId) {
        return new VideoCallResponse(call.getId(), call.getConversationId(), call.getCallerId(),
                call.getReceiverId(), signerId, call.getStatus(), call.getStartedAt(), call.getEndedAt(), call.getCreatedAt());
    }
}
