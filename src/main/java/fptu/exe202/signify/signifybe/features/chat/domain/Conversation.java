package fptu.exe202.signify.signifybe.features.chat.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "conversation")
@Getter
@Setter
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "conversation_seq")
    @SequenceGenerator(name = "conversation_seq", sequenceName = "conversation_id_seq", allocationSize = 1)
    @Column(name = "conversation_id")
    private Long id;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(length = 100)
    private String name;

    @Column(name = "creator_id")
    private Long creatorId;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;

    protected Conversation() { }

    public Conversation(String type, String name, Long creatorId, long now) {
        this.type = type;
        this.name = name;
        this.creatorId = creatorId;
        this.createdAt = now;
        this.updatedAt = now;
    }
}
