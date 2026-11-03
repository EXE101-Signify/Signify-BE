package fptu.exe202.signify.signifybe.features.payment.domain;

import lombok.Getter;

@Getter
public enum PaymentProvider {
    PAYOS("PAYOS");

    private String value;
    PaymentProvider(String value) {
        this.value = value;
    }
}
