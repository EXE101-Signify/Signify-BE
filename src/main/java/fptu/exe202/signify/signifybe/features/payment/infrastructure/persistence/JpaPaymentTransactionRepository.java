package fptu.exe202.signify.signifybe.features.payment.infrastructure.persistence;

import fptu.exe202.signify.signifybe.features.payment.domain.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface JpaPaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    List<PaymentTransaction> findByOrderIdOrderByCreatedAtDesc(long orderId);
    List<PaymentTransaction> findByOrderIdAndStatus(long orderId, String status);
    Optional<PaymentTransaction> findByPaymentProviderAndProviderTransactionReference(String provider, String reference);
}
