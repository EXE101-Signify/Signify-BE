package fptu.exe202.signify.signifybe.features.chat.infrastructure.persistence;

import fptu.exe202.signify.signifybe.features.chat.domain.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JpaConversationParticipantRepository extends JpaRepository<ConversationParticipant, Long> {

    boolean existsByConversationIdAndUserIdAndActiveTrue(long conversationId, long userId);

    List<ConversationParticipant> findByConversationIdAndActiveTrue(long conversationId);

    @Query("SELECT cp.conversationId FROM ConversationParticipant cp WHERE cp.userId = :userId AND cp.active = true")
    List<Long> findConversationIdsByUserId(@Param("userId") long userId);

    /**
     * Finds the conversation_id of an existing PRIVATE conversation between two users.
     * A PRIVATE conversation is one where both users are active participants
     * and the conversation has type 'PRIVATE'.
     */
    @Query("""
            SELECT cp1.conversationId FROM ConversationParticipant cp1
            JOIN ConversationParticipant cp2 ON cp1.conversationId = cp2.conversationId
            JOIN Conversation c ON c.id = cp1.conversationId
            WHERE cp1.userId = :userA AND cp1.active = true
              AND cp2.userId = :userB AND cp2.active = true
              AND c.type = 'PRIVATE'
            """)
    Optional<Long> findPrivateConversationBetween(@Param("userA") long userA, @Param("userB") long userB);
}
