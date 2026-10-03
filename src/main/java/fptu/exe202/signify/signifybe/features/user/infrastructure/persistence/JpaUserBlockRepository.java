package fptu.exe202.signify.signifybe.features.user.infrastructure.persistence;

import fptu.exe202.signify.signifybe.features.user.domain.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JpaUserBlockRepository extends JpaRepository<UserBlock, Long> {
    Optional<UserBlock> findByBlockerIdAndBlockedId(long blockerId, long blockedId);
    boolean existsByBlockerIdAndBlockedIdAndDeletedAtIsNull(long blockerId, long blockedId);
    List<UserBlock> findByBlockerIdAndDeletedAtIsNullOrderByCreatedAtDesc(long blockerId);
}
