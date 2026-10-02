package fptu.exe202.signify.signifybe.features.chat.application;

import fptu.exe202.signify.signifybe.features.chat.api.dto.EditedMessageResponse;
import fptu.exe202.signify.signifybe.features.chat.application.port.out.ConversationParticipantRepository;
import fptu.exe202.signify.signifybe.features.chat.application.port.out.ConversationRepository;
import fptu.exe202.signify.signifybe.features.chat.application.port.out.MessageRepository;
import fptu.exe202.signify.signifybe.features.chat.domain.Conversation;
import fptu.exe202.signify.signifybe.features.chat.domain.Message;
import fptu.exe202.signify.signifybe.features.chat.domain.exception.ConversationException;
import fptu.exe202.signify.signifybe.features.chat.infrastructure.persistence.JpaMessageAttachmentRepository;
import fptu.exe202.signify.signifybe.features.storage.application.StorageService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MessageEditTest {
    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final ConversationParticipantRepository participants = mock(ConversationParticipantRepository.class);
    private final MessageRepository messages = mock(MessageRepository.class);
    private final MessageService service = new MessageService(conversations, participants, messages,
            Clock.fixed(Instant.ofEpochMilli(123456L), ZoneOffset.UTC),
            mock(JpaMessageAttachmentRepository.class), mock(StorageService.class));

    private Message message(String type) {
        Message message = new Message(7L, 1L, "original", type, 100L);
        message.setId(31L);
        return message;
    }

    private void member(long userId) {
        when(conversations.findById(7)).thenReturn(Optional.of(new Conversation("PRIVATE", null, 1L, 100L)));
        when(participants.isParticipant(7, userId)).thenReturn(true);
    }

    @Test void ownerEditsTextAndSetsEpochMilliseconds() {
        member(1);
        Message message = message("TEXT");
        when(messages.findByIdForUpdate(31)).thenReturn(Optional.of(message));
        when(messages.save(message)).thenReturn(message);

        EditedMessageResponse result = service.editMessage(7, 31, 1, "updated");

        assertEquals("updated", result.content());
        assertEquals(123456L, result.editedAt());
        assertEquals(123456L, message.getEditedAt());
        verify(messages).findByIdForUpdate(31);
        verify(messages).save(message);
    }

    @Test void otherParticipantCannotEdit() {
        member(2);
        when(messages.findByIdForUpdate(31)).thenReturn(Optional.of(message("TEXT")));
        assertThrows(ConversationException.class, () -> service.editMessage(7, 31, 2, "updated"));
        verify(messages, never()).save(any());
    }

    @Test void deletedMessageCannotBeEditedAfterLock() {
        member(1);
        Message deleted = message("TEXT");
        deleted.setDeleted(true);
        when(messages.findByIdForUpdate(31)).thenReturn(Optional.of(deleted));
        assertThrows(ConversationException.class, () -> service.editMessage(7, 31, 1, "updated"));
        verify(messages, never()).save(any());
    }

    @Test void outsiderCannotEdit() {
        when(conversations.findById(7)).thenReturn(Optional.of(new Conversation("PRIVATE", null, 1L, 100L)));
        assertThrows(ConversationException.class, () -> service.editMessage(7, 31, 3, "updated"));
        verifyNoInteractions(messages);
    }

    @Test void wrongConversationAndFileMessageCannotBeEdited() {
        member(1);
        Message wrongConversation = new Message(8L, 1L, "original", "TEXT", 100L);
        wrongConversation.setId(31L);
        when(messages.findByIdForUpdate(31)).thenReturn(Optional.of(wrongConversation));
        assertThrows(ConversationException.class, () -> service.editMessage(7, 31, 1, "updated"));

        when(messages.findByIdForUpdate(31)).thenReturn(Optional.of(message("FILE")));
        assertThrows(ConversationException.class, () -> service.editMessage(7, 31, 1, "updated"));
        verify(messages, never()).save(any());
    }

    @Test void blankContentIsRejected() {
        assertThrows(ConversationException.class, () -> service.editMessage(7, 31, 1, "  "));
        verifyNoInteractions(messages);
    }
}
