package fptu.exe202.signify.signifybe.features.chat.application;

import fptu.exe202.signify.signifybe.features.chat.application.port.out.ConversationParticipantRepository;
import fptu.exe202.signify.signifybe.features.chat.application.port.out.ConversationRepository;
import fptu.exe202.signify.signifybe.features.chat.domain.ConversationType;
import fptu.exe202.signify.signifybe.features.chat.domain.exception.ConversationException;
import fptu.exe202.signify.signifybe.features.user.application.BlockValidationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PrivateChatAccessService {

    ConversationMembershipService membership;

    ConversationRepository conversations;

    ConversationParticipantRepository participants;

    BlockValidationService blocks;

    public List<Long> activeParticipantIds(long conversationId, long userId) {
        membership.validateActiveMembership(conversationId, userId);
        var conversation = conversations.findById(conversationId)
                .orElseThrow(ConversationException::notFound);
        var active = participants.findActiveByConversationId(conversationId);
        if (!ConversationType.PRIVATE.name().equalsIgnoreCase(conversation.getType())
                || active.size() != 2 || active.get(0).getUserId().equals(active.get(1).getUserId())) {
            throw ConversationException.invalidOneToOneConversation();
        }
        if (active.stream().noneMatch(p -> p.getUserId() == userId)) {
            throw ConversationException.accessDenied();
        }
        return active.stream().map(p -> p.getUserId()).toList();
    }

    public long unblockedPeer(long conversationId, long userId) {
        long peer = activeParticipantIds(conversationId, userId).stream()
                .filter(id -> id != userId).findFirst()
                .orElseThrow(ConversationException::invalidOneToOneConversation);
        blocks.assertCanInteract(userId, peer);
        return peer;
    }
}
