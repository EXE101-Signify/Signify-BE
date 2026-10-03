package fptu.exe202.signify.signifybe.features.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "message_reaction")
@Getter
public class MessageReaction {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "message_reaction_seq")
    @SequenceGenerator(name = "message_reaction_seq", sequenceName = "message_reaction_id_seq", allocationSize = 1)
    @Column(name = "reaction_id")
    private Long id;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "reaction", nullable = false, length = 50)
    private String reaction;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "updated_at")
    private Long updatedAt;

    protected MessageReaction() { }

    public MessageReaction(long messageId, long userId, String reaction, long createdAt) {
        this.messageId = messageId;
        this.userId = userId;
        this.reaction = reaction;
        this.createdAt = createdAt;
    }

    public void changeTo(String reaction, long updatedAt) {
        this.reaction = reaction;
        this.updatedAt = updatedAt;
    }
}
