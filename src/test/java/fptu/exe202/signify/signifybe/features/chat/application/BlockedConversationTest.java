package fptu.exe202.signify.signifybe.features.chat.application;

import fptu.exe202.signify.signifybe.features.chat.api.dto.CreateConversationRequest;
import fptu.exe202.signify.signifybe.features.chat.api.dto.SendMessageRequest;
import fptu.exe202.signify.signifybe.features.chat.application.port.out.ConversationParticipantRepository;
import fptu.exe202.signify.signifybe.features.chat.application.port.out.ConversationRepository;
import fptu.exe202.signify.signifybe.features.chat.application.port.out.MessageRepository;
import fptu.exe202.signify.signifybe.features.chat.domain.Conversation;
import fptu.exe202.signify.signifybe.features.chat.domain.ConversationParticipant;
import fptu.exe202.signify.signifybe.features.chat.domain.Message;
import fptu.exe202.signify.signifybe.features.chat.infrastructure.persistence.JpaMessageAttachmentRepository;
import fptu.exe202.signify.signifybe.features.chat.infrastructure.persistence.JpaMessageRepository;
import fptu.exe202.signify.signifybe.features.storage.application.StorageService;
import fptu.exe202.signify.signifybe.features.user.application.BlockValidationService;
import fptu.exe202.signify.signifybe.features.user.application.port.out.UserRepository;
import fptu.exe202.signify.signifybe.features.user.domain.User;
import fptu.exe202.signify.signifybe.features.user.domain.exception.BlockException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlockedConversationTest {
    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final ConversationParticipantRepository participants = mock(ConversationParticipantRepository.class);
    private final MessageRepository messages = mock(MessageRepository.class);
    private final BlockValidationService blockValidation = mock(BlockValidationService.class);

    private void blockedPair() {
        doThrow(BlockException.interactionUnavailable()).when(blockValidation).assertCanInteract(1, 2);
    }

    private void privateConversation() {
        when(conversations.findById(7)).thenReturn(Optional.of(new Conversation("PRIVATE", null, 1L, 1L)));
        when(participants.isParticipant(7, 1)).thenReturn(true);
        when(participants.findActiveByConversationId(7)).thenReturn(List.of(
                new ConversationParticipant(7L, 1L, 1L),
                new ConversationParticipant(7L, 2L, 1L)));
    }

    @Test void cannotCreatePrivateConversationWhenEitherSideBlocks() {
        UserRepository users = mock(UserRepository.class);
        User first = new User("first@example.com", "First", "User", 1L);
        first.setId(1L);
        User second = new User("second@example.com", "Second", "User", 1L);
        second.setId(2L);
        when(users.findUserById(1)).thenReturn(Optional.of(first));
        when(users.findUserById(2)).thenReturn(Optional.of(second));
        blockedPair();
        ConversationService service = new ConversationService(conversations, participants,
                mock(ConversationMembershipService.class), users, blockValidation,
                Clock.systemUTC(), mock(EntityManager.class));

        assertThrows(BlockException.class, () -> service.createConversation(1,
                new CreateConversationRequest("PRIVATE", null, List.of(2L))));
        verify(conversations, never()).save(any());
        verify(participants, never()).findPrivateConversationBetween(anyLong(), anyLong());
    }

    @Test void cannotSendTextOrUploadAttachmentInOldConversation() {
        privateConversation();
        blockedPair();
        StorageService storage = mock(StorageService.class);
        MessageService service = new MessageService(conversations, participants, messages,
                Clock.systemUTC(), mock(JpaMessageAttachmentRepository.class), storage, blockValidation,
                mock(org.springframework.context.ApplicationEventPublisher.class));

        assertThrows(BlockException.class, () -> service.sendMessage(7, 1,
                new SendMessageRequest("hello", "TEXT")));
        assertThrows(BlockException.class, () -> service.sendAttachment(7, 1, null,
                new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1})));
        verify(messages, never()).save(any());
        verifyNoInteractions(storage);
    }

    @Test void activeParticipantsCanReadOldHistoryAfterBlock() {
        privateConversation();
        blockedPair();
        assertThrows(BlockException.class, () -> blockValidation.assertCanInteract(1, 2));
        JpaMessageRepository messageJpa = mock(JpaMessageRepository.class);
        Message oldMessage = new Message(7L, 1L, "before block", "TEXT", 1L);
        oldMessage.setId(31L);
        when(messageJpa.findByConversationIdAndDeletedFalseOrderByIdDesc(eq(7L), any(Pageable.class)))
                .thenReturn(List.of(oldMessage));
        ConversationMembershipService membership = mock(ConversationMembershipService.class);
        MessageHistoryService history = new MessageHistoryService(membership, conversations,
                messageJpa, mock(JpaMessageAttachmentRepository.class));

        var result = history.get(7, 1, null, 30);

        assertEquals(1, result.messages().size());
        assertEquals("before block", result.messages().get(0).content());
        verify(membership).validateActiveMembership(7, 1);
    }
}
