package fptu.exe202.signify.signifybe.features.payment.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "subscription_plans")
public class SubscriptionPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_plan_id", nullable = false)
    private Long id;

    @Column(name = "plan_code", nullable = false, length = 50, unique = true)
    private String planCode;

    @Column(name = "plan_name", nullable = false, length = 100)
    private String planName;

    @Column(name = "plan_description", columnDefinition = "text")
    private String planDescription;

    @Column(name = "monthly_price_amount", nullable = false)
    private Long monthlyPriceAmount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "duration_months", nullable = false)
    private Integer durationMonths;

    @Column(name = "unlimited_video_calls", nullable = false)
    private boolean unlimitedVideoCalls;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;

    protected SubscriptionPlan() { }

}
