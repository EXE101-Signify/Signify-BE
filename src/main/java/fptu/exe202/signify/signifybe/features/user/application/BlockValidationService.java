package fptu.exe202.signify.signifybe.features.user.application;

import fptu.exe202.signify.signifybe.features.user.domain.exception.BlockException;
import fptu.exe202.signify.signifybe.features.user.infrastructure.persistence.JpaUserBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BlockValidationService {
    private final JpaUserBlockRepository blocks;

    public void assertCanInteract(long userA, long userB) {
        if (blocks.existsByBlockerIdAndBlockedIdAndDeletedAtIsNull(userA, userB)
                || blocks.existsByBlockerIdAndBlockedIdAndDeletedAtIsNull(userB, userA)) {
            throw BlockException.interactionUnavailable();
        }
    }
}
