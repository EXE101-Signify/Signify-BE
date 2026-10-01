package fptu.exe202.signify.signifybe.features.chat.application;

import fptu.exe202.signify.signifybe.features.chat.api.dto.MessageResponse;
import fptu.exe202.signify.signifybe.features.chat.api.dto.SendMessageRequest;
import fptu.exe202.signify.signifybe.features.chat.application.port.out.ConversationParticipantRepository;
import fptu.exe202.signify.signifybe.features.chat.application.port.out.ConversationRepository;
import fptu.exe202.signify.signifybe.features.chat.application.port.out.MessageRepository;
import fptu.exe202.signify.signifybe.features.chat.domain.Conversation;
import fptu.exe202.signify.signifybe.features.chat.domain.ConversationType;
import fptu.exe202.signify.signifybe.features.chat.domain.Message;
import fptu.exe202.signify.signifybe.features.chat.domain.exception.ConversationException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MessageService {
    ConversationRepository conversationRepository;
    ConversationParticipantRepository participantRepository;
    MessageRepository messageRepository;
    Clock clock;

    @Transactional
    public MessageResponse sendMessage(long conversationId, long senderId, SendMessageRequest request) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(ConversationException::notFound);

        if (!participantRepository.isParticipant(conversationId, senderId)) {
            throw ConversationException.accessDenied();
        }

        if (!ConversationType.PRIVATE.name().equalsIgnoreCase(conversation.getType())
                || participantRepository.findActiveByConversationId(conversationId).size() != 2) {
            throw ConversationException.invalidOneToOneConversation();
        }

        if (request.content() == null || request.content().isBlank()) {
            throw ConversationException.invalidMessage("Message content must not be blank");
        }
        if (request.content().length() > 5000) {
            throw ConversationException.invalidMessage("Message content must not exceed 5000 characters");
        }
        if (!"TEXT".equalsIgnoreCase(request.messageType())) {
            throw ConversationException.invalidMessage("Only TEXT messages are supported");
        }

        long now = clock.millis();
        Message message = new Message(conversationId, senderId, request.content(), "TEXT", now);

        Message saved = messageRepository.save(message);
        conversation.setUpdatedAt(now);
        conversationRepository.save(conversation);

        // Keep this return point as the future realtime publication boundary; persist first.
        return new MessageResponse(saved.getId(), saved.getConversationId(), saved.getSenderId(),
                saved.getContent(), saved.getMessageType(), saved.getCreatedAt());
    }
}
