package fptu.exe202.signify.signifybe.features.user.infrastructure.persistence;

import fptu.exe202.signify.signifybe.features.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import java.util.Optional;

@Repository
public interface JpaUserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);
    boolean existsByEmailAndIdNot(String email, long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId")
    Optional<User> lockById(@Param("userId") long userId);

    @Query("""
            select u from User u join Account a on a.userId = u.id
            where :search = '' or lower(a.username) like lower(concat('%', :search, '%'))
                or lower(u.email) like lower(concat('%', :search, '%'))
                or lower(u.fullName) like lower(concat('%', :search, '%'))
            """)
    Page<User> searchUsers(@Param("search") String search, Pageable pageable);

    @Query("""
            select u.id as userId, a.username as username, u.fullName as fullName, u.avatar as avatar,
                   relationship.status as relationshipStatus, relationship.requestedById as requestedById
              from User u
              join Account a on a.userId = u.id
              left join FriendRelationship relationship
                on (relationship.userLowId = :currentUserId and relationship.userHighId = u.id)
                or (relationship.userHighId = :currentUserId and relationship.userLowId = u.id)
             where u.id <> :currentUserId
               and u.deletedAt is null
               and a.status = 'ACTIVE'
               and (lower(a.username) like lower(concat('%', :search, '%'))
                    or lower(u.email) like lower(concat('%', :search, '%')))
               and not exists (
                   select block.id from UserBlock block
                    where block.deletedAt is null
                      and ((block.blockerId = :currentUserId and block.blockedId = u.id)
                           or (block.blockerId = u.id and block.blockedId = :currentUserId))
               )
             order by a.username asc, u.id asc
            """)
    Page<FriendSearchProjection> searchFriendCandidates(@Param("currentUserId") long currentUserId,
                                                        @Param("search") String search,
                                                        Pageable pageable);

    Optional<User> findByEmail(String email);

    @Modifying
    @Query("update User u set u.online = true where u.id = :userId and u.online = false")
    int markOnline(@Param("userId") long userId);

    @Modifying
    @Query("update User u set u.online = false, u.lastSeenAt = :at where u.id = :userId and u.online = true")
    int markOffline(@Param("userId") long userId, @Param("at") long at);

    @Query("select u.id from User u where u.online = true")
    java.util.List<Long> findOnlineUserIds();
}
