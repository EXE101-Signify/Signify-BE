package fptu.exe202.signify.signifybe.features.user.application;

import fptu.exe202.signify.signifybe.features.user.application.port.out.UserRepository;
import fptu.exe202.signify.signifybe.features.user.domain.User;
import fptu.exe202.signify.signifybe.features.user.domain.UserBlock;
import fptu.exe202.signify.signifybe.features.user.infrastructure.persistence.JpaFriendRelationshipRepository;
import fptu.exe202.signify.signifybe.features.user.infrastructure.persistence.JpaUserBlockRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockServiceFriendshipTest {
    @Test
    void blockingDeletesAnyFriendRelationshipForThePair() {
        JpaUserBlockRepository blocks = mock(JpaUserBlockRepository.class);
        UserRepository users = mock(UserRepository.class);
        JpaFriendRelationshipRepository relationships = mock(JpaFriendRelationshipRepository.class);
        User first = user(2, "first");
        User second = user(9, "second");
        when(users.lockUserById(2)).thenReturn(Optional.of(first));
        when(users.lockUserById(9)).thenReturn(Optional.of(second));
        when(blocks.findByBlockerIdAndBlockedId(9, 2)).thenReturn(Optional.empty());
        when(blocks.saveAndFlush(org.mockito.ArgumentMatchers.any(UserBlock.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        BlockService service = new BlockService(blocks, users, relationships,
                Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC));

        service.block(9, 2);

        verify(relationships).deletePair(2, 9);
    }

    private User user(long id, String name) {
        User user = new User(name + "@example.com", name, "User", 1);
        user.setId(id);
        return user;
    }
}
