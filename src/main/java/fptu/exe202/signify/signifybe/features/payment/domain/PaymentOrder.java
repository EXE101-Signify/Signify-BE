package fptu.exe202.signify.signifybe.features.payment.domain;

import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "payment_orders")
public class PaymentOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_order_id", nullable = false)
    private Long id;

    @Column(name = "order_code", nullable = false, unique = true)
    private Long orderCode;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_plan_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_payment_orders_plan"))
    private SubscriptionPlan plan;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_subscription_id", unique = true,
            foreignKey = @ForeignKey(name = "fk_payment_orders_subscription"))
    private UserSubscription userSubscription;

    @Column(name = "order_type", nullable = false, length = 30)
    private String orderType;

    @Column(name = "order_status", nullable = false, length = 30)
    private String status;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "payment_provider", nullable = false, length = 30)
    private String paymentProvider;

    @Column(name = "provider_payment_link_id", length = 255)
    private String providerPaymentLinkId;

    @Column(name = "checkout_url", columnDefinition = "text")
    private String checkoutUrl;

    @Column(name = "order_expires_at")
    private Long orderExpiresAt;

    @Column(name = "paid_at")
    private Long paidAt;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;

    protected PaymentOrder() {
    }

    public PaymentOrder(long orderCode, long userId, SubscriptionPlan plan, String orderType, long now) {
        this.orderCode = orderCode;
        this.userId = userId;
        this.plan = plan;
        this.orderType = orderType;
        this.status = PaymentOrderStatus.PENDING.getValue();
        this.totalAmount = plan.getMonthlyPriceAmount();
        this.currencyCode = plan.getCurrencyCode();
        this.paymentProvider = PaymentProvider.PAYOS.getValue();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markPaid(long paidAt) {
        requirePending();
        status = PaymentOrderStatus.PAID.getValue();
        this.paidAt = paidAt;
        updatedAt = paidAt;
    }

    public void markExpired(long now) {
        requirePending();
        status = PaymentOrderStatus.EXPIRED.getValue();
        updatedAt = now;
    }

    public void markCancelled(long now) {
        requirePending();
        status = PaymentOrderStatus.CANCELLED.getValue();
        updatedAt = now;
    }

    private void requirePending() {
        if (!PaymentOrderStatus.PENDING.getValue().equals(status)) {
            throw PaymentOrderException.invalidTransition();
        }
    }

    public void assignUserSubscription(UserSubscription subscription) {
        this.userSubscription = subscription;
    }

}
