package fptu.exe202.signify.signifybe.features.payment.domain;

import lombok.Getter;

@Getter
public enum SubscriptionStatus {
    PENDING("PENDING"),
    ACTIVE("ACTIVE"),
    EXPIRED("EXPIRED"),
    CANCELLED("CANCELLED"),
    FAILED("FAILED");

    private final String value;

    SubscriptionStatus(String value) {
        this.value = value;
    }
}
