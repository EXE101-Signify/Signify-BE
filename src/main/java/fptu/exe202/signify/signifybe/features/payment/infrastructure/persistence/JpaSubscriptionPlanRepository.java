package fptu.exe202.signify.signifybe.features.payment.infrastructure.persistence;

import fptu.exe202.signify.signifybe.features.payment.domain.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JpaSubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {
    Optional<SubscriptionPlan> findByPlanCodeAndActiveTrue(String planCode);
}

