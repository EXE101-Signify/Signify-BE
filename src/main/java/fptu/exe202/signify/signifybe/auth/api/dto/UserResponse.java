package fptu.exe202.signify.signifybe.auth.api.dto;

import fptu.exe202.signify.signifybe.auth.domain.UserProfile;

public record UserResponse(long userId, String username, String role, String email,
                           String firstName, String lastName, String fullName,
                           String avatar, boolean emailVerified) {
    public static UserResponse from(UserProfile user) {
        return new UserResponse(user.userId(), user.username(), user.role().name(), user.email(),
                user.firstName(), user.lastName(), user.fullName(), user.avatar(), user.emailVerified());
    }
}
