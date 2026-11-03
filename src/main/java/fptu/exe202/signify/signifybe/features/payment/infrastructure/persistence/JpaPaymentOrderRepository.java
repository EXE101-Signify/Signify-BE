package fptu.exe202.signify.signifybe.features.payment.infrastructure.persistence;

import fptu.exe202.signify.signifybe.features.payment.domain.PaymentOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JpaPaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {
    List<PaymentOrder> findByUserIdOrderByCreatedAtDesc(long userId, Pageable pageable);

    Optional<PaymentOrder> findByOrderCode(long orderCode);

    Optional<PaymentOrder> findByOrderCodeAndUserId(long orderCode, long userId);

    Optional<PaymentOrder> findByPaymentProviderAndProviderPaymentLinkId(String provider, String linkId);

    List<PaymentOrder> findByUserIdAndStatusOrderByCreatedAtDesc(long userId, String status, Pageable pageable);

    @Query("select o from PaymentOrder o where o.userId=:userId and o.status=:status and (o.orderExpiresAt is null or o.orderExpiresAt>:at) order by o.createdAt desc")
    List<PaymentOrder> findPendingOrders(@Param("userId") long userId, @Param("status") String status, @Param("at") long at, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PaymentOrder o where o.id=:id")
    Optional<PaymentOrder> findLockedById(@Param("id") long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PaymentOrder o where o.orderCode=:code")
    Optional<PaymentOrder> findLockedByOrderCode(@Param("code") long code);
}
