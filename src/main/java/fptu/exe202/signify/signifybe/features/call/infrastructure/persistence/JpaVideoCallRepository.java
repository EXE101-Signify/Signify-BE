package fptu.exe202.signify.signifybe.features.call.infrastructure.persistence;

import fptu.exe202.signify.signifybe.features.call.domain.VideoCall;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JpaVideoCallRepository extends JpaRepository<VideoCall, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select call from VideoCall call where call.id = :id")
    Optional<VideoCall> findLockedById(@Param("id") long id);
}
