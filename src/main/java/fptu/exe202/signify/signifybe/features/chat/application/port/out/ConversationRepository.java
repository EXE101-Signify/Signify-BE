package fptu.exe202.signify.signifybe.features.chat.application.port.out;

import fptu.exe202.signify.signifybe.features.chat.domain.Conversation;

import java.util.Optional;

public interface ConversationRepository {
    Conversation save(Conversation conversation);
    Optional<Conversation> findById(long conversationId);
}
