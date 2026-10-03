package fptu.exe202.signify.signifybe.features.chat.application;

import fptu.exe202.signify.signifybe.features.chat.application.port.out.*;
import fptu.exe202.signify.signifybe.features.chat.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatRealtimePublisherTest {
    private final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final ConversationParticipantRepository participants = mock(ConversationParticipantRepository.class);
    private final ChatRealtimePublisher publisher = new ChatRealtimePublisher(messaging, conversations, participants);

    @Test void deliveryRunsAfterCommitAndTargetsOnlyTwoActiveUsers() throws Exception {
        assertEquals(TransactionPhase.AFTER_COMMIT, ChatRealtimePublisher.class
                .getMethod("afterCommit", ChatEvent.class)
                .getAnnotation(TransactionalEventListener.class).phase());
        when(conversations.findById(7)).thenReturn(Optional.of(new Conversation("PRIVATE", null, 1L, 1L)));
        when(participants.findActiveByConversationId(7)).thenReturn(List.of(member(7, 1), member(7, 2)));
        ChatEvent event = ChatEvent.created(7, 31, 1, "hello", "TEXT", 100, null);
        publisher.afterCommit(event);
        verify(messaging).convertAndSendToUser("1", "/queue/conversations/7", event);
        verify(messaging).convertAndSendToUser("2", "/queue/conversations/7", event);
        verifyNoMoreInteractions(messaging);
    }

    @Test void brokerFailureDoesNotEscapeOrPreventSecondDelivery() {
        when(conversations.findById(7)).thenReturn(Optional.of(new Conversation("PRIVATE", null, 1L, 1L)));
        when(participants.findActiveByConversationId(7)).thenReturn(List.of(member(7, 1), member(7, 2)));
        ChatEvent event = ChatEvent.deleted(7, 31, 1);
        doThrow(new IllegalStateException("broker unavailable")).when(messaging)
                .convertAndSendToUser("1", "/queue/conversations/7", event);
        assertDoesNotThrow(() -> publisher.afterCommit(event));
        verify(messaging).convertAndSendToUser("2", "/queue/conversations/7", event);
    }

    private ConversationParticipant member(long conversationId, long userId) {
        return new ConversationParticipant(conversationId, userId, 1);
    }
}
