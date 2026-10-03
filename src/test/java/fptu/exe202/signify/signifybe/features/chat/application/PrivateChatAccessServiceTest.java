package fptu.exe202.signify.signifybe.features.chat.application;

import fptu.exe202.signify.signifybe.features.chat.application.port.out.*;
import fptu.exe202.signify.signifybe.features.chat.domain.*;
import fptu.exe202.signify.signifybe.features.chat.domain.exception.ConversationException;
import fptu.exe202.signify.signifybe.features.user.application.BlockValidationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrivateChatAccessServiceTest {
    private final ConversationMembershipService membership = mock(ConversationMembershipService.class);
    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final ConversationParticipantRepository participants = mock(ConversationParticipantRepository.class);
    private final BlockValidationService blocks = mock(BlockValidationService.class);
    private final PrivateChatAccessService access = new PrivateChatAccessService(
            membership, conversations, participants, blocks);

    @Test void typingChecksMembershipAndBlockForTheTwoParticipants() {
        when(conversations.findById(7)).thenReturn(Optional.of(new Conversation("PRIVATE", null, 1L, 1)));
        when(participants.findActiveByConversationId(7)).thenReturn(List.of(
                new ConversationParticipant(7L, 1L, 1), new ConversationParticipant(7L, 2L, 1)));
        assertEquals(2, access.unblockedPeer(7, 1));
        verify(membership).validateActiveMembership(7, 1);
        verify(blocks).assertCanInteract(1, 2);
        doThrow(ConversationException.accessDenied()).when(membership).validateActiveMembership(7, 3);
        assertThrows(ConversationException.class, () -> access.unblockedPeer(7, 3));
        verify(blocks, never()).assertCanInteract(3, 1);
        verify(blocks, never()).assertCanInteract(3, 2);
    }
}
