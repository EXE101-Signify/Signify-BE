package fptu.exe202.signify.signifybe.features.user.application;

import fptu.exe202.signify.signifybe.features.user.domain.exception.BlockException;
import fptu.exe202.signify.signifybe.features.user.infrastructure.persistence.JpaUserBlockRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlockValidationServiceTest {
    private final JpaUserBlockRepository blocks = mock(JpaUserBlockRepository.class);
    private final BlockValidationService service = new BlockValidationService(blocks);

    @Test void blocksEitherDirectionWithoutIdentifyingBlocker() {
        when(blocks.existsByBlockerIdAndBlockedIdAndDeletedAtIsNull(1, 2)).thenReturn(true);
        BlockException first = assertThrows(BlockException.class, () -> service.assertCanInteract(1, 2));
        when(blocks.existsByBlockerIdAndBlockedIdAndDeletedAtIsNull(1, 2)).thenReturn(false);
        when(blocks.existsByBlockerIdAndBlockedIdAndDeletedAtIsNull(2, 1)).thenReturn(true);
        BlockException second = assertThrows(BlockException.class, () -> service.assertCanInteract(1, 2));
        assertEquals(first.getMessage(), second.getMessage());
        assertFalse(first.getMessage().contains("1"));
        assertFalse(first.getMessage().contains("2"));
    }

    @Test void allowsInteractionAfterUnblock() {
        assertDoesNotThrow(() -> service.assertCanInteract(1, 2));
    }
}
