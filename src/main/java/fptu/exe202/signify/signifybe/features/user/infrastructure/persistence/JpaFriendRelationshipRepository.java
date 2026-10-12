package fptu.exe202.signify.signifybe.features.user.infrastructure.persistence;

import fptu.exe202.signify.signifybe.features.user.domain.FriendRelationship;
import fptu.exe202.signify.signifybe.features.user.domain.FriendRelationshipStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface JpaFriendRelationshipRepository extends JpaRepository<FriendRelationship, Long> {
    Optional<FriendRelationship> findByUserLowIdAndUserHighId(long userLowId, long userHighId);

    @Query("select relationship.userLowId as userLowId, relationship.userHighId as userHighId from FriendRelationship relationship where relationship.id = :id")
    Optional<FriendPairProjection> findPairById(@Param("id") long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select relationship from FriendRelationship relationship where relationship.id = :id")
    Optional<FriendRelationship> lockById(@Param("id") long id);

    @Query("""
            select relationship from FriendRelationship relationship
             where relationship.status = :status
               and (relationship.userLowId = :userId or relationship.userHighId = :userId)
               and relationship.requestedById <> :userId
             order by relationship.createdAt desc, relationship.id desc
            """)
    Page<FriendRelationship> findIncoming(@Param("userId") long userId,
                                          @Param("status") FriendRelationshipStatus status,
                                          Pageable pageable);

    @Query("""
            select relationship from FriendRelationship relationship
             where relationship.status = :status and relationship.requestedById = :userId
             order by relationship.createdAt desc, relationship.id desc
            """)
    Page<FriendRelationship> findOutgoing(@Param("userId") long userId,
                                          @Param("status") FriendRelationshipStatus status,
                                          Pageable pageable);

    @Query("""
            select relationship from FriendRelationship relationship
             where relationship.status = :status
               and (relationship.userLowId = :userId or relationship.userHighId = :userId)
             order by relationship.acceptedAt desc, relationship.id desc
            """)
    Page<FriendRelationship> findFriends(@Param("userId") long userId,
                                         @Param("status") FriendRelationshipStatus status,
                                         Pageable pageable);

    @Modifying(flushAutomatically = true)
    @Query("delete from FriendRelationship relationship where relationship.userLowId = :userLowId and relationship.userHighId = :userHighId")
    int deletePair(@Param("userLowId") long userLowId, @Param("userHighId") long userHighId);
}
