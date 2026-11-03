package fptu.exe202.signify.signifybe.features.payment.infrastructure.persistence;

import fptu.exe202.signify.signifybe.features.payment.domain.SubscriptionStatus;
import fptu.exe202.signify.signifybe.features.payment.domain.UserSubscription;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JpaUserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {
    List<UserSubscription> findByUserIdOrderBySubscriptionStartAtDesc(long userId);

    @Query(value = "select pg_advisory_xact_lock(cast(:userId as bigint)) is null", nativeQuery = true)
    boolean lockRenewalForUser(@Param("userId") long userId);

    @Query("select s from UserSubscription s where s.userId=:userId and s.status=:status and s.subscriptionStartAt<=:at and s.subscriptionEndAt>:at order by s.subscriptionEndAt desc")
    List<UserSubscription> findActiveSubscriptions(@Param("userId") long userId, @Param("status") String status, @Param("at") long at);

    default Optional<UserSubscription> findActiveSubscription(long userId, long at) {
        return findActiveSubscriptions(userId, SubscriptionStatus.ACTIVE.getValue(), at).stream().findFirst();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UserSubscription s where s.id=:id")
    Optional<UserSubscription> findLockedById(@Param("id") long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UserSubscription s where s.userId=:userId and s.status=:status order by s.subscriptionEndAt desc")
    List<UserSubscription> findLockedByUserIdAndStatusOrderBySubscriptionEndAtDesc(@Param("userId") long userId, @Param("status") String status);
}
