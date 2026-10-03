package fptu.exe202.signify.signifybe.features.chat.application;

import fptu.exe202.signify.signifybe.features.chat.domain.exception.ConversationException;

import java.util.Locale;

public enum ReactionType {
    LIKE, LOVE, HAHA, WOW, SAD, ANGRY;

    public static ReactionType parse(String value) {
        if (value == null || value.isBlank()) throw ConversationException.invalidReaction();
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ConversationException.invalidReaction();
        }
    }
}
