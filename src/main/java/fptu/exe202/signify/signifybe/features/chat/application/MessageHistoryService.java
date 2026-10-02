package fptu.exe202.signify.signifybe.features.chat.application;

import fptu.exe202.signify.signifybe.features.chat.application.port.out.ConversationRepository;
import fptu.exe202.signify.signifybe.features.chat.domain.ConversationType;
import fptu.exe202.signify.signifybe.features.chat.domain.Message;
import fptu.exe202.signify.signifybe.features.chat.domain.MessageAttachment;
import fptu.exe202.signify.signifybe.features.chat.domain.exception.ConversationException;
import fptu.exe202.signify.signifybe.features.chat.infrastructure.persistence.JpaMessageAttachmentRepository;
import fptu.exe202.signify.signifybe.features.chat.infrastructure.persistence.JpaMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageHistoryService {
    private final ConversationMembershipService membership;
    private final ConversationRepository conversations;
    private final JpaMessageRepository messages;
    private final JpaMessageAttachmentRepository attachments;

    @Transactional(readOnly = true)
    public MessageHistoryResponse get(long conversationId, long userId, Long before, int limit) {
        if (limit < 1 || limit > 100 || (before != null && before <= 0)) {
            throw ConversationException.invalidMessage("limit must be 1-100 and before must be positive");
        }
        membership.validateActiveMembership(conversationId, userId);
        var conversation = conversations.findById(conversationId).orElseThrow(ConversationException::notFound);
        if (!ConversationType.PRIVATE.name().equalsIgnoreCase(conversation.getType())) {
            throw ConversationException.invalidOneToOneConversation();
        }

        PageRequest page = PageRequest.of(0, limit + 1);
        List<Message> fetched = before == null
                ? messages.findByConversationIdAndDeletedFalseOrderByIdDesc(conversationId, page)
                : messages.findByConversationIdAndDeletedFalseAndIdLessThanOrderByIdDesc(conversationId, before, page);
        boolean hasMore = fetched.size() > limit;
        List<Message> visible = hasMore ? fetched.subList(0, limit) : fetched;
        if (visible.isEmpty()) return new MessageHistoryResponse(List.of(), null, false);

        List<Long> ids = visible.stream().map(Message::getId).toList();
        Map<Long, List<MessageHistoryResponse.Attachment>> files = attachments
                .findByMessageIdInAndDeletedFalse(ids).stream()
                .collect(Collectors.groupingBy(MessageAttachment::getMessageId,
                        Collectors.mapping(a -> new MessageHistoryResponse.Attachment(a.getId(),
                                a.getFileName(), a.getMimeType(), a.getFileSize(), a.getCreatedAt()),
                                Collectors.toList())));
        List<MessageHistoryResponse.Item> items = visible.stream()
                .map(m -> new MessageHistoryResponse.Item(m.getId(), m.getConversationId(), m.getSenderId(),
                        m.getContent(), m.getMessageType(), m.getCreatedAt(), m.getEditedAt(),
                        files.getOrDefault(m.getId(), List.of())))
                .toList();
        Long nextCursor = hasMore ? visible.get(visible.size() - 1).getId() : null;
        return new MessageHistoryResponse(items, nextCursor, hasMore);
    }
}
