package fptu.exe202.signify.signifybe.features.user.api.dto;

public record BlockedUserResponse(long userId, String fullName, String avatar, long blockedAt) { }
