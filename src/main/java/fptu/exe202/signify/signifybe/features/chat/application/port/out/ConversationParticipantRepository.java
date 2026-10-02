package fptu.exe202.signify.signifybe.features.chat.application.port.out;

import fptu.exe202.signify.signifybe.features.chat.domain.ConversationParticipant;

import java.util.List;
import java.util.Optional;

public interface ConversationParticipantRepository {
    ConversationParticipant save(ConversationParticipant participant);
    List<ConversationParticipant> saveAll(List<ConversationParticipant> participants);
    boolean isParticipant(long conversationId, long userId);
    List<ConversationParticipant> findActiveByConversationId(long conversationId);
    List<Long> findConversationIdsByUserId(long userId);

    /**
     * Finds an existing PRIVATE conversation between exactly these two users.
     * Returns empty if no such conversation exists.
     */
    Optional<Long> findPrivateConversationBetween(long userIdA, long userIdB);
}
