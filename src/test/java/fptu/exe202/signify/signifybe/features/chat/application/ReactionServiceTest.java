package fptu.exe202.signify.signifybe.features.chat.application;

import fptu.exe202.signify.signifybe.features.chat.application.port.out.MessageRepository;
import fptu.exe202.signify.signifybe.features.chat.domain.Message;
import fptu.exe202.signify.signifybe.features.chat.domain.MessageReaction;
import fptu.exe202.signify.signifybe.features.chat.domain.exception.ConversationException;
import fptu.exe202.signify.signifybe.features.chat.infrastructure.persistence.JpaMessageReactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReactionServiceTest {
    private final ConversationMembershipService membership = mock(ConversationMembershipService.class);
    private final MessageRepository messages = mock(MessageRepository.class);
    private final JpaMessageReactionRepository reactions = mock(JpaMessageReactionRepository.class);
    private final ReactionService service = new ReactionService(membership, messages, reactions,
            Clock.fixed(Instant.ofEpochMilli(123456L), ZoneOffset.UTC));

    private void activeMessage() {
        Message message = new Message(7L, 1L, "hello", "TEXT", 1L);
        message.setId(31L);
        when(messages.findByIdForUpdate(31)).thenReturn(Optional.of(message));
        when(messages.findById(31)).thenReturn(Optional.of(message));
    }

    private MessageReaction reaction(String type) {
        MessageReaction reaction = new MessageReaction(31, 2, type, 100L);
        ReflectionTestUtils.setField(reaction, "id", 51L);
        return reaction;
    }

    @Test void addsReactionAndReturnsCounts() {
        activeMessage();
        AtomicReference<MessageReaction> saved = new AtomicReference<>();
        when(reactions.saveAndFlush(any())).thenAnswer(call -> {
            MessageReaction row = call.getArgument(0);
            ReflectionTestUtils.setField(row, "id", 51L);
            saved.set(row);
            return row;
        });
        when(reactions.findByMessageIdOrderByIdAsc(31)).thenAnswer(call -> List.of(saved.get()));

        ReactionSummaryResponse result = service.addOrChange(7, 31, 2, "like");

        assertEquals(1L, result.counts().get("LIKE").longValue());
        assertEquals(2L, result.reactions().get(0).userId());
        assertEquals(123456L, saved.get().getCreatedAt().longValue());
        InOrder order = inOrder(messages, reactions);
        order.verify(messages).findByIdForUpdate(31);
        order.verify(reactions).findByMessageIdAndUserId(31, 2);
        order.verify(reactions).saveAndFlush(any());
    }

    @Test void duplicateRequestKeepsOneUnchangedRow() {
        activeMessage();
        MessageReaction existing = reaction("LIKE");
        when(reactions.findByMessageIdAndUserId(31, 2)).thenReturn(Optional.of(existing));
        when(reactions.findByMessageIdOrderByIdAsc(31)).thenReturn(List.of(existing));

        ReactionSummaryResponse result = service.addOrChange(7, 31, 2, "LIKE");

        assertEquals(1, result.reactions().size());
        assertEquals(1L, result.counts().get("LIKE").longValue());
        assertNull(existing.getUpdatedAt());
        verify(reactions, never()).saveAndFlush(any());
    }

    @Test void listsUsersAndCountsEachReactionType() {
        activeMessage();
        MessageReaction first = reaction("LIKE");
        MessageReaction second = new MessageReaction(31, 3, "LIKE", 101L);
        ReflectionTestUtils.setField(second, "id", 52L);
        MessageReaction third = new MessageReaction(31, 4, "SAD", 102L);
        ReflectionTestUtils.setField(third, "id", 53L);
        when(reactions.findByMessageIdOrderByIdAsc(31)).thenReturn(List.of(first, second, third));

        ReactionSummaryResponse result = service.get(7, 31, 2);

        assertEquals(3, result.reactions().size());
        assertEquals(2L, result.counts().get("LIKE").longValue());
        assertEquals(1L, result.counts().get("SAD").longValue());
        verify(messages).findById(31);
    }

    @Test void changesExistingReactionAndUpdatedAt() {
        activeMessage();
        MessageReaction existing = reaction("LIKE");
        when(reactions.findByMessageIdAndUserId(31, 2)).thenReturn(Optional.of(existing));
        when(reactions.findByMessageIdOrderByIdAsc(31)).thenReturn(List.of(existing));

        ReactionSummaryResponse result = service.addOrChange(7, 31, 2, "LOVE");

        assertEquals("LOVE", existing.getReaction());
        assertEquals(123456L, existing.getUpdatedAt().longValue());
        assertEquals(1L, result.counts().get("LOVE").longValue());
        assertFalse(result.counts().containsKey("LIKE"));
        verify(reactions).saveAndFlush(existing);
    }

    @Test void removesOnlyCurrentUsersMatchingReaction() {
        activeMessage();
        MessageReaction existing = reaction("LOVE");
        when(reactions.findByMessageIdAndUserId(31, 2)).thenReturn(Optional.of(existing));
        when(reactions.findByMessageIdOrderByIdAsc(31)).thenReturn(List.of());

        ReactionSummaryResponse result = service.remove(7, 31, 2, "LOVE");

        assertTrue(result.reactions().isEmpty());
        assertTrue(result.counts().isEmpty());
        verify(reactions).delete(existing);
        verify(reactions).flush();
        assertThrows(ConversationException.class, () -> service.remove(7, 31, 2, "LIKE"));
    }

    @Test void outsiderAndWrongConversationCannotAccessReactions() {
        doThrow(ConversationException.accessDenied()).when(membership).validateActiveMembership(7, 3);
        assertThrows(ConversationException.class, () -> service.addOrChange(7, 31, 3, "LIKE"));
        verifyNoInteractions(messages, reactions);

        Message wrongConversation = new Message(8L, 1L, "hello", "TEXT", 1L);
        wrongConversation.setId(31L);
        when(messages.findById(31)).thenReturn(Optional.of(wrongConversation));
        assertThrows(ConversationException.class, () -> service.get(7, 31, 2));
        verifyNoInteractions(reactions);
    }

    @Test void deletedMessageAndUnsupportedReactionAreRejected() {
        activeMessage();
        Message deleted = messages.findByIdForUpdate(31).orElseThrow();
        deleted.setDeleted(true);
        assertThrows(ConversationException.class, () -> service.addOrChange(7, 31, 2, "LIKE"));
        assertThrows(ConversationException.class, () -> service.addOrChange(7, 31, 2, "CUSTOM"));
        verifyNoInteractions(reactions);
    }
}
