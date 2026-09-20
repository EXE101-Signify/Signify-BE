package fptu.exe202.signify.signifybe.auth.application;

import fptu.exe202.signify.signifybe.auth.domain.UserProfile;

public record AuthResult(UserProfile user, TokenPair tokens) { }
