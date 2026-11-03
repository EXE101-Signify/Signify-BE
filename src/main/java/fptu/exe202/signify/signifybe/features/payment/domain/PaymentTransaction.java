package fptu.exe202.signify.signifybe.features.payment.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "payment_transactions")
public class PaymentTransaction {
    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_transaction_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_order_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_payment_transactions_order"))
    private PaymentOrder order;

    @Column(name = "payment_provider", nullable = false, length = 30)
    private String paymentProvider;

    @Column(name = "provider_transaction_reference", length = 255)
    private String providerTransactionReference;

    @Column(name = "transaction_amount", nullable = false)
    private Long transactionAmount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "transaction_status", nullable = false, length = 30)
    private String status;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message", columnDefinition = "text")
    private String failureMessage;

    @Column(name = "completed_at")
    private Long completedAt;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    protected PaymentTransaction() { }

}
