package fptu.exe202.signify.signifybe.features.chat.infrastructure.persistence;

import fptu.exe202.signify.signifybe.features.chat.application.port.out.ConversationRepository;
import fptu.exe202.signify.signifybe.features.chat.domain.Conversation;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JpaConversationPersistence implements ConversationRepository {
    JpaConversationRepository jpaConversationRepository;

    @Override
    public Conversation save(Conversation conversation) {
        return jpaConversationRepository.saveAndFlush(conversation);
    }

    @Override
    public Optional<Conversation> findById(long conversationId) {
        return jpaConversationRepository.findById(conversationId);
    }
}
