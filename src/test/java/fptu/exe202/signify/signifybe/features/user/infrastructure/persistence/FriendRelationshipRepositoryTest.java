package fptu.exe202.signify.signifybe.features.user.infrastructure.persistence;

import fptu.exe202.signify.signifybe.features.auth.domain.Account;
import fptu.exe202.signify.signifybe.features.auth.infrastructure.persistence.JpaAccountRepository;
import fptu.exe202.signify.signifybe.features.user.domain.FriendRelationship;
import fptu.exe202.signify.signifybe.features.user.domain.User;
import fptu.exe202.signify.signifybe.features.user.domain.UserBlock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.liquibase.enabled=true",
        "spring.liquibase.change-log=classpath:db/changelog/friend-relationship-test.yaml",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.url=jdbc:h2:mem:friendtest;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FriendRelationshipRepositoryTest {
    @Autowired JpaFriendRelationshipRepository relationships;
    @Autowired JpaUserRepository users;
    @Autowired JpaAccountRepository accounts;
    @Autowired JpaUserBlockRepository blocks;

    @Test
    void migrationAndUniquePairConstraintWorkOnH2() {
        User alice = createUser("alice");
        User bob = createUser("bob");
        relationships.saveAndFlush(new FriendRelationship(alice.getId(), bob.getId(), alice.getId(), 100));

        assertEquals(1, relationships.count());
        assertThrows(DataIntegrityViolationException.class,
                () -> relationships.saveAndFlush(
                        new FriendRelationship(bob.getId(), alice.getId(), bob.getId(), 200)));
    }

    @Test
    void friendSearchMatchesCaseInsensitiveUsernameAndReturnsRelationshipState() {
        User alice = createUser("alice");
        User bob = createUser("BobBuilder");
        User blockedBob = createUser("bobBlocked");
        relationships.saveAndFlush(new FriendRelationship(alice.getId(), bob.getId(), alice.getId(), 100));
        blocks.saveAndFlush(new UserBlock(blockedBob.getId(), alice.getId(), 100));

        var result = users.searchFriendCandidates(alice.getId(), "bOb", PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        FriendSearchProjection candidate = result.getContent().get(0);
        assertEquals(bob.getId(), candidate.getUserId());
        assertEquals("BobBuilder", candidate.getUsername());
        assertEquals(alice.getId(), candidate.getRequestedById());
        assertTrue(candidate.getRelationshipStatus() != null);
    }

    private User createUser(String username) {
        User user = users.saveAndFlush(new User(username + "@example.com", username, "User", 1));
        accounts.saveAndFlush(new Account(user.getId(), username, "hash", 1));
        return user;
    }
}
