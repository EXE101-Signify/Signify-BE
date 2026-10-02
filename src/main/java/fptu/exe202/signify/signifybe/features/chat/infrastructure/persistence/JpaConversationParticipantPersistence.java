package fptu.exe202.signify.signifybe.features.chat.infrastructure.persistence;

import fptu.exe202.signify.signifybe.features.chat.application.port.out.ConversationParticipantRepository;
import fptu.exe202.signify.signifybe.features.chat.domain.ConversationParticipant;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JpaConversationParticipantPersistence implements ConversationParticipantRepository {
    JpaConversationParticipantRepository jpaRepository;

    @Override
    public ConversationParticipant save(ConversationParticipant participant) {
        return jpaRepository.saveAndFlush(participant);
    }

    @Override
    public List<ConversationParticipant> saveAll(List<ConversationParticipant> participants) {
        return jpaRepository.saveAllAndFlush(participants);
    }

    @Override
    public boolean isParticipant(long conversationId, long userId) {
        return jpaRepository.existsByConversationIdAndUserIdAndActiveTrue(conversationId, userId);
    }

    @Override
    public List<ConversationParticipant> findActiveByConversationId(long conversationId) {
        return jpaRepository.findByConversationIdAndActiveTrue(conversationId);
    }

    @Override
    public List<Long> findConversationIdsByUserId(long userId) {
        return jpaRepository.findConversationIdsByUserId(userId);
    }

    @Override
    public Optional<Long> findPrivateConversationBetween(long userIdA, long userIdB) {
        return jpaRepository.findPrivateConversationBetween(userIdA, userIdB);
    }
}
