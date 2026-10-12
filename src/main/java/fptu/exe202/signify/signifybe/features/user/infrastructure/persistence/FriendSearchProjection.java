package fptu.exe202.signify.signifybe.features.user.infrastructure.persistence;

import fptu.exe202.signify.signifybe.features.user.domain.FriendRelationshipStatus;

public interface FriendSearchProjection {
    Long getUserId();
    String getUsername();
    String getFullName();
    String getAvatar();
    FriendRelationshipStatus getRelationshipStatus();
    Long getRequestedById();
}
