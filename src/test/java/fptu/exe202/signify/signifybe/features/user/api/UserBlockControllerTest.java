package fptu.exe202.signify.signifybe.features.user.api;

import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.Role;
import fptu.exe202.signify.signifybe.features.user.application.BlockService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class UserBlockControllerTest {
    @Test void usesAuthenticatedPrincipalForBlockerAndOwnList() {
        BlockService blocks = mock(BlockService.class);
        UserBlockController controller = new UserBlockController(blocks);
        CurrentUser current = new CurrentUser(1L, 10L, Role.USER);

        controller.block(current, 2L);
        controller.unblock(current, 2L);
        controller.listBlocked(current);

        verify(blocks).block(1L, 2L);
        verify(blocks).unblock(1L, 2L);
        verify(blocks).listBlocked(1L);
    }
}
