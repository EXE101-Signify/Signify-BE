package fptu.exe202.signify.signifybe.features.user.application;

import fptu.exe202.signify.signifybe.common.UserValidation;
import fptu.exe202.signify.signifybe.features.auth.domain.Account;
import fptu.exe202.signify.signifybe.features.auth.infrastructure.persistence.JpaAccountRepository;
import fptu.exe202.signify.signifybe.features.notification.application.NotificationService;
import fptu.exe202.signify.signifybe.features.user.api.dto.FriendRelationshipResponse;
import fptu.exe202.signify.signifybe.features.user.api.dto.FriendRequestResponse;
import fptu.exe202.signify.signifybe.features.user.api.dto.FriendResponse;
import fptu.exe202.signify.signifybe.features.user.api.dto.FriendSearchResponse;
import fptu.exe202.signify.signifybe.features.user.api.dto.FriendUserResponse;
import fptu.exe202.signify.signifybe.features.user.domain.FriendRelationship;
import fptu.exe202.signify.signifybe.features.user.domain.FriendRelationshipStatus;
import fptu.exe202.signify.signifybe.features.user.domain.FriendshipState;
import fptu.exe202.signify.signifybe.features.user.domain.User;
import fptu.exe202.signify.signifybe.features.user.domain.exception.BlockException;
import fptu.exe202.signify.signifybe.features.user.domain.exception.FriendException;
import fptu.exe202.signify.signifybe.features.user.infrastructure.persistence.FriendSearchProjection;
import fptu.exe202.signify.signifybe.features.user.infrastructure.persistence.FriendPairProjection;
import fptu.exe202.signify.signifybe.features.user.infrastructure.persistence.JpaFriendRelationshipRepository;
import fptu.exe202.signify.signifybe.features.user.infrastructure.persistence.JpaUserBlockRepository;
import fptu.exe202.signify.signifybe.features.user.infrastructure.persistence.JpaUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FriendService {
    private final JpaFriendRelationshipRepository relationships;
    private final JpaUserRepository users;
    private final JpaAccountRepository accounts;
    private final JpaUserBlockRepository blocks;
    private final NotificationService notifications;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<FriendSearchResponse> search(long currentUserId, String query, int page, int size) {
        validatePagination(page, size);
        String normalized = query == null ? "" : query.strip();
        if (normalized.isEmpty() || normalized.length() > UserValidation.SEARCH_MAX_LENGTH) {
            throw new IllegalArgumentException("Invalid search query");
        }
        return users.searchFriendCandidates(currentUserId, normalized, PageRequest.of(page, size))
                .map(candidate -> new FriendSearchResponse(candidate.getUserId(), candidate.getUsername(),
                        candidate.getFullName(), candidate.getAvatar(), state(currentUserId, candidate)));
    }

    @Transactional
    public FriendRelationshipResponse send(long senderId, long targetId) {
        if (senderId == targetId) throw FriendException.selfRequest();
        LockedPair pair = lockPair(senderId, targetId);
        assertCanInteract(senderId, targetId);

        FriendRelationship existing = relationships.findByUserLowIdAndUserHighId(pair.low().getId(), pair.high().getId())
                .orElse(null);
        if (existing != null) {
            if (existing.isAccepted()) throw FriendException.alreadyFriends();
            if (existing.getRequestedById() == senderId) {
                return relationshipResponse(existing, senderId, pair);
            }
            existing.accept(clock.millis());
            relationships.flush();
            notifications.createFriendRequestAccepted(existing.getRequestedById(), existing.getId());
            return relationshipResponse(existing, senderId, pair);
        }

        FriendRelationship created = relationships.saveAndFlush(
                new FriendRelationship(senderId, targetId, senderId, clock.millis()));
        notifications.createFriendRequest(targetId, created.getId());
        return relationshipResponse(created, senderId, pair);
    }

    @Transactional(readOnly = true)
    public Page<FriendRequestResponse> incoming(long userId, int page, int size) {
        validatePagination(page, size);
        return mapRequests(relationships.findIncoming(userId, FriendRelationshipStatus.PENDING,
                PageRequest.of(page, size)), userId);
    }

    @Transactional(readOnly = true)
    public Page<FriendRequestResponse> outgoing(long userId, int page, int size) {
        validatePagination(page, size);
        return mapRequests(relationships.findOutgoing(userId, FriendRelationshipStatus.PENDING,
                PageRequest.of(page, size)), userId);
    }

    @Transactional
    public FriendResponse accept(long receiverId, long requestId) {
        LockedRelationship locked = lockRelationship(requestId);
        FriendRelationship relationship = locked.relationship();
        if (!relationship.isPending() || relationship.getRequestedById() == receiverId
                || !relationship.includes(receiverId)) {
            throw FriendException.requestNotFound();
        }
        assertCanInteract(relationship.getUserLowId(), relationship.getUserHighId());
        relationship.accept(clock.millis());
        relationships.flush();
        notifications.createFriendRequestAccepted(relationship.getRequestedById(), relationship.getId());
        return friendResponse(relationship, receiverId, locked.pair());
    }

    @Transactional
    public void reject(long receiverId, long requestId) {
        FriendRelationship relationship = lockRelationship(requestId).relationship();
        if (!relationship.isPending() || relationship.getRequestedById() == receiverId
                || !relationship.includes(receiverId)) {
            throw FriendException.requestNotFound();
        }
        relationships.delete(relationship);
        relationships.flush();
    }

    @Transactional
    public void cancel(long senderId, long requestId) {
        FriendRelationship relationship = lockRelationship(requestId).relationship();
        if (!relationship.isPending() || relationship.getRequestedById() != senderId) {
            throw FriendException.requestNotFound();
        }
        relationships.delete(relationship);
        relationships.flush();
    }

    @Transactional(readOnly = true)
    public Page<FriendResponse> friends(long userId, int page, int size) {
        validatePagination(page, size);
        Page<FriendRelationship> pageResult = relationships.findFriends(userId, FriendRelationshipStatus.ACCEPTED,
                PageRequest.of(page, size));
        Map<Long, FriendUserResponse> profiles = profiles(pageResult.getContent().stream()
                .map(relationship -> relationship.otherUserId(userId)).toList());
        return pageResult.map(relationship -> new FriendResponse(relationship.getId(),
                requireProfile(profiles, relationship.otherUserId(userId)), relationship.getAcceptedAt()));
    }

    @Transactional
    public void unfriend(long userId, long friendUserId) {
        if (userId == friendUserId) throw FriendException.friendshipNotFound();
        LockedPair pair = lockPair(userId, friendUserId);
        FriendRelationship relationship = relationships.findByUserLowIdAndUserHighId(
                        pair.low().getId(), pair.high().getId())
                .filter(FriendRelationship::isAccepted)
                .orElseThrow(FriendException::friendshipNotFound);
        relationships.delete(relationship);
        relationships.flush();
    }

    private Page<FriendRequestResponse> mapRequests(Page<FriendRelationship> page, long currentUserId) {
        Map<Long, FriendUserResponse> profiles = profiles(page.getContent().stream()
                .map(relationship -> relationship.otherUserId(currentUserId)).toList());
        return page.map(relationship -> new FriendRequestResponse(relationship.getId(),
                requireProfile(profiles, relationship.otherUserId(currentUserId)), relationship.getCreatedAt()));
    }

    private Map<Long, FriendUserResponse> profiles(List<Long> userIds) {
        Map<Long, User> foundUsers = new HashMap<>();
        users.findAllById(userIds).forEach(user -> foundUsers.put(user.getId(), user));
        Map<Long, Account> foundAccounts = new HashMap<>();
        accounts.findAllById(userIds).forEach(account -> foundAccounts.put(account.getUserId(), account));
        Map<Long, FriendUserResponse> result = new HashMap<>();
        for (Long userId : userIds) {
            User user = foundUsers.get(userId);
            Account account = foundAccounts.get(userId);
            if (user != null && account != null) result.put(userId, profile(user, account));
        }
        return result;
    }

    private FriendUserResponse requireProfile(Map<Long, FriendUserResponse> profiles, long userId) {
        FriendUserResponse profile = profiles.get(userId);
        if (profile == null) throw FriendException.userNotFound();
        return profile;
    }

    private FriendRelationshipResponse relationshipResponse(FriendRelationship relationship, long currentUserId,
                                                            LockedPair pair) {
        long otherId = relationship.otherUserId(currentUserId);
        User other = pair.user(otherId);
        Account account = pair.account(otherId);
        FriendshipState state = relationship.isAccepted() ? FriendshipState.FRIEND
                : relationship.getRequestedById() == currentUserId
                ? FriendshipState.OUTGOING_PENDING : FriendshipState.INCOMING_PENDING;
        return new FriendRelationshipResponse(relationship.getId(), profile(other, account), state,
                relationship.getCreatedAt(), relationship.getAcceptedAt());
    }

    private FriendResponse friendResponse(FriendRelationship relationship, long currentUserId, LockedPair pair) {
        long otherId = relationship.otherUserId(currentUserId);
        return new FriendResponse(relationship.getId(), profile(pair.user(otherId), pair.account(otherId)),
                relationship.getAcceptedAt());
    }

    private FriendUserResponse profile(User user, Account account) {
        return new FriendUserResponse(user.getId(), account.getUsername(), user.getFullName(), user.getAvatar());
    }

    private FriendshipState state(long currentUserId, FriendSearchProjection candidate) {
        if (candidate.getRelationshipStatus() == null) return FriendshipState.NONE;
        if (candidate.getRelationshipStatus() == FriendRelationshipStatus.ACCEPTED) return FriendshipState.FRIEND;
        return candidate.getRequestedById() == currentUserId
                ? FriendshipState.OUTGOING_PENDING : FriendshipState.INCOMING_PENDING;
    }

    private LockedPair lockPair(long userA, long userB) {
        long lowId = Math.min(userA, userB);
        long highId = Math.max(userA, userB);
        Account lowAccount = accounts.lockByUserId(lowId).filter(Account::isActive)
                .orElseThrow(FriendException::userNotFound);
        Account highAccount = accounts.lockByUserId(highId).filter(Account::isActive)
                .orElseThrow(FriendException::userNotFound);
        User low = users.lockById(lowId).filter(user -> !user.isDeleted())
                .orElseThrow(FriendException::userNotFound);
        User high = users.lockById(highId).filter(user -> !user.isDeleted())
                .orElseThrow(FriendException::userNotFound);
        return new LockedPair(low, high, lowAccount, highAccount);
    }

    private LockedRelationship lockRelationship(long relationshipId) {
        FriendPairProjection ids = relationships.findPairById(relationshipId)
                .orElseThrow(FriendException::requestNotFound);
        LockedPair pair = lockPair(ids.getUserLowId(), ids.getUserHighId());
        FriendRelationship relationship = relationships.lockById(relationshipId)
                .orElseThrow(FriendException::requestNotFound);
        return new LockedRelationship(relationship, pair);
    }

    private void assertCanInteract(long userA, long userB) {
        if (blocks.existsByBlockerIdAndBlockedIdAndDeletedAtIsNull(userA, userB)
                || blocks.existsByBlockerIdAndBlockedIdAndDeletedAtIsNull(userB, userA)) {
            throw BlockException.interactionUnavailable();
        }
    }

    private void validatePagination(int page, int size) {
        if (page < 0 || size < 1 || size > UserValidation.PAGE_MAX_SIZE) {
            throw new IllegalArgumentException("Invalid pagination");
        }
    }

    private record LockedPair(User low, User high, Account lowAccount, Account highAccount) {
        User user(long userId) {
            if (low.getId() == userId) return low;
            if (high.getId() == userId) return high;
            throw FriendException.userNotFound();
        }

        Account account(long userId) {
            if (lowAccount.getUserId() == userId) return lowAccount;
            if (highAccount.getUserId() == userId) return highAccount;
            throw FriendException.userNotFound();
        }
    }

    private record LockedRelationship(FriendRelationship relationship, LockedPair pair) { }
}
