package fptu.exe202.signify.signifybe.auth.domain;

public enum Role {
    USER, ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
