package fptu.exe202.signify.signifybe.features.auth.application;

import fptu.exe202.signify.signifybe.features.user.domain.UserProfile;

public record AuthResult(UserProfile user, TokenPair tokens) { }
