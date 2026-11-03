package fptu.exe202.signify.signifybe.features.payment.domain;

import lombok.Getter;

@Getter
public enum PaymentOrderStatus {
    PENDING("PENDING"),
    PAID("PAID"),
    FAILED("FAILED"),
    CANCELLED("CANCELLED"),
    EXPIRED("EXPIRED"),;

    private final String value;

    PaymentOrderStatus(String value) {
        this.value = value;
    }
}
