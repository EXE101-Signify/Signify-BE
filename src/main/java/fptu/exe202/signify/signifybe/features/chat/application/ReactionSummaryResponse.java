package fptu.exe202.signify.signifybe.features.chat.application;

import java.util.List;
import java.util.Map;

public record ReactionSummaryResponse(long messageId, List<Item> reactions, Map<String, Long> counts) {
    public record Item(long reactionId, long userId, String reaction, long createdAt, Long updatedAt) { }
}
