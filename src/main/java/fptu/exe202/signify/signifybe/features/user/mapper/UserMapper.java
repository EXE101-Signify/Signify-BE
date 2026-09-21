package fptu.exe202.signify.signifybe.features.user.mapper;
import fptu.exe202.signify.signifybe.features.user.api.dto.UserResponse;
import fptu.exe202.signify.signifybe.features.user.domain.UserProfile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface UserMapper {
    @Mapping(target = "role", source = "role")
    UserResponse toResponse(UserProfile profile);
}
