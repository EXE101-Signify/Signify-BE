package fptu.exe202.signify.signifybe.features.chat.application;

import fptu.exe202.signify.signifybe.features.chat.domain.Message;
import fptu.exe202.signify.signifybe.features.chat.domain.MessageAttachment;
import fptu.exe202.signify.signifybe.features.chat.domain.exception.ConversationException;
import fptu.exe202.signify.signifybe.features.chat.infrastructure.persistence.JpaMessageAttachmentRepository;
import fptu.exe202.signify.signifybe.features.chat.infrastructure.persistence.JpaMessageRepository;
import fptu.exe202.signify.signifybe.features.storage.application.StorageService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AttachmentService {
    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    JpaMessageAttachmentRepository attachments;

    JpaMessageRepository messages;

    ConversationMembershipService membership;

    StorageService storage;

    @Transactional(readOnly = true)
    public AttachmentResponse get(long attachmentId, long userId) {
        MessageAttachment attachment = attachments.findById(attachmentId)
                .orElseThrow(ConversationException::attachmentNotFound);
        Message message = messages.findById(attachment.getMessageId())
                .orElseThrow(ConversationException::attachmentNotFound);
        membership.validateActiveMembership(message.getConversationId(), userId);
        if (message.isDeleted() || attachment.isDeleted()) throw ConversationException.attachmentNotFound();
        return new AttachmentResponse(attachment.getId(), message.getId(), attachment.getFileName(),
                attachment.getMimeType(), attachment.getFileSize(), attachment.getCreatedAt(),
                storage.generateAttachmentUrl(attachment.getFileUrl()));
    }

    @Transactional
    public void remove(long attachmentId, long userId) {
        MessageAttachment attachment = attachments.findById(attachmentId)
                .orElseThrow(ConversationException::attachmentNotFound);
        Message message = messages.findById(attachment.getMessageId())
                .orElseThrow(ConversationException::attachmentNotFound);
        membership.validateActiveMembership(message.getConversationId(), userId);
        if (message.isDeleted() || attachment.isDeleted()) throw ConversationException.attachmentNotFound();
        if (message.getSenderId() != userId) throw ConversationException.notMessageSender();

        attachment.markDeleted();
        attachments.saveAndFlush(attachment);
        String key = attachment.getFileUrl();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { cleanup(key); }
            });
        } else {
            cleanup(key);
        }
    }

    private void cleanup(String key) {
        try { storage.deleteAttachment(key); }
        catch (RuntimeException ex) { log.warn("Could not remove deleted chat attachment {}", key, ex); }
    }
}
