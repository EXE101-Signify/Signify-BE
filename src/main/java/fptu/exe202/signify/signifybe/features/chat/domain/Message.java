package fptu.exe202.signify.signifybe.features.chat.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "message")
@Getter
@Setter
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "message_seq")
    @SequenceGenerator(name = "message_seq", sequenceName = "message_id_seq", allocationSize = 1)
    @Column(name = "message_id")
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "message_type", nullable = false, length = 20)
    private String messageType;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "edited_at")
    private Long editedAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    protected Message() { }

    public Message(Long conversationId, Long senderId, String content, String messageType, long createdAt) {
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.content = content;
        this.messageType = messageType;
        this.createdAt = createdAt;
        this.deleted = false;
    }
}
