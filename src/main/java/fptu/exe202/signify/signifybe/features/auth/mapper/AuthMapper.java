package fptu.exe202.signify.signifybe.features.auth.mapper;

import fptu.exe202.signify.signifybe.features.auth.api.dto.AuthResponse;
import fptu.exe202.signify.signifybe.features.auth.api.dto.TokenResponse;
import fptu.exe202.signify.signifybe.features.auth.application.AuthResult;
import fptu.exe202.signify.signifybe.features.auth.application.TokenPair;
import fptu.exe202.signify.signifybe.features.user.mapper.UserMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring",
        uses = UserMapper.class,
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface AuthMapper {
    AuthResponse toAuthResponse(AuthResult result);

    @Mapping(target = "tokenType", constant = "Bearer")
    TokenResponse toTokenResponse(TokenPair pair);
}
