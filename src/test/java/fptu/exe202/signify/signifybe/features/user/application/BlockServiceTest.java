package fptu.exe202.signify.signifybe.features.user.application;

import fptu.exe202.signify.signifybe.features.user.application.port.out.UserRepository;
import fptu.exe202.signify.signifybe.features.user.domain.User;
import fptu.exe202.signify.signifybe.features.user.domain.UserBlock;
import fptu.exe202.signify.signifybe.features.user.domain.exception.BlockException;
import fptu.exe202.signify.signifybe.features.user.infrastructure.persistence.JpaUserBlockRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BlockServiceTest {
    private final JpaUserBlockRepository blocks = mock(JpaUserBlockRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final BlockService service = new BlockService(blocks, users,
            Clock.fixed(Instant.ofEpochMilli(123456L), ZoneOffset.UTC));

    private User user(long id) {
        User user = new User("user" + id + "@example.com", "User", "" + id, 1L);
        user.setId(id);
        return user;
    }

    private void usersExist() {
        when(users.lockUserById(1)).thenReturn(Optional.of(user(1)));
        when(users.findUserById(2)).thenReturn(Optional.of(user(2)));
    }

    @Test void blockCreatesOneActiveRow() {
        usersExist();
        when(blocks.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        var result = service.block(1, 2);

        assertEquals(2L, result.userId());
        assertEquals(123456L, result.blockedAt());
        verify(blocks).saveAndFlush(argThat(row -> row.getBlockerId() == 1
                && row.getBlockedId() == 2 && row.isActive()));
    }

    @Test void duplicateBlockIsIdempotent() {
        usersExist();
        UserBlock existing = new UserBlock(1, 2, 100L);
        when(blocks.findByBlockerIdAndBlockedId(1, 2)).thenReturn(Optional.of(existing));

        var result = service.block(1, 2);

        assertEquals(100L, result.blockedAt());
        verify(blocks, never()).saveAndFlush(any());
    }

    @Test void unblockSetsDeletedAtAndReblockReusesRow() {
        usersExist();
        UserBlock existing = new UserBlock(1, 2, 100L);
        when(blocks.findByBlockerIdAndBlockedId(1, 2)).thenReturn(Optional.of(existing));
        when(blocks.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        service.unblock(1, 2);
        assertEquals(123456L, existing.getDeletedAt().longValue());
        var result = service.block(1, 2);
        assertTrue(existing.isActive());
        assertEquals(123456L, result.blockedAt());
        verify(blocks, times(2)).saveAndFlush(existing);
    }

    @Test void repeatedUnblockDoesNotRewriteTimestamp() {
        usersExist();
        UserBlock existing = new UserBlock(1, 2, 100L);
        existing.unblock(200L);
        when(blocks.findByBlockerIdAndBlockedId(1, 2)).thenReturn(Optional.of(existing));

        service.unblock(1, 2);

        assertEquals(200L, existing.getDeletedAt().longValue());
        verify(blocks, never()).saveAndFlush(any());
    }

    @Test void selfBlockIsRejectedAndListContainsOnlyOwnActiveBlocks() {
        assertThrows(BlockException.class, () -> service.block(1, 1));
        verifyNoInteractions(blocks, users);

        UserBlock ownBlock = new UserBlock(1, 2, 100L);
        when(blocks.findByBlockerIdAndDeletedAtIsNullOrderByCreatedAtDesc(1)).thenReturn(List.of(ownBlock));
        when(users.findUserById(2)).thenReturn(Optional.of(user(2)));
        var result = service.listBlocked(1);
        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).userId());
        verify(blocks).findByBlockerIdAndDeletedAtIsNullOrderByCreatedAtDesc(1);
    }
}
