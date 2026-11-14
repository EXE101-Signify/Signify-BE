package fptu.exe202.signify.signifybe.features.user.api.dto;


public record UserResponse(long userId, String username, String role, String email,
                           String firstName, String lastName, String fullName,
                           String avatar, boolean emailVerified, boolean isSigner,
                           String phone, String gender, String address) {
}
