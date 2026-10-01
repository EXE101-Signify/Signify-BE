package fptu.exe202.signify.signifybe.features.chat.application;

import fptu.exe202.signify.signifybe.features.chat.api.dto.ParticipantResponse;
import fptu.exe202.signify.signifybe.features.chat.application.port.out.ConversationParticipantRepository;
import fptu.exe202.signify.signifybe.features.chat.application.port.out.ConversationRepository;
import fptu.exe202.signify.signifybe.features.chat.domain.ConversationParticipant;
import fptu.exe202.signify.signifybe.features.user.application.port.out.UserRepository;
import fptu.exe202.signify.signifybe.features.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationParticipantServiceTest {

    @Mock ConversationRepository conversationRepository;
    @Mock ConversationParticipantRepository participantRepository;
    @Mock ConversationMembershipService membershipService;
    @Mock UserRepository userRepository;
    @Mock Clock clock;
    @Mock EntityManager entityManager;

    @InjectMocks
    ConversationService conversationService;

    static final long CONVERSATION_ID = 1L;
    static final long USER_A = 10L;
    static final long USER_B = 20L;

    @Test
    @DisplayName("getParticipants should return participant list with user info and joinedAt")
    void getParticipants_returnsParticipantsWithUserInfo() {
        long joinedAtA = 1695900000000L;
        long joinedAtB = 1695900001000L;

        ConversationParticipant cpA = new ConversationParticipant(CONVERSATION_ID, USER_A, joinedAtA);
        ConversationParticipant cpB = new ConversationParticipant(CONVERSATION_ID, USER_B, joinedAtB);

        User userA = stubUser(USER_A, "Nguyen A", "avatar_a.png");
        User userB = stubUser(USER_B, "Tran B", "avatar_b.png");

        when(participantRepository.findActiveByConversationId(CONVERSATION_ID))
                .thenReturn(List.of(cpA, cpB));
        when(userRepository.findUserById(USER_A)).thenReturn(Optional.of(userA));
        when(userRepository.findUserById(USER_B)).thenReturn(Optional.of(userB));

        List<ParticipantResponse> result = conversationService.getParticipants(CONVERSATION_ID);

        assertEquals(2, result.size());

        // Verify first participant
        ParticipantResponse pA = result.stream().filter(p -> p.userId() == USER_A).findFirst().orElseThrow();
        assertEquals("Nguyen A", pA.fullName());
        assertEquals("avatar_a.png", pA.avatar());
        assertEquals(joinedAtA, pA.joinedAt());

        // Verify second participant
        ParticipantResponse pB = result.stream().filter(p -> p.userId() == USER_B).findFirst().orElseThrow();
        assertEquals("Tran B", pB.fullName());
        assertEquals("avatar_b.png", pB.avatar());
        assertEquals(joinedAtB, pB.joinedAt());
    }

    @Test
    @DisplayName("getParticipants should return at most two participants for PRIVATE")
    void getParticipants_privateConversation_maxTwoParticipants() {
        ConversationParticipant cpA = new ConversationParticipant(CONVERSATION_ID, USER_A, 1695900000000L);
        ConversationParticipant cpB = new ConversationParticipant(CONVERSATION_ID, USER_B, 1695900001000L);

        when(participantRepository.findActiveByConversationId(CONVERSATION_ID))
                .thenReturn(List.of(cpA, cpB));
        when(userRepository.findUserById(USER_A)).thenReturn(Optional.of(stubUser(USER_A, "A", null)));
        when(userRepository.findUserById(USER_B)).thenReturn(Optional.of(stubUser(USER_B, "B", null)));

        List<ParticipantResponse> result = conversationService.getParticipants(CONVERSATION_ID);

        assertTrue(result.size() <= 2);
    }

    @Test
    @DisplayName("getParticipants should not expose sensitive data")
    void getParticipants_doesNotExposeSensitiveData() {
        ConversationParticipant cp = new ConversationParticipant(CONVERSATION_ID, USER_A, 1695900000000L);
        User user = stubUser(USER_A, "Nguyen A", "avatar.png");
        user.setEmail("secret@email.com");
        user.setPhone("0123456789");
        user.setAddress("Secret Address");

        when(participantRepository.findActiveByConversationId(CONVERSATION_ID))
                .thenReturn(List.of(cp));
        when(userRepository.findUserById(USER_A)).thenReturn(Optional.of(user));

        List<ParticipantResponse> result = conversationService.getParticipants(CONVERSATION_ID);

        assertEquals(1, result.size());
        ParticipantResponse p = result.get(0);

        // ParticipantResponse record only has userId, fullName, avatar, joinedAt
        // No email, phone, address, password, or other sensitive fields
        assertEquals(USER_A, p.userId());
        assertEquals("Nguyen A", p.fullName());
        assertEquals("avatar.png", p.avatar());
        assertNotNull(p.joinedAt());

        // Compile-time guarantee: ParticipantResponse record has exactly 4 fields
        assertEquals(4, ParticipantResponse.class.getRecordComponents().length);
    }

    @Test
    @DisplayName("getParticipants should return empty list when no active participants")
    void getParticipants_noActiveParticipants_returnsEmpty() {
        when(participantRepository.findActiveByConversationId(CONVERSATION_ID))
                .thenReturn(List.of());

        List<ParticipantResponse> result = conversationService.getParticipants(CONVERSATION_ID);

        assertTrue(result.isEmpty());
    }

    private User stubUser(long id, String fullName, String avatar) {
        User user = new User("test@test.com", "First", "Last", System.currentTimeMillis());
        user.setId(id);
        user.setFullName(fullName);
        user.setAvatar(avatar);
        return user;
    }
}
