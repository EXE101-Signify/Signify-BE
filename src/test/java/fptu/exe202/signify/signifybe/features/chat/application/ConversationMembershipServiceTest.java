package fptu.exe202.signify.signifybe.features.chat.application;

import fptu.exe202.signify.signifybe.features.chat.application.port.out.ConversationParticipantRepository;
import fptu.exe202.signify.signifybe.features.chat.application.port.out.ConversationRepository;
import fptu.exe202.signify.signifybe.features.chat.domain.Conversation;
import fptu.exe202.signify.signifybe.features.chat.domain.exception.ConversationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationMembershipServiceTest {

    @Mock
    ConversationRepository conversationRepository;

    @Mock
    ConversationParticipantRepository participantRepository;

    @InjectMocks
    ConversationMembershipService membershipService;

    static final long CONVERSATION_ID = 1L;
    static final long USER_ID = 10L;

    @Nested
    @DisplayName("validateActiveMembership")
    class ValidateActiveMembership {

        @Test
        @DisplayName("should pass when user is active participant")
        void activeParticipant_passes() {
            when(conversationRepository.findById(CONVERSATION_ID))
                    .thenReturn(Optional.of(stubConversation()));
            when(participantRepository.isParticipant(CONVERSATION_ID, USER_ID))
                    .thenReturn(true);

            assertDoesNotThrow(() ->
                    membershipService.validateActiveMembership(CONVERSATION_ID, USER_ID));

            verify(conversationRepository).findById(CONVERSATION_ID);
            verify(participantRepository).isParticipant(CONVERSATION_ID, USER_ID);
        }

        @Test
        @DisplayName("should throw 404 when conversation does not exist")
        void conversationNotFound_throws404() {
            when(conversationRepository.findById(CONVERSATION_ID))
                    .thenReturn(Optional.empty());

            ConversationException ex = assertThrows(ConversationException.class, () ->
                    membershipService.validateActiveMembership(CONVERSATION_ID, USER_ID));

            assertEquals(404, ex.getStatus().value());
            assertEquals("Conversation not found", ex.getMessage());

            // Should NOT check participant when conversation doesn't exist
            verify(participantRepository, never()).isParticipant(anyLong(), anyLong());
        }

        @Test
        @DisplayName("should throw 403 when user is not a participant")
        void nonParticipant_throws403() {
            when(conversationRepository.findById(CONVERSATION_ID))
                    .thenReturn(Optional.of(stubConversation()));
            when(participantRepository.isParticipant(CONVERSATION_ID, USER_ID))
                    .thenReturn(false);

            ConversationException ex = assertThrows(ConversationException.class, () ->
                    membershipService.validateActiveMembership(CONVERSATION_ID, USER_ID));

            assertEquals(403, ex.getStatus().value());
            assertEquals("You are not a participant of this conversation", ex.getMessage());
        }

        @Test
        @DisplayName("should throw 403 when participant is inactive")
        void inactiveParticipant_throws403() {
            // isParticipant checks is_active = true, so inactive returns false
            when(conversationRepository.findById(CONVERSATION_ID))
                    .thenReturn(Optional.of(stubConversation()));
            when(participantRepository.isParticipant(CONVERSATION_ID, USER_ID))
                    .thenReturn(false);

            ConversationException ex = assertThrows(ConversationException.class, () ->
                    membershipService.validateActiveMembership(CONVERSATION_ID, USER_ID));

            assertEquals(403, ex.getStatus().value());
        }
    }

    @Nested
    @DisplayName("isActiveMember")
    class IsActiveMember {

        @Test
        @DisplayName("should return true for active participant")
        void activeParticipant_returnsTrue() {
            when(participantRepository.isParticipant(CONVERSATION_ID, USER_ID))
                    .thenReturn(true);

            assertTrue(membershipService.isActiveMember(CONVERSATION_ID, USER_ID));
        }

        @Test
        @DisplayName("should return false for non-participant")
        void nonParticipant_returnsFalse() {
            when(participantRepository.isParticipant(CONVERSATION_ID, USER_ID))
                    .thenReturn(false);

            assertFalse(membershipService.isActiveMember(CONVERSATION_ID, USER_ID));
        }

        @Test
        @DisplayName("should return false for inactive participant")
        void inactiveParticipant_returnsFalse() {
            when(participantRepository.isParticipant(CONVERSATION_ID, USER_ID))
                    .thenReturn(false);

            assertFalse(membershipService.isActiveMember(CONVERSATION_ID, USER_ID));
        }
    }

    private Conversation stubConversation() {
        return new Conversation("PRIVATE", null, 99L, System.currentTimeMillis());
    }
}
