package fptu.exe202.signify.signifybe.features.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "user_blocks")
@Getter
public class UserBlock {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_blocks_seq")
    @SequenceGenerator(name = "user_blocks_seq", sequenceName = "user_blocks_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "blocker_id", nullable = false)
    private Long blockerId;

    @Column(name = "blocked_id", nullable = false)
    private Long blockedId;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "deleted_at")
    private Long deletedAt;

    protected UserBlock() { }

    public UserBlock(long blockerId, long blockedId, long createdAt) {
        this.blockerId = blockerId;
        this.blockedId = blockedId;
        this.createdAt = createdAt;
    }

    public boolean isActive() { return deletedAt == null; }

    public void unblock(long now) { deletedAt = now; }

    public void reblock(long now) {
        createdAt = now;
        deletedAt = null;
    }
}
