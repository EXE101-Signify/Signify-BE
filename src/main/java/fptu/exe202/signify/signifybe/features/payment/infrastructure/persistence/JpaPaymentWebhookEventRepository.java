package fptu.exe202.signify.signifybe.features.payment.infrastructure.persistence;

import fptu.exe202.signifybe.features.payment.domain.PaymentWebhookEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface JpaPaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> {
    Optional<PaymentWebhookEvent> findByPaymentProviderAndProviderEventKey(String provider, String eventKey);
    List<PaymentWebhookEvent> findByOrderIdOrderByReceivedAtDesc(long orderId);
    List<PaymentWebhookEvent> findByProcessingStatusOrderByReceivedAtAsc(String status, Pageable pageable);
}
