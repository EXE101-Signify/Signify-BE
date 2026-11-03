package fptu.exe202.signify.signifybe.features.payment.domain;

import lombok.Getter;

@Getter
public enum WebhookProcessingStatus {
    RECEIVED("RECEIVED"),
    PROCESSED("PROCESSED"),
    FAILED("FAILED"),
    IGNORED("IGNORED");

    private final String value;

    WebhookProcessingStatus(String value) {
        this.value = value;
    }
}
