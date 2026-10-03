package fptu.exe202.signify.signifybe.features.chat.application;

import fptu.exe202.signify.signifybe.features.chat.api.dto.SendMessageRequest;
import fptu.exe202.signify.signifybe.features.chat.application.port.out.*;
import fptu.exe202.signify.signifybe.features.chat.domain.*;
import fptu.exe202.signify.signifybe.features.chat.infrastructure.persistence.JpaMessageAttachmentRepository;
import fptu.exe202.signify.signifybe.features.storage.application.StorageService;
import fptu.exe202.signify.signifybe.features.user.application.BlockValidationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessageRealtimeEventTest {
    @Test void restWritesPublishCreatedUpdatedAndDeletedEventsFromSavedState() {
        ConversationRepository conversations = mock(ConversationRepository.class);
        ConversationParticipantRepository participants = mock(ConversationParticipantRepository.class);
        MessageRepository messages = mock(MessageRepository.class);
        JpaMessageAttachmentRepository attachments = mock(JpaMessageAttachmentRepository.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        MessageService service = new MessageService(conversations, participants, messages,
                Clock.fixed(Instant.ofEpochMilli(1000), ZoneOffset.UTC), attachments,
                mock(StorageService.class), mock(BlockValidationService.class), events);
        when(conversations.findById(7)).thenReturn(Optional.of(new Conversation("PRIVATE", null, 1L, 1L)));
        when(participants.isParticipant(7, 1)).thenReturn(true);
        when(participants.findActiveByConversationId(7)).thenReturn(List.of(
                new ConversationParticipant(7L, 1L, 1), new ConversationParticipant(7L, 2L, 1)));
        when(messages.save(any(Message.class))).thenAnswer(call -> {
            Message message = call.getArgument(0);
            message.setId(31L);
            return message;
        });
        when(attachments.findByMessageIdAndDeletedFalse(31)).thenReturn(List.of());

        var created = service.sendMessage(7, 1, new SendMessageRequest("hello", "TEXT"));
        assertEquals(31, created.messageId());
        Message saved = new Message(7L, 1L, "hello", "TEXT", 1000);
        saved.setId(31L);
        when(messages.findByIdForUpdate(31)).thenReturn(Optional.of(saved));
        service.editMessage(7, 31, 1, "edited");
        service.removeMessage(7, 31, 1);

        ArgumentCaptor<ChatEvent> published = ArgumentCaptor.forClass(ChatEvent.class);
        verify(events, times(3)).publishEvent(published.capture());
        assertEquals(List.of("MESSAGE_CREATED", "MESSAGE_UPDATED", "MESSAGE_DELETED"),
                published.getAllValues().stream().map(ChatEvent::type).toList());
        assertEquals("edited", published.getAllValues().get(1).content());
        assertNull(published.getAllValues().get(2).content());
        assertEquals("MESSAGE_CREATED:31", published.getAllValues().get(0).eventId());
    }
}
