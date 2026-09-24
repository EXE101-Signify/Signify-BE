package fptu.exe202.signify.signifybe.features.user.domain;

import fptu.exe202.signify.signifybe.features.auth.domain.Account;
import fptu.exe202.signify.signifybe.features.auth.domain.Role;

public record UserProfile(long userId, String username, Role role, String email,
                          String firstName, String lastName, String fullName,
                          String avatar, boolean emailVerified,
                          String phone, Boolean gender, String address) {
    public static UserProfile of(User user, Account account) {
        return new UserProfile(user.getId(), account.getUsername(), account.getRole(), user.getEmail(),
                user.getFirstName(), user.getLastName(), user.getFullName(), user.getAvatar(), user.isEmailVerified(),
                user.getPhone(), user.getGender(), user.getAddress());
    }
}
