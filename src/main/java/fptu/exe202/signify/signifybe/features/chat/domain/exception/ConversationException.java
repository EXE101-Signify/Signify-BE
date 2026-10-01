package fptu.exe202.signify.signifybe.features.chat.domain.exception;

import fptu.exe202.signify.apiresponse.exception.BaseException;
import org.springframework.http.HttpStatus;

/** Only safe messages cross the shared API exception handler. */
public final class ConversationException extends BaseException {
    private ConversationException(HttpStatus status, String message) { super(status, message); }

    public static ConversationException notFound() {
        return new ConversationException(HttpStatus.NOT_FOUND, "Conversation not found");
    }
    public static ConversationException accessDenied() {
        return new ConversationException(HttpStatus.FORBIDDEN, "You are not a participant of this conversation");
    }
    public static ConversationException cannotChatWithSelf() {
        return new ConversationException(HttpStatus.BAD_REQUEST, "Cannot create a conversation with yourself");
    }
    public static ConversationException privateAlreadyExists() {
        return new ConversationException(HttpStatus.CONFLICT, "A private conversation already exists between these users");
    }
    public static ConversationException invalidParticipants(String detail) {
        return new ConversationException(HttpStatus.BAD_REQUEST, detail);
    }
    public static ConversationException groupNameRequired() {
        return new ConversationException(HttpStatus.BAD_REQUEST, "Group conversation name is required");
    }
    public static ConversationException invalidMessage(String detail) {
        return new ConversationException(HttpStatus.BAD_REQUEST, detail);
    }
    public static ConversationException invalidOneToOneConversation() {
        return new ConversationException(HttpStatus.CONFLICT, "Messages can only be sent in a PRIVATE conversation with exactly two active participants");
    }
}
