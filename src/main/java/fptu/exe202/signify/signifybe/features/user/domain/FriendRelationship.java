package fptu.exe202.signify.signifybe.features.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "friend_relationships")
@Getter
public class FriendRelationship {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "friend_relationships_seq")
    @SequenceGenerator(name = "friend_relationships_seq", sequenceName = "friend_relationships_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "user_low_id", nullable = false)
    private Long userLowId;

    @Column(name = "user_high_id", nullable = false)
    private Long userHighId;

    @Column(name = "requested_by_id", nullable = false)
    private Long requestedById;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FriendRelationshipStatus status;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;

    @Column(name = "accepted_at")
    private Long acceptedAt;

    protected FriendRelationship() { }

    public FriendRelationship(long userA, long userB, long requestedById, long now) {
        if (userA == userB || (requestedById != userA && requestedById != userB)) {
            throw new IllegalArgumentException("Invalid friend relationship");
        }
        this.userLowId = Math.min(userA, userB);
        this.userHighId = Math.max(userA, userB);
        this.requestedById = requestedById;
        this.status = FriendRelationshipStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean isPending() {
        return status == FriendRelationshipStatus.PENDING;
    }

    public boolean isAccepted() {
        return status == FriendRelationshipStatus.ACCEPTED;
    }

    public long otherUserId(long userId) {
        if (userLowId == userId) return userHighId;
        if (userHighId == userId) return userLowId;
        throw new IllegalArgumentException("User does not belong to relationship");
    }

    public boolean includes(long userId) {
        return userLowId == userId || userHighId == userId;
    }

    public void accept(long now) {
        if (status == FriendRelationshipStatus.ACCEPTED) return;
        status = FriendRelationshipStatus.ACCEPTED;
        acceptedAt = now;
        updatedAt = now;
    }
}
