package fptu.exe202.signify.signifybe.features.payment.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "user_subscriptions")
public class UserSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_subscription_id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_plan_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_user_subscriptions_plan"))
    private SubscriptionPlan plan;

    @Column(name = "subscription_status", nullable = false, length = 30)
    private String status;

    @Column(name = "subscription_price_amount", nullable = false)
    private Long subscriptionPriceAmount;

    @Column(name = "subscription_start_at", nullable = false)
    private Long subscriptionStartAt;

    @Column(name = "subscription_end_at", nullable = false)
    private Long subscriptionEndAt;

    @Column(name = "activated_at")
    private Long activatedAt;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;

    protected UserSubscription() { }

    public UserSubscription(long userId, SubscriptionPlan plan, String status,
                            long subscriptionPriceAmount, long subscriptionStartAt,
                            long subscriptionEndAt, long activatedAt, long now) {
        this.userId = userId;
        this.plan = plan;
        this.status = status;
        this.subscriptionPriceAmount = subscriptionPriceAmount;
        this.subscriptionStartAt = subscriptionStartAt;
        this.subscriptionEndAt = subscriptionEndAt;
        this.activatedAt = activatedAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public SubscriptionPlan getPlan() { return plan; }
    public String getStatus() { return status; }
    public Long getSubscriptionPriceAmount() { return subscriptionPriceAmount; }
    public Long getSubscriptionStartAt() { return subscriptionStartAt; }
    public Long getSubscriptionEndAt() { return subscriptionEndAt; }
    public Long getActivatedAt() { return activatedAt; }
    public Long getCreatedAt() { return createdAt; }
    public Long getUpdatedAt() { return updatedAt; }
}
