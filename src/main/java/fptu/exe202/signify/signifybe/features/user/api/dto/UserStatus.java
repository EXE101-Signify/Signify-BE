package fptu.exe202.signify.signifybe.features.user.api.dto;

import lombok.Getter;

@Getter
public enum UserStatus {
    ACTIVE("ACTIVE"),
    INACTIVE("INACTIVE"),
    BANNED("BANNED"),
    DELETED("DELETED");

    private final String value;

    UserStatus(String value) {
        this.value = value;
    }
}
