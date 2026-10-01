package fptu.exe202.signify.signifybe.features.chat.application;

import fptu.exe202.signify.signifybe.features.chat.api.dto.SendMessageRequest;
import fptu.exe202.signify.signifybe.features.chat.application.port.out.ConversationParticipantRepository;
import fptu.exe202.signify.signifybe.features.chat.application.port.out.ConversationRepository;
import fptu.exe202.signify.signifybe.features.chat.application.port.out.MessageRepository;
import fptu.exe202.signify.signifybe.features.chat.domain.Conversation;
import fptu.exe202.signify.signifybe.features.chat.domain.ConversationParticipant;
import fptu.exe202.signify.signifybe.features.chat.domain.Message;
import fptu.exe202.signify.signifybe.features.chat.domain.exception.ConversationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {
    private static final long CONVERSATION_ID = 40L;
    private static final long SENDER_ID = 10L;
    private static final long NOW = 1_695_900_000_123L;

    @Mock ConversationRepository conversationRepository;
    @Mock ConversationParticipantRepository participantRepository;
    @Mock MessageRepository messageRepository;
    @Mock Clock clock;
    @InjectMocks MessageService service;

    private Conversation conversation;

    @BeforeEach
    void setUp() {
        conversation = new Conversation("PRIVATE", null, SENDER_ID, 1L);
        lenient().when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
        lenient().when(participantRepository.isParticipant(CONVERSATION_ID, SENDER_ID)).thenReturn(true);
        lenient().when(participantRepository.findActiveByConversationId(CONVERSATION_ID)).thenReturn(List.of(
                new ConversationParticipant(CONVERSATION_ID, SENDER_ID, 1L),
                new ConversationParticipant(CONVERSATION_ID, 20L, 1L)));
        lenient().when(clock.millis()).thenReturn(NOW);
        lenient().when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId(99L);
            return message;
        });
    }

    @Test
    void sendsTextAndUpdatesConversationTimestamp() {
        var response = service.sendMessage(CONVERSATION_ID, SENDER_ID,
                new SendMessageRequest("Hello", "TEXT"));

        assertEquals(99L, response.messageId());
        assertEquals(CONVERSATION_ID, response.conversationId());
        assertEquals(SENDER_ID, response.senderId());
        assertEquals("Hello", response.content());
        assertEquals("TEXT", response.messageType());
        assertEquals(NOW, response.createdAt());
        assertEquals(NOW, conversation.getUpdatedAt());
        verify(messageRepository).save(any(Message.class));
        verify(conversationRepository).save(conversation);
    }

    @Test
    void rejectsNonParticipantAndInactiveParticipant() {
        when(participantRepository.isParticipant(CONVERSATION_ID, SENDER_ID)).thenReturn(false);
        assertEquals(403, assertThrows(ConversationException.class, () -> service.sendMessage(
                CONVERSATION_ID, SENDER_ID, new SendMessageRequest("Hello", "TEXT"))).getStatus().value());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void rejectsMissingConversation() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ConversationException.class, () -> service.sendMessage(
                CONVERSATION_ID, SENDER_ID, new SendMessageRequest("Hello", "TEXT"))).getStatus().value());
        verify(participantRepository, never()).isParticipant(anyLong(), anyLong());
    }

    @Test
    void rejectsBlankAndTooLongContent() {
        assertThrows(ConversationException.class, () -> service.sendMessage(
                CONVERSATION_ID, SENDER_ID, new SendMessageRequest("  ", "TEXT")));
        assertThrows(ConversationException.class, () -> service.sendMessage(
                CONVERSATION_ID, SENDER_ID, new SendMessageRequest("x".repeat(5001), "TEXT")));
        verify(messageRepository, never()).save(any());
    }

    @Test
    void rejectsConversationThatIsNotExactlyOneToOne() {
        when(participantRepository.findActiveByConversationId(CONVERSATION_ID)).thenReturn(List.of(
                new ConversationParticipant(CONVERSATION_ID, SENDER_ID, 1L)));
        assertThrows(ConversationException.class, () -> service.sendMessage(
                CONVERSATION_ID, SENDER_ID, new SendMessageRequest("Hello", "TEXT")));
        verify(messageRepository, never()).save(any());
    }

    @Test
    void requestHasNoSenderIdentityFieldAndAuthenticatedSenderIsPersisted() throws Exception {
        assertFalse(java.util.Arrays.stream(SendMessageRequest.class.getRecordComponents())
                .anyMatch(component -> component.getName().equalsIgnoreCase("senderId")));

        SendMessageRequest request = new ObjectMapper().readValue(
                "{\"content\":\"Hello\",\"messageType\":\"TEXT\",\"senderId\":999}",
                SendMessageRequest.class);
        service.sendMessage(CONVERSATION_ID, SENDER_ID, request);
        verify(messageRepository).save(argThat(message -> message.getSenderId() == SENDER_ID));
    }
}
