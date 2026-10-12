package fptu.exe202.signify.signifybe.features.user.application;

import fptu.exe202.signify.signifybe.features.auth.domain.Account;
import fptu.exe202.signify.signifybe.features.auth.infrastructure.persistence.JpaAccountRepository;
import fptu.exe202.signify.signifybe.features.notification.application.NotificationService;
import fptu.exe202.signify.signifybe.features.user.domain.FriendRelationship;
import fptu.exe202.signify.signifybe.features.user.domain.FriendshipState;
import fptu.exe202.signify.signifybe.features.user.domain.User;
import fptu.exe202.signify.signifybe.features.user.domain.exception.BlockException;
import fptu.exe202.signify.signifybe.features.user.domain.exception.FriendException;
import fptu.exe202.signify.signifybe.features.user.infrastructure.persistence.FriendPairProjection;
import fptu.exe202.signify.signifybe.features.user.infrastructure.persistence.JpaFriendRelationshipRepository;
import fptu.exe202.signify.signifybe.features.user.infrastructure.persistence.JpaUserBlockRepository;
import fptu.exe202.signify.signifybe.features.user.infrastructure.persistence.JpaUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendServiceTest {
    @Mock JpaFriendRelationshipRepository relationships;
    @Mock JpaUserRepository users;
    @Mock JpaAccountRepository accounts;
    @Mock JpaUserBlockRepository blocks;
    @Mock NotificationService notifications;

    FriendService service;

    @BeforeEach
    void setUp() {
        service = new FriendService(relationships, users, accounts, blocks, notifications,
                Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC));
        allowPair(1, "alice", 2, "bob");
    }

    @Test
    void sendsNewRequestAndNotifiesTarget() {
        when(relationships.findByUserLowIdAndUserHighId(1, 2)).thenReturn(Optional.empty());
        when(relationships.saveAndFlush(any(FriendRelationship.class))).thenAnswer(invocation -> {
            FriendRelationship relationship = invocation.getArgument(0);
            ReflectionTestUtils.setField(relationship, "id", 10L);
            return relationship;
        });

        var response = service.send(1, 2);

        assertEquals(10, response.relationshipId());
        assertEquals(FriendshipState.OUTGOING_PENDING, response.status());
        assertEquals("bob", response.user().username());
        verify(notifications).createFriendRequest(2, 10);
    }

    @Test
    void repeatedRequestIsIdempotent() {
        FriendRelationship existing = relationship(1, 2, 1, 10);
        when(relationships.findByUserLowIdAndUserHighId(1, 2)).thenReturn(Optional.of(existing));

        var response = service.send(1, 2);

        assertEquals(FriendshipState.OUTGOING_PENDING, response.status());
        verify(relationships, never()).saveAndFlush(any());
        verify(notifications, never()).createFriendRequest(any(Long.class), any(Long.class));
    }

    @Test
    void oppositeRequestAutomaticallyAccepts() {
        FriendRelationship existing = relationship(1, 2, 2, 10);
        when(relationships.findByUserLowIdAndUserHighId(1, 2)).thenReturn(Optional.of(existing));

        var response = service.send(1, 2);

        assertEquals(FriendshipState.FRIEND, response.status());
        assertEquals(1_000, response.acceptedAt());
        verify(notifications).createFriendRequestAccepted(2, 10);
    }

    @Test
    void refusesRequestWhenEitherUserHasBlockedTheOther() {
        when(blocks.existsByBlockerIdAndBlockedIdAndDeletedAtIsNull(1, 2)).thenReturn(false);
        when(blocks.existsByBlockerIdAndBlockedIdAndDeletedAtIsNull(2, 1)).thenReturn(true);

        assertThrows(BlockException.class, () -> service.send(1, 2));

        verify(relationships, never()).saveAndFlush(any());
    }

    @Test
    void receiverCanAcceptOwnedIncomingRequest() {
        FriendRelationship existing = relationship(1, 2, 1, 10);
        when(relationships.findPairById(10)).thenReturn(Optional.of(pair(1, 2)));
        when(relationships.lockById(10)).thenReturn(Optional.of(existing));

        var response = service.accept(2, 10);

        assertEquals("alice", response.user().username());
        assertEquals(1_000, response.friendsSince());
        verify(notifications).createFriendRequestAccepted(1, 10);
    }

    @Test
    void senderCannotAcceptOwnRequest() {
        FriendRelationship existing = relationship(1, 2, 1, 10);
        when(relationships.findPairById(10)).thenReturn(Optional.of(pair(1, 2)));
        when(relationships.lockById(10)).thenReturn(Optional.of(existing));

        assertThrows(FriendException.class, () -> service.accept(1, 10));

        verify(notifications, never()).createFriendRequestAccepted(any(Long.class), any(Long.class));
    }

    private void allowPair(long firstId, String firstUsername, long secondId, String secondUsername) {
        User first = user(firstId, firstUsername);
        User second = user(secondId, secondUsername);
        Account firstAccount = account(firstId, firstUsername);
        Account secondAccount = account(secondId, secondUsername);
        when(accounts.lockByUserId(firstId)).thenReturn(Optional.of(firstAccount));
        when(accounts.lockByUserId(secondId)).thenReturn(Optional.of(secondAccount));
        when(users.lockById(firstId)).thenReturn(Optional.of(first));
        when(users.lockById(secondId)).thenReturn(Optional.of(second));
    }

    private User user(long id, String name) {
        User user = new User(name + "@example.com", name, "User", 1);
        user.setId(id);
        return user;
    }

    private Account account(long id, String username) {
        return new Account(id, username, "hash", 1);
    }

    private FriendRelationship relationship(long userA, long userB, long requester, long id) {
        FriendRelationship relationship = new FriendRelationship(userA, userB, requester, 100);
        ReflectionTestUtils.setField(relationship, "id", id);
        return relationship;
    }

    private FriendPairProjection pair(long low, long high) {
        return new FriendPairProjection() {
            @Override public Long getUserLowId() { return low; }
            @Override public Long getUserHighId() { return high; }
        };
    }
}
