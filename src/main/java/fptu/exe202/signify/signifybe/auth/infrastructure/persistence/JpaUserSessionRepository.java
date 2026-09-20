package fptu.exe202.signify.signifybe.auth.infrastructure.persistence;

import fptu.exe202.signify.signifybe.auth.domain.UserSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface JpaUserSessionRepository extends JpaRepository<UserSession, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UserSession s where s.refreshTokenHash = :hash")
    Optional<UserSession> lockByHash(@Param("hash") String hash);

    @Modifying(flushAutomatically = true)
    @Query("update UserSession s set s.revokedAt = :now where s.userId = :userId and s.revokedAt is null")
    int revokeAll(@Param("userId") long userId, @Param("now") long now);
}
