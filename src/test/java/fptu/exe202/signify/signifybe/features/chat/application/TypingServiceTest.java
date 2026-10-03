package fptu.exe202.signify.signifybe.features.chat.application;

import fptu.exe202.signify.signifybe.features.auth.domain.*;
import fptu.exe202.signify.signifybe.features.chat.api.TypingRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TypingServiceTest {
    private final PrivateChatAccessService access = mock(PrivateChatAccessService.class);
    private final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    private final TypingService typing = new TypingService(access, messaging,
            Clock.fixed(Instant.ofEpochMilli(1000), ZoneOffset.UTC));
    private final CurrentUser sender = new CurrentUser(1, 9, Role.USER);

    @Test void principalDeterminesSenderAndOnlyPeerReceivesTyping() {
        when(access.unblockedPeer(7, 1)).thenReturn(2L);
        typing.send(7, sender, new TypingRequest("TYPING_START"));
        ArgumentCaptor<ChatEvent> event = ArgumentCaptor.forClass(ChatEvent.class);
        verify(messaging).convertAndSendToUser(eq("2"), eq("/queue/conversations/7"), event.capture());
        assertEquals(1, event.getValue().senderId());
        assertEquals("TYPING_START", event.getValue().type());
        verifyNoMoreInteractions(messaging);
    }

    @Test void repeatedTypingIsRateLimitedAndBlockPolicyIsChecked() {
        when(access.unblockedPeer(7, 1)).thenReturn(2L);
        typing.send(7, sender, new TypingRequest("TYPING_START"));
        typing.send(7, sender, new TypingRequest("TYPING_START"));
        verify(messaging, times(1)).convertAndSendToUser(eq("2"), anyString(), any(ChatEvent.class));
        verify(access, times(2)).unblockedPeer(7, 1);
        assertThrows(IllegalArgumentException.class, () -> typing.send(7, sender, new TypingRequest("INVALID")));
    }
}
