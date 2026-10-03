package fptu.exe202.signify.signifybe.features.user.application;

import fptu.exe202.signify.signifybe.features.user.api.dto.BlockedUserResponse;
import fptu.exe202.signify.signifybe.features.user.application.port.out.UserRepository;
import fptu.exe202.signify.signifybe.features.user.domain.User;
import fptu.exe202.signify.signifybe.features.user.domain.UserBlock;
import fptu.exe202.signify.signifybe.features.user.domain.exception.BlockException;
import fptu.exe202.signify.signifybe.features.user.infrastructure.persistence.JpaUserBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BlockService {
    private final JpaUserBlockRepository blocks;
    private final UserRepository users;
    private final Clock clock;

    @Transactional
    public BlockedUserResponse block(long blockerId, long blockedId) {
        if (blockerId == blockedId) throw BlockException.selfBlock();
        // Locking the blocker serializes first-time and repeated requests from this account.
        users.lockUserById(blockerId).orElseThrow(BlockException::userNotFound);
        User target = users.findUserById(blockedId)
                .filter(user -> !user.isDeleted())
                .orElseThrow(BlockException::userNotFound);
        var existing = blocks.findByBlockerIdAndBlockedId(blockerId, blockedId);
        if (existing.isPresent() && existing.get().isActive()) {
            return response(target, existing.get());
        }
        UserBlock block = existing.orElseGet(() -> new UserBlock(blockerId, blockedId, clock.millis()));
        if (existing.isPresent()) block.reblock(clock.millis());
        block = blocks.saveAndFlush(block);
        return response(target, block);
    }

    private BlockedUserResponse response(User target, UserBlock block) {
        return new BlockedUserResponse(target.getId(), target.getFullName(), target.getAvatar(), block.getCreatedAt());
    }

    @Transactional
    public void unblock(long blockerId, long blockedId) {
        if (blockerId == blockedId) throw BlockException.selfBlock();
        users.lockUserById(blockerId).orElseThrow(BlockException::userNotFound);
        blocks.findByBlockerIdAndBlockedId(blockerId, blockedId).filter(UserBlock::isActive)
                .ifPresent(block -> {
                    block.unblock(clock.millis());
                    blocks.saveAndFlush(block);
                });
    }

    @Transactional(readOnly = true)
    public List<BlockedUserResponse> listBlocked(long blockerId) {
        return blocks.findByBlockerIdAndDeletedAtIsNullOrderByCreatedAtDesc(blockerId).stream()
                .map(block -> {
                    User target = users.findUserById(block.getBlockedId()).orElse(null);
                    return new BlockedUserResponse(block.getBlockedId(),
                            target == null ? null : target.getFullName(),
                            target == null ? null : target.getAvatar(), block.getCreatedAt());
                })
                .toList();
    }
}
