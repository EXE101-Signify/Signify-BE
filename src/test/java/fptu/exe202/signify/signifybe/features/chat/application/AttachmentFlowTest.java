package fptu.exe202.signify.signifybe.features.chat.application;

import fptu.exe202.signify.signifybe.features.chat.application.port.out.*;
import fptu.exe202.signify.signifybe.features.chat.domain.*;
import fptu.exe202.signify.signifybe.features.chat.domain.exception.ConversationException;
import fptu.exe202.signify.signifybe.features.chat.infrastructure.persistence.JpaMessageAttachmentRepository;
import fptu.exe202.signify.signifybe.features.chat.infrastructure.persistence.JpaMessageRepository;
import fptu.exe202.signify.signifybe.features.storage.application.ImageProperties;
import fptu.exe202.signify.signifybe.features.storage.application.StorageService;
import fptu.exe202.signify.signifybe.features.user.application.BlockValidationService;
import fptu.exe202.signify.signifybe.features.storage.application.port.out.ObjectStorage;
import fptu.exe202.signify.signifybe.features.storage.domain.StorageException;
import fptu.exe202.signify.signifybe.features.storage.domain.StorageObject;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AttachmentFlowTest {
    private final ObjectStorage objectStorage = mock(ObjectStorage.class);
    private final StorageService storage = new StorageService(objectStorage, new ImageProperties(5_242_880));
    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final ConversationParticipantRepository participants = mock(ConversationParticipantRepository.class);
    private final MessageRepository messages = mock(MessageRepository.class);
    private final JpaMessageAttachmentRepository attachments = mock(JpaMessageAttachmentRepository.class);
    private final MessageService service = new MessageService(conversations, participants, messages,
            Clock.systemUTC(), attachments, storage, mock(BlockValidationService.class),
            mock(org.springframework.context.ApplicationEventPublisher.class));

    private MockMultipartFile png(String mime, String name, byte[] bytes) {
        return new MockMultipartFile("file", name, mime, bytes);
    }

    private byte[] pngBytes() {
        return new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0};
    }

    private void member() {
        when(conversations.findById(7)).thenReturn(Optional.of(new Conversation("PRIVATE", null, 1L, 1L)));
        when(participants.isParticipant(7, 1)).thenReturn(true);
        when(participants.findActiveByConversationId(7)).thenReturn(List.of(
                new ConversationParticipant(7L, 1L, 1L), new ConversationParticipant(7L, 2L, 1L)));
    }

    @Test void validFileUsesGeneratedKeyAndPersistsMetadata() {
        member();
        when(objectStorage.upload(anyString(), any(), eq("image/png"), eq(12L)))
                .thenAnswer(call -> new StorageObject(call.getArgument(0), "private-url", "image/png", 12));
        when(messages.save(any())).thenAnswer(call -> {
            Message message = call.getArgument(0);
            message.setId(31L);
            return message;
        });
        when(attachments.saveAndFlush(any())).thenAnswer(call -> {
            MessageAttachment attachment = call.getArgument(0);
            ReflectionTestUtils.setField(attachment, "id", 51L);
            return attachment;
        });
        AttachmentMessageResponse response = service.sendAttachment(7, 1, "hi", png("image/png", "photo.png", pngBytes()));
        assertEquals(31, response.messageId());
        assertEquals("photo.png", response.fileName());
        verify(objectStorage).upload(matches("chat-attachments/7/[0-9a-f-]+\\.png"), any(), eq("image/png"), eq(12L));
        verify(attachments).saveAndFlush(argThat(a -> a.getMessageId() == 31 &&
                a.getFileUrl().startsWith("chat-attachments/7/") && a.getFileSize() == 12));
    }

    @Test void oversizedFileIsRejectedBeforeStorage() {
        member();
        assertThrows(StorageException.class, () -> service.sendAttachment(7, 1, null,
                png("image/png", "large.png", new byte[5_242_881])));
        verifyNoInteractions(objectStorage);
    }

    @Test void invalidMimeAndSignatureAreRejected() {
        member();
        assertThrows(StorageException.class, () -> service.sendAttachment(7, 1, null,
                png("application/x-msdownload", "bad.exe", pngBytes())));
        assertThrows(StorageException.class, () -> service.sendAttachment(7, 1, null,
                png("image/png", "disguised.exe", pngBytes())));
        assertThrows(StorageException.class, () -> service.sendAttachment(7, 1, null,
                png("image/png", "fake.png", new byte[] {1, 2, 3})));
        verifyNoInteractions(objectStorage);
    }

    @Test void outsiderCannotUpload() {
        when(conversations.findById(7)).thenReturn(Optional.of(new Conversation("PRIVATE", null, 1L, 1L)));
        assertThrows(ConversationException.class, () -> service.sendAttachment(7, 3, null,
                png("image/png", "photo.png", pngBytes())));
        verifyNoInteractions(objectStorage);
    }

    @Test void storageFailureDoesNotWriteDatabase() {
        member();
        when(objectStorage.upload(anyString(), any(), anyString(), anyLong()))
                .thenThrow(StorageException.unavailable());
        assertThrows(StorageException.class, () -> service.sendAttachment(7, 1, null,
                png("image/png", "photo.png", pngBytes())));
        verifyNoInteractions(messages, attachments);
    }

    @Test void outsiderCannotReadAttachmentMetadataOrUrl() {
        JpaMessageRepository messageJpa = mock(JpaMessageRepository.class);
        ConversationMembershipService membership = mock(ConversationMembershipService.class);
        AttachmentService lookup = new AttachmentService(attachments, messageJpa, membership, storage);
        MessageAttachment attachment = new MessageAttachment(31, "chat-attachments/7/key.png", "photo.png",
                "image/png", 12, 1);
        when(attachments.findById(51L)).thenReturn(Optional.of(attachment));
        when(messageJpa.findById(31L)).thenReturn(Optional.of(new Message(7L, 1L, null, "FILE", 1)));
        doThrow(ConversationException.accessDenied()).when(membership).validateActiveMembership(7, 3);
        assertThrows(ConversationException.class, () -> lookup.get(51, 3));
        verifyNoInteractions(objectStorage);
    }

    @Test void databaseFailureDeletesUploadedObject() {
        member();
        when(objectStorage.upload(anyString(), any(), eq("image/png"), eq(12L)))
                .thenAnswer(call -> new StorageObject(call.getArgument(0), "private-url", "image/png", 12));
        when(messages.save(any())).thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(IllegalStateException.class, () -> service.sendAttachment(7, 1, null,
                png("image/png", "photo.png", pngBytes())));
        verify(objectStorage).delete(matches("chat-attachments/7/[0-9a-f-]+\\.png"));
    }
}
