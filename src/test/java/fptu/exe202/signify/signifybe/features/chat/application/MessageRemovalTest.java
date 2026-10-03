package fptu.exe202.signify.signifybe.features.chat.application;

import fptu.exe202.signify.signifybe.features.chat.application.port.out.*;
import fptu.exe202.signify.signifybe.features.chat.domain.*;
import fptu.exe202.signify.signifybe.features.chat.domain.exception.ConversationException;
import fptu.exe202.signify.signifybe.features.chat.infrastructure.persistence.JpaMessageAttachmentRepository;
import fptu.exe202.signify.signifybe.features.chat.infrastructure.persistence.JpaMessageRepository;
import fptu.exe202.signify.signifybe.features.storage.application.StorageService;
import fptu.exe202.signify.signifybe.features.user.application.BlockValidationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MessageRemovalTest {
    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final ConversationParticipantRepository participants = mock(ConversationParticipantRepository.class);
    private final MessageRepository messages = mock(MessageRepository.class);
    private final JpaMessageAttachmentRepository attachments = mock(JpaMessageAttachmentRepository.class);
    private final StorageService storage = mock(StorageService.class);
    private final MessageService service = new MessageService(conversations, participants, messages,
            Clock.systemUTC(), attachments, storage, mock(BlockValidationService.class),
            mock(org.springframework.context.ApplicationEventPublisher.class));

    private Message message() {
        Message message = new Message(7L, 1L, "hello", "TEXT", 1);
        message.setId(31L);
        return message;
    }

    private void member() {
        when(conversations.findById(7)).thenReturn(Optional.of(new Conversation("PRIVATE", null, 1L, 1)));
        when(participants.isParticipant(7, 1)).thenReturn(true);
    }

    @Test void senderCanRemoveMessageAndItsAttachment() {
        member();
        Message message = message();
        MessageAttachment attachment = new MessageAttachment(31, "chat-attachments/7/key.png", "photo.png",
                "image/png", 12, 1);
        when(messages.findByIdForUpdate(31)).thenReturn(Optional.of(message));
        when(attachments.findByMessageIdAndDeletedFalse(31)).thenReturn(List.of(attachment));

        service.removeMessage(7, 31, 1);

        assertTrue(message.isDeleted());
        assertTrue(attachment.isDeleted());
        verify(messages).save(message);
        verify(attachments).saveAllAndFlush(List.of(attachment));
        verify(storage).deleteAttachment(attachment.getFileUrl());
    }

    @Test void outsiderCannotRemoveMessage() {
        when(conversations.findById(7)).thenReturn(Optional.of(new Conversation("PRIVATE", null, 1L, 1)));
        assertThrows(ConversationException.class, () -> service.removeMessage(7, 31, 3));
        verifyNoInteractions(messages, attachments, storage);
    }

    @Test void otherParticipantCannotRemoveSenderMessage() {
        when(conversations.findById(7)).thenReturn(Optional.of(new Conversation("PRIVATE", null, 1L, 1)));
        when(participants.isParticipant(7, 2)).thenReturn(true);
        when(messages.findByIdForUpdate(31)).thenReturn(Optional.of(message()));
        assertThrows(ConversationException.class, () -> service.removeMessage(7, 31, 2));
        verify(messages, never()).save(any());
    }

    @Test void deletedOrWrongConversationMessageCannotBeRemoved() {
        member();
        Message deleted = message();
        deleted.setDeleted(true);
        when(messages.findByIdForUpdate(31)).thenReturn(Optional.of(deleted));
        assertThrows(ConversationException.class, () -> service.removeMessage(7, 31, 1));

        Message wrongConversation = new Message(8L, 1L, "hello", "TEXT", 1);
        wrongConversation.setId(31L);
        when(messages.findByIdForUpdate(31)).thenReturn(Optional.of(wrongConversation));
        assertThrows(ConversationException.class, () -> service.removeMessage(7, 31, 1));
        verify(messages, never()).save(any());
    }

    @Test void removedAttachmentCannotBeReadAndSenderCanRemoveIt() {
        JpaMessageRepository messageJpa = mock(JpaMessageRepository.class);
        ConversationMembershipService membership = mock(ConversationMembershipService.class);
        AttachmentService service = new AttachmentService(attachments, messageJpa, membership, storage);
        MessageAttachment attachment = new MessageAttachment(31, "chat-attachments/7/key.png", "photo.png",
                "image/png", 12, 1);
        ReflectionTestUtils.setField(attachment, "id", 51L);
        when(attachments.findById(51L)).thenReturn(Optional.of(attachment));
        when(messageJpa.findById(31L)).thenReturn(Optional.of(message()));

        service.remove(51, 1);

        assertTrue(attachment.isDeleted());
        verify(attachments).saveAndFlush(attachment);
        verify(storage).deleteAttachment(attachment.getFileUrl());
        assertThrows(ConversationException.class, () -> service.get(51, 1));
        verify(storage, never()).generateAttachmentUrl(any());
    }

    @Test void storageObjectIsDeletedOnlyAfterCommit() {
        member();
        MessageAttachment attachment = new MessageAttachment(31, "chat-attachments/7/key.png", "photo.png",
                "image/png", 12, 1);
        when(messages.findByIdForUpdate(31)).thenReturn(Optional.of(message()));
        when(attachments.findByMessageIdAndDeletedFalse(31)).thenReturn(List.of(attachment));
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.removeMessage(7, 31, 1);
            verify(storage, never()).deleteAttachment(any());
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
            verify(storage).deleteAttachment(attachment.getFileUrl());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
