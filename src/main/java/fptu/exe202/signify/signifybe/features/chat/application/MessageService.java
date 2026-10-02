package fptu.exe202.signify.signifybe.features.chat.application;

import fptu.exe202.signify.signifybe.features.chat.api.dto.MessageResponse;
import fptu.exe202.signify.signifybe.features.chat.api.dto.EditedMessageResponse;
import fptu.exe202.signify.signifybe.features.chat.api.dto.SendMessageRequest;
import fptu.exe202.signify.signifybe.features.chat.application.port.out.ConversationParticipantRepository;
import fptu.exe202.signify.signifybe.features.chat.application.port.out.ConversationRepository;
import fptu.exe202.signify.signifybe.features.chat.application.port.out.MessageRepository;
import fptu.exe202.signify.signifybe.features.chat.domain.Conversation;
import fptu.exe202.signify.signifybe.features.chat.domain.ConversationType;
import fptu.exe202.signify.signifybe.features.chat.domain.Message;
import fptu.exe202.signify.signifybe.features.chat.domain.MessageAttachment;
import fptu.exe202.signify.signifybe.features.chat.infrastructure.persistence.JpaMessageAttachmentRepository;
import fptu.exe202.signify.signifybe.features.storage.application.StorageService;
import fptu.exe202.signify.signifybe.features.storage.domain.StorageObject;
import fptu.exe202.signify.signifybe.features.chat.domain.exception.ConversationException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MessageService {
    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    ConversationRepository conversationRepository;
    ConversationParticipantRepository participantRepository;
    MessageRepository messageRepository;
    Clock clock;
    JpaMessageAttachmentRepository attachmentRepository;
    StorageService storageService;

    @Transactional
    public MessageResponse sendMessage(long conversationId, long senderId, SendMessageRequest request) {
        Conversation conversation = validateOneToOne(conversationId, senderId);
        validateContent(request.content(), request.messageType());

        long now = clock.millis();
        Message message = new Message(conversationId, senderId, request.content(), "TEXT", now);

        Message saved = messageRepository.save(message);
        conversation.setUpdatedAt(now);
        conversationRepository.save(conversation);

        return new MessageResponse(saved.getId(), saved.getConversationId(), saved.getSenderId(),
                saved.getContent(), saved.getMessageType(), saved.getCreatedAt());
    }

    @Transactional
    public AttachmentMessageResponse sendAttachment(long conversationId, long senderId,
                                                     String content, MultipartFile file) {
        Conversation conversation = validateOneToOne(conversationId, senderId);
        if (content != null && content.length() > 5000) {
            throw ConversationException.invalidMessage("Message content must not exceed 5000 characters");
        }
        StorageObject object = storageService.uploadChatAttachment(conversationId, file);
        // A flush failure is handled below; a later commit failure triggers this rollback hook.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) cleanup(object.key());
                }
            });
        }
        try {
            long now = clock.millis();
            Message message = messageRepository.save(new Message(conversationId, senderId,
                    content == null ? null : content.strip(), "FILE", now));
            MessageAttachment attachment = attachmentRepository.saveAndFlush(new MessageAttachment(
                    message.getId(), object.key(), file.getOriginalFilename(), object.contentType(), object.size(), now));
            conversation.setUpdatedAt(now);
            conversationRepository.save(conversation);
            return new AttachmentMessageResponse(message.getId(), attachment.getId(), conversationId,
                    senderId, message.getContent(), attachment.getFileName(), attachment.getMimeType(),
                    attachment.getFileSize(), now);
        } catch (RuntimeException ex) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) cleanup(object.key());
            throw ex;
        }
    }

    @Transactional
    public EditedMessageResponse editMessage(long conversationId, long messageId, long userId, String content) {
        validateContent(content, "TEXT");
        Message message = lockOwnedMessage(conversationId, messageId, userId);
        if (!"TEXT".equalsIgnoreCase(message.getMessageType())) {
            throw ConversationException.messageNotEditable();
        }
        message.setContent(content);
        message.setEditedAt(clock.millis());
        Message saved = messageRepository.save(message);
        // Publication boundary for a future MESSAGE_UPDATED event: persist first.
        return new EditedMessageResponse(saved.getId(), saved.getConversationId(), saved.getSenderId(),
                saved.getContent(), saved.getMessageType(), saved.getCreatedAt(), saved.getEditedAt());
    }

    @Transactional
    public void removeMessage(long conversationId, long messageId, long userId) {
        Message message = lockOwnedMessage(conversationId, messageId, userId);
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(ConversationException::notFound);

        message.setDeleted(true);
        messageRepository.save(message);
        List<MessageAttachment> files = attachmentRepository.findByMessageIdAndDeletedFalse(messageId);
        files.forEach(MessageAttachment::markDeleted);
        attachmentRepository.saveAllAndFlush(files);
        conversation.setUpdatedAt(clock.millis());
        conversationRepository.save(conversation);
        files.forEach(file -> deleteObjectAfterCommit(file.getFileUrl()));
        // Publication boundary for a future MESSAGE_DELETED event: persist first.
    }

    private Message lockOwnedMessage(long conversationId, long messageId, long userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(ConversationException::notFound);
        if (!participantRepository.isParticipant(conversationId, userId)) {
            throw ConversationException.accessDenied();
        }
        if (!ConversationType.PRIVATE.name().equalsIgnoreCase(conversation.getType())) {
            throw ConversationException.invalidOneToOneConversation();
        }
        Message message = messageRepository.findByIdForUpdate(messageId)
                .filter(m -> m.getConversationId() == conversationId && !m.isDeleted())
                .orElseThrow(ConversationException::messageNotFound);
        if (message.getSenderId() != userId) throw ConversationException.notMessageSender();
        return message;
    }

    private void deleteObjectAfterCommit(String key) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { cleanup(key); }
            });
        } else {
            cleanup(key);
        }
    }

    private void cleanup(String key) {
        try { storageService.deleteAttachment(key); }
        catch (RuntimeException ex) { log.warn("Could not remove orphan chat attachment {}", key, ex); }
    }

    private Conversation validateOneToOne(long conversationId, long senderId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(ConversationException::notFound);

        if (!participantRepository.isParticipant(conversationId, senderId)) {
            throw ConversationException.accessDenied();
        }

        if (!ConversationType.PRIVATE.name().equalsIgnoreCase(conversation.getType())
                || participantRepository.findActiveByConversationId(conversationId).size() != 2) {
            throw ConversationException.invalidOneToOneConversation();
        }

        return conversation;
    }

    private void validateContent(String content, String messageType) {
        if (content == null || content.isBlank()) {
            throw ConversationException.invalidMessage("Message content must not be blank");
        }
        if (content.length() > 5000) {
            throw ConversationException.invalidMessage("Message content must not exceed 5000 characters");
        }
        if (!"TEXT".equalsIgnoreCase(messageType)) {
            throw ConversationException.invalidMessage("Only TEXT messages are supported");
        }
    }
}
