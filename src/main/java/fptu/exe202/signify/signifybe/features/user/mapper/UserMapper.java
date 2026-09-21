package fptu.exe202.signify.signifybe.features.user.mapper;
import fptu.exe202.signify.signifybe.features.user.api.dto.UserResponse;
import fptu.exe202.signify.signifybe.features.user.domain.UserProfile;

public final class UserMapper {
    private UserMapper() { }
    public static UserResponse toResponse(UserProfile profile) {
        return new UserResponse(profile.userId(), profile.username(), profile.role().name(), profile.email(),
                profile.firstName(), profile.lastName(), profile.fullName(), profile.avatar(), profile.emailVerified());
    }
}
