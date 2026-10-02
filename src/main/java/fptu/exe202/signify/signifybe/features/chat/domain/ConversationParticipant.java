package fptu.exe202.signify.signifybe.features.chat.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "conversation_participant")
@Getter
@Setter
public class ConversationParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "conv_participant_seq")
    @SequenceGenerator(name = "conv_participant_seq", sequenceName = "conversation_participant_id_seq", allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "joined_at", nullable = false)
    private Long joinedAt;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected ConversationParticipant() { }

    public ConversationParticipant(Long conversationId, Long userId, long now) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.joinedAt = now;
        this.active = true;
    }
}
