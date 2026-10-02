package fptu.exe202.signify.signifybe.features.chat.application;

import fptu.exe202.signify.signifybe.features.chat.application.port.out.ConversationRepository;
import fptu.exe202.signify.signifybe.features.chat.domain.Conversation;
import fptu.exe202.signify.signifybe.features.chat.domain.Message;
import fptu.exe202.signify.signifybe.features.chat.domain.MessageAttachment;
import fptu.exe202.signify.signifybe.features.chat.domain.exception.ConversationException;
import fptu.exe202.signify.signifybe.features.chat.infrastructure.persistence.JpaMessageAttachmentRepository;
import fptu.exe202.signify.signifybe.features.chat.infrastructure.persistence.JpaMessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessageHistoryServiceTest {
    private final ConversationMembershipService membership = mock(ConversationMembershipService.class);
    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final JpaMessageRepository messages = mock(JpaMessageRepository.class);
    private final JpaMessageAttachmentRepository attachments = mock(JpaMessageAttachmentRepository.class);
    private final MessageHistoryService service = new MessageHistoryService(
            membership, conversations, messages, attachments);

    private Message message(long id) {
        Message message = new Message(7L, 1L, "message " + id, "TEXT", id);
        message.setId(id);
        return message;
    }

    private void privateConversation() {
        when(conversations.findById(7)).thenReturn(Optional.of(new Conversation("PRIVATE", null, 1L, 1)));
    }

    @Test void firstPageReturnsCursorAndOnlyVisibleAttachments() {
        privateConversation();
        when(messages.findByConversationIdAndDeletedFalseOrderByIdDesc(eq(7L), any(Pageable.class)))
                .thenReturn(List.of(message(13), message(12), message(11)));
        MessageAttachment file = new MessageAttachment(12, "chat-attachments/7/key.png",
                "photo.png", "image/png", 12, 1);
        ReflectionTestUtils.setField(file, "id", 9L);
        when(attachments.findByMessageIdInAndDeletedFalse(List.of(13L, 12L)))
                .thenReturn(List.of(file));

        MessageHistoryResponse page = service.get(7, 1, null, 2);

        assertEquals(List.of(13L, 12L), page.messages().stream()
                .map(MessageHistoryResponse.Item::messageId).toList());
        assertTrue(page.hasMore());
        assertEquals(12L, page.nextCursor());
        assertTrue(page.messages().get(0).attachments().isEmpty());
        assertEquals(9L, page.messages().get(1).attachments().get(0).attachmentId());
        verify(messages).findByConversationIdAndDeletedFalseOrderByIdDesc(eq(7L),
                argThat(pageable -> pageable.getPageSize() == 3));
    }

    @Test void nextPageUsesExclusiveCursor() {
        privateConversation();
        when(messages.findByConversationIdAndDeletedFalseAndIdLessThanOrderByIdDesc(
                eq(7L), eq(12L), any(Pageable.class))).thenReturn(List.of(message(11)));
        MessageHistoryResponse page = service.get(7, 1, 12L, 2);
        assertFalse(page.hasMore());
        assertNull(page.nextCursor());
        assertEquals(11L, page.messages().get(0).messageId());
    }

    @Test void invalidPageIsRejectedBeforeDatabaseAccess() {
        assertThrows(ConversationException.class, () -> service.get(7, 1, null, 101));
        assertThrows(ConversationException.class, () -> service.get(7, 1, 0L, 30));
        verifyNoInteractions(membership, conversations, messages, attachments);
    }

    @Test void outsiderCannotReadMessages() {
        doThrow(ConversationException.accessDenied()).when(membership).validateActiveMembership(7, 3);
        assertThrows(ConversationException.class, () -> service.get(7, 3, null, 30));
        verifyNoInteractions(messages, attachments);
    }
}
