package fptu.exe202.signify.signifybe.features.user.api.dto;

import fptu.exe202.signify.signifybe.features.user.domain.FriendshipState;

public record FriendSearchResponse(long userId, String username, String fullName, String avatar,
                                   FriendshipState relationshipStatus) { }
