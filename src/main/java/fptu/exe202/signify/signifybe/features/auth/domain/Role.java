package fptu.exe202.signify.signifybe.features.auth.domain;

public enum Role {
    USER, ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
