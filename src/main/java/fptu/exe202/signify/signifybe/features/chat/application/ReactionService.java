package fptu.exe202.signify.signifybe.features.chat.application;

import fptu.exe202.signify.signifybe.features.chat.application.port.out.MessageRepository;
import fptu.exe202.signify.signifybe.features.chat.domain.MessageReaction;
import fptu.exe202.signify.signifybe.features.chat.domain.exception.ConversationException;
import fptu.exe202.signify.signifybe.features.chat.infrastructure.persistence.JpaMessageReactionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReactionService {

    ConversationMembershipService membership;

    MessageRepository messages;

    JpaMessageReactionRepository reactions;

    Clock clock;

    @Transactional
    public ReactionSummaryResponse addOrChange(long conversationId, long messageId, long userId, String value) {
        String reaction = ReactionType.parse(value).name();
        validateMessage(conversationId, messageId, userId, true);
        var existing = reactions.findByMessageIdAndUserId(messageId, userId);
        if (existing.isPresent()) {
            MessageReaction current = existing.get();
            if (!current.getReaction().equals(reaction)) {
                current.changeTo(reaction, clock.millis());
                reactions.saveAndFlush(current);
            }
        } else {
            reactions.saveAndFlush(new MessageReaction(messageId, userId, reaction, clock.millis()));
        }
        return summary(messageId);
    }

    @Transactional
    public ReactionSummaryResponse remove(long conversationId, long messageId, long userId, String value) {
        String reaction = ReactionType.parse(value).name();
        validateMessage(conversationId, messageId, userId, true);
        MessageReaction current = reactions.findByMessageIdAndUserId(messageId, userId)
                .filter(row -> row.getReaction().equals(reaction))
                .orElseThrow(ConversationException::reactionNotFound);
        reactions.delete(current);
        reactions.flush();
        return summary(messageId);
    }

    @Transactional(readOnly = true)
    public ReactionSummaryResponse get(long conversationId, long messageId, long userId) {
        validateMessage(conversationId, messageId, userId, false);
        return summary(messageId);
    }

    private void validateMessage(long conversationId, long messageId, long userId, boolean lock) {
        membership.validateActiveMembership(conversationId, userId);
        (lock ? messages.findByIdForUpdate(messageId) : messages.findById(messageId))
                .filter(row -> row.getConversationId() == conversationId && !row.isDeleted())
                .orElseThrow(ConversationException::messageNotFound);
    }

    private ReactionSummaryResponse summary(long messageId) {
        List<ReactionSummaryResponse.Item> items = new ArrayList<>();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (MessageReaction row : reactions.findByMessageIdOrderByIdAsc(messageId)) {
            items.add(new ReactionSummaryResponse.Item(row.getId(), row.getUserId(), row.getReaction(),
                    row.getCreatedAt(), row.getUpdatedAt()));
            counts.merge(row.getReaction(), 1L, Long::sum);
        }
        return new ReactionSummaryResponse(messageId, items, counts);
    }
}
