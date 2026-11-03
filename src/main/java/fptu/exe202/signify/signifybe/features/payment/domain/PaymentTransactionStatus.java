package fptu.exe202.signify.signifybe.features.payment.domain;

import lombok.Getter;

@Getter
public enum PaymentTransactionStatus {
    PENDING("PENDING"),
    COMPLETED("COMPLETED"),
    FAILED("FAILED"),
    CANCELLED("CANCELLED");

    private final String value;

    PaymentTransactionStatus(String value) {
        this.value = value;
    }
}
