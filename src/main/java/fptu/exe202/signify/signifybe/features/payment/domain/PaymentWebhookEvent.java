package fptu.exe202.signify.signifybe.features.payment.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "payment_webhook_events")
@Getter
@Setter
public class PaymentWebhookEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_webhook_event_id", nullable = false)
    private Long id;

    @Column(name = "payment_provider", nullable = false, length = 30)
    private String paymentProvider;

    @Column(name = "provider_event_key", length = 255)
    private String providerEventKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_order_id", foreignKey = @ForeignKey(name = "fk_payment_webhook_events_order"))
    private PaymentOrder order;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "webhook_payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> webhookPayload;

    @Column(name = "signature_valid", nullable = false)
    private boolean signatureValid;

    @Column(name = "processing_status", nullable = false, length = 30)
    private String processingStatus;

    @Column(name = "processing_error_message", columnDefinition = "text")
    private String processingErrorMessage;

    @Column(name = "received_at", nullable = false)
    private Long receivedAt;

    @Column(name = "processed_at")
    private Long processedAt;

    public PaymentWebhookEvent() { }

}
