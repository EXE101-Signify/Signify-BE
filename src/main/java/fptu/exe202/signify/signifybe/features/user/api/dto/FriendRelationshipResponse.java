package fptu.exe202.signify.signifybe.features.user.api.dto;

import fptu.exe202.signify.signifybe.features.user.domain.FriendshipState;

public record FriendRelationshipResponse(long relationshipId, FriendUserResponse user,
                                         FriendshipState status, long createdAt, Long acceptedAt) { }
