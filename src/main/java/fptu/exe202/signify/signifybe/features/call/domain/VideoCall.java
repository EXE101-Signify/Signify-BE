package fptu.exe202.signify.signifybe.features.call.domain;

import fptu.exe202.signify.signifybe.features.call.domain.exception.VideoCallException;
import jakarta.persistence.*;

@Entity
@Table(name = "video_calls")
public class VideoCall {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "video_calls_seq")
    @SequenceGenerator(name = "video_calls_seq", sequenceName = "video_calls_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "caller_id", nullable = false)
    private Long callerId;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VideoCallStatus status;

    @Column(name = "started_at")
    private Long startedAt;

    @Column(name = "ended_at")
    private Long endedAt;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    protected VideoCall() { }

    public VideoCall(long conversationId, long callerId, long receiverId, long createdAt) {
        this.conversationId = conversationId;
        this.callerId = callerId;
        this.receiverId = receiverId;
        this.status = VideoCallStatus.CALLING;
        this.createdAt = createdAt;
    }

    public void accept(long now) {
        transition(VideoCallStatus.CALLING, VideoCallStatus.ACCEPTED);
        startedAt = now;
    }

    public void reject() { transition(VideoCallStatus.CALLING, VideoCallStatus.REJECTED); }
    public void miss() { transition(VideoCallStatus.CALLING, VideoCallStatus.MISSED); }
    public void busy() { transition(VideoCallStatus.CALLING, VideoCallStatus.BUSY); }

    public void complete(long now) {
        transition(VideoCallStatus.ACCEPTED, VideoCallStatus.COMPLETED);
        endedAt = now;
    }

    private void transition(VideoCallStatus expected, VideoCallStatus next) {
        if (status != expected) throw VideoCallException.invalidTransition();
        status = next;
    }

    public Long getId() { return id; }
    public Long getConversationId() { return conversationId; }
    public Long getCallerId() { return callerId; }
    public Long getReceiverId() { return receiverId; }
    public VideoCallStatus getStatus() { return status; }
    public Long getStartedAt() { return startedAt; }
    public Long getEndedAt() { return endedAt; }
    public Long getCreatedAt() { return createdAt; }
}
