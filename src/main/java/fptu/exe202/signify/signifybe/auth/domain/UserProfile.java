package fptu.exe202.signify.signifybe.auth.domain;

public record UserProfile(long userId, String username, Role role, String email,
                          String firstName, String lastName, String fullName,
                          String avatar, boolean emailVerified) {
    public static UserProfile of(User user, Account account) {
        return new UserProfile(user.getId(), account.getUsername(), account.getRole(), user.getEmail(),
                user.getFirstName(), user.getLastName(), user.getFullName(), user.getAvatar(), user.isEmailVerified());
    }
}
