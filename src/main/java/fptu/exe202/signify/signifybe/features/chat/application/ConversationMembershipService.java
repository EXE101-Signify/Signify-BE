package fptu.exe202.signify.signifybe.features.chat.application;

import fptu.exe202.signify.signifybe.features.chat.application.port.out.ConversationParticipantRepository;
import fptu.exe202.signify.signifybe.features.chat.application.port.out.ConversationRepository;
import fptu.exe202.signify.signifybe.features.chat.domain.exception.ConversationException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared service for conversation membership validation.
 * <p>
 * Designed to be reused by all chat-related sections (Message, Reaction, etc.)
 * without creating a dependency on {@link ConversationService}.
 * </p>
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConversationMembershipService {

    ConversationRepository conversationRepository;
    ConversationParticipantRepository participantRepository;

    /**
     * Validates that the conversation exists and the user is an active participant.
     * <p>
     * Distinguishes between "conversation does not exist" (404) and
     * "user is not a participant" (403) for proper error reporting.
     * </p>
     *
     * @param conversationId the conversation to check
     * @param userId         the user to validate
     * @throws ConversationException if conversation not found or user not a participant
     */
    @Transactional(readOnly = true)
    public void validateActiveMembership(long conversationId, long userId) {
        boolean conversationExists = conversationRepository.findById(conversationId).isPresent();
        if (!conversationExists) {
            throw ConversationException.notFound();
        }
        if (!participantRepository.isParticipant(conversationId, userId)) {
            throw ConversationException.accessDenied();
        }
    }

    /**
     * Checks whether the user is an active participant of the conversation
     * without throwing exceptions.
     *
     * @param conversationId the conversation to check
     * @param userId         the user to check
     * @return true if the user is an active participant
     */
    @Transactional(readOnly = true)
    public boolean isActiveMember(long conversationId, long userId) {
        return participantRepository.isParticipant(conversationId, userId);
    }
}
