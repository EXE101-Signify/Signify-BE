package fptu.exe202.signify.signifybe.features.chat.infrastructure.persistence;

import fptu.exe202.signify.signifybe.features.chat.domain.MessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JpaMessageReactionRepository extends JpaRepository<MessageReaction, Long> {
    Optional<MessageReaction> findByMessageIdAndUserId(long messageId, long userId);
    List<MessageReaction> findByMessageIdOrderByIdAsc(long messageId);
}
