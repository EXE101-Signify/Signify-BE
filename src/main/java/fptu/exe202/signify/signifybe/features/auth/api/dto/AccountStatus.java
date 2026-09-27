package fptu.exe202.signify.signifybe.features.auth.api.dto;

import lombok.Getter;

@Getter
public enum AccountStatus {
    PENDING_VERIFICATION("PENDING_VERIFICATION"),
    ACTIVE("ACTIVE"),
    SUSPENDED("SUSPENDED"),
    LOCKED("LOCKED"),
    DEACTIVATED("DEACTIVATED"),
    DELETED("DELETED");

    private final String value;

    AccountStatus(String value) {
        this.value = value;
    }
}
