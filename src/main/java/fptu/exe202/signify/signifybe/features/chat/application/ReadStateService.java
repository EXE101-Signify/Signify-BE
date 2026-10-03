package fptu.exe202.signify.signifybe.features.chat.application;

import fptu.exe202.signify.signifybe.features.chat.domain.exception.ConversationException;
import fptu.exe202.signify.signifybe.features.chat.infrastructure.persistence.JpaConversationParticipantRepository;
import fptu.exe202.signify.signifybe.features.chat.infrastructure.persistence.JpaMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class ReadStateService {
    private final PrivateChatAccessService access;
    private final JpaConversationParticipantRepository participants;
    private final JpaMessageRepository messages;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public ReadReceiptEvent markRead(long conversationId, long userId, long messageId) {
        if (messageId <= 0) throw ConversationException.invalidMessage("lastReadMessageId must be positive");
        access.activeParticipantIds(conversationId, userId);
        var participant = participants.lockActive(conversationId, userId)
                .orElseThrow(ConversationException::accessDenied);
        var message = messages.findById(messageId)
                .filter(m -> m.getConversationId() == conversationId)
                .orElseThrow(ConversationException::messageNotFound);
        Long current = participant.getLastReadMessageId();
        if (current != null && message.getId() < current) throw ConversationException.readCursorConflict();
        ReadReceiptEvent receipt = ReadReceiptEvent.of(conversationId, userId, messageId, clock.millis());
        if (current != null && message.getId().equals(current)) return receipt;
        participant.setLastReadMessageId(messageId);
        participants.saveAndFlush(participant);
        events.publishEvent(receipt);
        return receipt;
    }

    @Transactional(readOnly = true)
    public UnreadCount unreadCount(long conversationId, long userId) {
        access.activeParticipantIds(conversationId, userId);
        var participant = participants.findByConversationIdAndUserIdAndActiveTrue(conversationId, userId)
                .orElseThrow(ConversationException::accessDenied);
        long cursor = participant.getLastReadMessageId() == null ? 0 : participant.getLastReadMessageId();
        return new UnreadCount(conversationId, messages.countUnread(conversationId, userId, cursor));
    }

    public record UnreadCount(long conversationId, long unreadCount) { }
}
