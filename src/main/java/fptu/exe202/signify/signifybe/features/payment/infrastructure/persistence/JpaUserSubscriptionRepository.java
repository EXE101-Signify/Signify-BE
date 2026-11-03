package fptu.exe202.signify.signifybe.features.payment.infrastructure.persistence;

import fptu.exe202.signify.signifybe.features.payment.domain.SubscriptionStatus;
import fptu.exe202.signify.signifybe.features.payment.domain.UserSubscription;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JpaUserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {
    @EntityGraph(attributePaths = "plan")
    List<UserSubscription> findByUserIdOrderBySubscriptionStartAtDesc(long userId);

    @Query(value = "select pg_advisory_xact_lock(cast(:userId as bigint)) is null", nativeQuery = true)
    boolean lockRenewalForUser(@Param("userId") long userId);

    @EntityGraph(attributePaths = "plan")
    @Query("select s from UserSubscription s where s.userId=:userId and s.status in :statuses and s.activatedAt is not null and s.subscriptionStartAt<=:at and s.subscriptionEndAt>:at order by s.subscriptionEndAt desc")
    List<UserSubscription> findActiveSubscriptions(@Param("userId") long userId, @Param("statuses") List<String> statuses, @Param("at") long at);

    default Optional<UserSubscription> findActiveSubscription(long userId, long at) {
        return findActiveSubscriptions(userId, List.of(SubscriptionStatus.ACTIVE.getValue(),
                SubscriptionStatus.PENDING.getValue()), at).stream().findFirst();
    }

    @Query("select max(s.subscriptionEndAt) from UserSubscription s where s.userId=:userId and s.status in :statuses and s.activatedAt is not null and s.subscriptionEndAt>:at")
    Optional<Long> findLatestEntitlementEnd(@Param("userId") long userId,
            @Param("statuses") List<String> statuses, @Param("at") long at);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UserSubscription s where s.id=:id")
    Optional<UserSubscription> findLockedById(@Param("id") long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UserSubscription s where s.userId=:userId and s.status in :statuses and s.activatedAt is not null and s.subscriptionEndAt>:at order by s.subscriptionEndAt desc")
    List<UserSubscription> findLockedRenewalPeriods(@Param("userId") long userId,
            @Param("statuses") List<String> statuses, @Param("at") long at);

    @Modifying
    @Query("update UserSubscription s set s.status=:active, s.updatedAt=:now where s.status=:pending and s.activatedAt is not null and s.subscriptionStartAt<=:now and s.subscriptionEndAt>:now")
    int activateStartedPeriods(@Param("pending") String pending, @Param("active") String active, @Param("now") long now);

    @Modifying
    @Query("update UserSubscription s set s.status=:expired, s.updatedAt=:now where s.status in :eligible and s.activatedAt is not null and s.subscriptionEndAt<=:now")
    int expireEndedPeriods(@Param("eligible") List<String> eligible, @Param("expired") String expired,
                           @Param("now") long now);
}
