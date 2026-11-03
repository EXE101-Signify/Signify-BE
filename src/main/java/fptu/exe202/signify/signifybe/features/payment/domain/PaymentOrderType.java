package fptu.exe202.signify.signifybe.features.payment.domain;

import lombok.Getter;

@Getter
public enum PaymentOrderType {
    NEW_SUBSCRIPTION("NEW_SUBSCRIPTION"),
    RENEWAL("RENEWAL"),;

    private final String value;

    PaymentOrderType(String value) {
        this.value = value;
    }
}
