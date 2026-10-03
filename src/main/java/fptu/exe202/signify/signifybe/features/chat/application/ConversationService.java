package fptu.exe202.signify.signifybe.features.chat.application;

import fptu.exe202.signify.signifybe.features.chat.api.dto.*;
import fptu.exe202.signify.signifybe.features.chat.application.port.out.ConversationParticipantRepository;
import fptu.exe202.signify.signifybe.features.chat.application.port.out.ConversationRepository;
import fptu.exe202.signify.signifybe.features.chat.domain.Conversation;
import fptu.exe202.signify.signifybe.features.chat.domain.ConversationParticipant;
import fptu.exe202.signify.signifybe.features.chat.domain.ConversationType;
import fptu.exe202.signify.signifybe.features.chat.domain.Message;
import fptu.exe202.signify.signifybe.features.chat.domain.exception.ConversationException;
import fptu.exe202.signify.signifybe.features.user.application.port.out.UserRepository;
import fptu.exe202.signify.signifybe.features.user.application.BlockValidationService;
import fptu.exe202.signify.signifybe.features.user.domain.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConversationService {

    ConversationRepository conversationRepository;
    ConversationParticipantRepository participantRepository;
    ConversationMembershipService membershipService;
    UserRepository userRepository;
    BlockValidationService blockValidation;
    Clock clock;
    EntityManager entityManager;

    @Transactional
    public ConversationResponse createConversation(long creatorId, CreateConversationRequest request) {
        ConversationType type = parseType(request.type());
        long now = clock.millis();

        // Ensure creator is always included in participant list
        Set<Long> allParticipantIds = new LinkedHashSet<>(request.participantIds());
        allParticipantIds.add(creatorId);

        // Validate: cannot create conversation with only yourself
        if (allParticipantIds.size() < 2) {
            throw ConversationException.cannotChatWithSelf();
        }

        // Validate all participant users exist
        validateUsersExist(allParticipantIds);

        if (type == ConversationType.PRIVATE) {
            return createPrivateConversation(creatorId, allParticipantIds, now);
        } else {
            return createGroupConversation(creatorId, request.name(), allParticipantIds, now);
        }
    }

    private ConversationResponse createPrivateConversation(long creatorId, Set<Long> participantIds, long now) {
        if (participantIds.size() != 2) {
            throw ConversationException.invalidParticipants("Private conversation must have exactly 2 participants");
        }

        List<Long> ids = new ArrayList<>(participantIds);
        long otherUserId = ids.get(0) == creatorId ? ids.get(1) : ids.get(0);
        blockValidation.assertCanInteract(creatorId, otherUserId);

        // Check if PRIVATE conversation already exists between these two users
        Optional<Long> existing = participantRepository.findPrivateConversationBetween(creatorId, otherUserId);
        if (existing.isPresent()) {
            // Return the existing conversation instead of creating a duplicate
            return getConversationDetail(existing.get());
        }

        Conversation conversation = new Conversation(ConversationType.PRIVATE.name(), null, creatorId, now);
        conversation = conversationRepository.save(conversation);

        List<ConversationParticipant> saved = saveParticipants(conversation.getId(), participantIds, now);

        return buildResponse(conversation, saved);
    }

    private ConversationResponse createGroupConversation(long creatorId, String name, Set<Long> participantIds, long now) {
        if (name == null || name.isBlank()) {
            throw ConversationException.groupNameRequired();
        }

        Conversation conversation = new Conversation(ConversationType.GROUP.name(), name.trim(), creatorId, now);
        conversation = conversationRepository.save(conversation);

        List<ConversationParticipant> saved = saveParticipants(conversation.getId(), participantIds, now);

        return buildResponse(conversation, saved);
    }

    @Transactional(readOnly = true)
    public List<ConversationListResponse> getConversations(long userId) {
        List<Long> conversationIds = participantRepository.findConversationIdsByUserId(userId);
        if (conversationIds.isEmpty()) {
            return List.of();
        }

        // Fetch conversations
        TypedQuery<Conversation> convQuery = entityManager.createQuery(
                "SELECT c FROM Conversation c WHERE c.id IN :ids ORDER BY c.updatedAt DESC",
                Conversation.class);
        convQuery.setParameter("ids", conversationIds);
        List<Conversation> conversations = convQuery.getResultList();

        // Fetch all participants for these conversations
        TypedQuery<ConversationParticipant> partQuery = entityManager.createQuery(
                "SELECT cp FROM ConversationParticipant cp WHERE cp.conversationId IN :ids AND cp.active = true",
                ConversationParticipant.class);
        partQuery.setParameter("ids", conversationIds);
        List<ConversationParticipant> allParticipants = partQuery.getResultList();

        Map<Long, List<ConversationParticipant>> participantsByConv = allParticipants.stream()
                .collect(Collectors.groupingBy(ConversationParticipant::getConversationId));

        // Collect all user IDs to fetch user info
        Set<Long> allUserIds = allParticipants.stream()
                .map(ConversationParticipant::getUserId)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = loadUsers(allUserIds);

        // Fetch last message for each conversation
        Map<Long, Message> lastMessages = findLastMessages(conversationIds);

        return conversations.stream()
                .map(conv -> {
                    List<ParticipantResponse> participants = buildParticipantResponses(
                            participantsByConv.getOrDefault(conv.getId(), List.of()), userMap);
                    Message lastMsg = lastMessages.get(conv.getId());
                    LastMessageResponse lastMsgResponse = lastMsg == null ? null :
                            new LastMessageResponse(lastMsg.getId(), lastMsg.getSenderId(),
                                    lastMsg.getContent(), lastMsg.getMessageType(), lastMsg.getCreatedAt());
                    return new ConversationListResponse(conv.getId(), conv.getType(), conv.getName(),
                            participants, lastMsgResponse, conv.getUpdatedAt());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversationDetail(long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(ConversationException::notFound);

        List<ConversationParticipant> participants = participantRepository.findActiveByConversationId(conversationId);

        return buildResponse(conversation, participants);
    }

    @Transactional(readOnly = true)
    public List<ParticipantResponse> getParticipants(long conversationId) {
        List<ConversationParticipant> participants = participantRepository.findActiveByConversationId(conversationId);
        Set<Long> userIds = participants.stream()
                .map(ConversationParticipant::getUserId)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = loadUsers(userIds);
        return buildParticipantResponses(participants, userMap);
    }

    public void validateParticipant(long conversationId, long userId) {
        membershipService.validateActiveMembership(conversationId, userId);
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    private ConversationType parseType(String type) {
        try {
            return ConversationType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ConversationException.invalidParticipants("Invalid conversation type. Must be PRIVATE or GROUP");
        }
    }

    private void validateUsersExist(Set<Long> userIds) {
        for (Long userId : userIds) {
            userRepository.findUserById(userId)
                    .orElseThrow(() -> ConversationException.invalidParticipants("User with ID " + userId + " does not exist"));
        }
    }

    private List<ConversationParticipant> saveParticipants(Long conversationId, Set<Long> participantIds, long now) {
        List<ConversationParticipant> participants = participantIds.stream()
                .map(userId -> new ConversationParticipant(conversationId, userId, now))
                .toList();
        return participantRepository.saveAll(participants);
    }

    private ConversationResponse buildResponse(Conversation conversation, List<ConversationParticipant> participants) {
        Set<Long> userIds = participants.stream()
                .map(ConversationParticipant::getUserId)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = loadUsers(userIds);
        List<ParticipantResponse> participantResponses = buildParticipantResponses(participants, userMap);

        return new ConversationResponse(conversation.getId(), conversation.getType(), conversation.getName(),
                conversation.getCreatorId(), participantResponses, conversation.getCreatedAt(), conversation.getUpdatedAt());
    }

    private Map<Long, User> loadUsers(Set<Long> userIds) {
        Map<Long, User> map = new HashMap<>();
        for (Long userId : userIds) {
            userRepository.findUserById(userId).ifPresent(u -> map.put(u.getId(), u));
        }
        return map;
    }

    private List<ParticipantResponse> buildParticipantResponses(
            List<ConversationParticipant> participants, Map<Long, User> userMap) {
        return participants.stream()
                .map(cp -> {
                    User user = userMap.get(cp.getUserId());
                    return user == null
                            ? new ParticipantResponse(cp.getUserId(), null, null, cp.getJoinedAt())
                            : new ParticipantResponse(user.getId(), user.getFullName(), user.getAvatar(), cp.getJoinedAt());
                })
                .toList();
    }

    private Map<Long, Message> findLastMessages(List<Long> conversationIds) {
        // Fetch last (most recent) non-deleted message per conversation
        TypedQuery<Message> query = entityManager.createQuery("""
                SELECT m FROM Message m WHERE m.id IN (
                    SELECT MAX(m2.id) FROM Message m2
                    WHERE m2.conversationId IN :ids AND m2.deleted = false
                    GROUP BY m2.conversationId
                )
                """, Message.class);
        query.setParameter("ids", conversationIds);

        return query.getResultList().stream()
                .collect(Collectors.toMap(Message::getConversationId, m -> m));
    }
}
