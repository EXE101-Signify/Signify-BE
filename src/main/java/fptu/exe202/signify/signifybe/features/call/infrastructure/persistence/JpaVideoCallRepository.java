package fptu.exe202.signify.signifybe.features.call.infrastructure.persistence;

import fptu.exe202.signify.signifybe.features.call.domain.VideoCall;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCallStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface JpaVideoCallRepository extends JpaRepository<VideoCall, Long> {
    @Query(value = "select pg_advisory_xact_lock(cast(:userId as bigint)) is null", nativeQuery = true)
    boolean lockParticipant(@Param("userId") long userId);

    @Query("select count(c)>0 from VideoCall c where c.status in :statuses and (c.callerId=:userId or c.receiverId=:userId)")
    boolean hasOngoingCall(@Param("userId") long userId, @Param("statuses") List<VideoCallStatus> statuses);

    @Query("select c.id from VideoCall c where c.status=:status")
    List<Long> findIdsByStatus(@Param("status") VideoCallStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select call from VideoCall call where call.id = :id")
    Optional<VideoCall> findLockedById(@Param("id") long id);
}
