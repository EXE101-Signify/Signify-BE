package fptu.exe202.signify.signifybe.features.chat.api;

import fptu.exe202.signify.signifybe.features.auth.application.AccountAccessService;
import fptu.exe202.signify.signifybe.features.auth.application.JwtService;
import fptu.exe202.signify.signifybe.features.auth.application.SessionAccessService;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.exception.AuthException;
import fptu.exe202.signify.signifybe.features.call.application.VideoCallService;
import fptu.exe202.signify.signifybe.features.call.domain.exception.VideoCallException;
import fptu.exe202.signify.signifybe.features.chat.application.PrivateChatAccessService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatStompAuthorization implements ChannelInterceptor {
    static Pattern SUBSCRIPTION = Pattern.compile("/user/queue/conversations/([1-9][0-9]*)");
    static Pattern CALL_SUBSCRIPTION = Pattern.compile("/user/queue/calls/([1-9][0-9]*)");

    static Pattern TYPING = Pattern.compile("/app/conversations/([1-9][0-9]*)/typing");

    JwtService jwt;

    SessionAccessService sessions;

    AccountAccessService accounts;

    PrivateChatAccessService access;
    VideoCallService calls;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        boolean wrapped = headers == null;
        if (wrapped) headers = StompHeaderAccessor.wrap(message);
        StompCommand command = headers.getCommand();
        if (command == null) return message;
        if (command == StompCommand.CONNECT) {
            String bearer = headers.getFirstNativeHeader("Authorization");
            if (bearer == null) bearer = headers.getFirstNativeHeader("authorization");
            if (bearer == null || !bearer.regionMatches(true, 0, "Bearer ", 0, 7))
                throw new AccessDeniedException("Authentication required");
            try {
                CurrentUser claims = jwt.validateAccessToken(bearer.substring(7));
                sessions.requireActiveSession(claims.sessionId(), claims.userId());
                CurrentUser user = accounts.requireActiveUser(claims.userId(), claims.sessionId());
                headers.setUser(user);
            } catch (AuthException ex) {
                throw new AccessDeniedException("Invalid access token");
            }
            return wrapped ? MessageBuilder.createMessage(message.getPayload(), headers.getMessageHeaders()) : message;
        }
        if (command == StompCommand.SUBSCRIBE || command == StompCommand.SEND) {
            if (!(headers.getUser() instanceof CurrentUser user))
                throw new AccessDeniedException("Authentication required");
            try {
                sessions.requireActiveSession(user.sessionId(), user.userId());
                accounts.requireActiveUser(user.userId(), user.sessionId());
            } catch (AuthException ex) {
                throw new AccessDeniedException("Invalid access token");
            }
            String destination = headers.getDestination();
            if (command == StompCommand.SUBSCRIBE && "/user/queue/notifications".equals(destination))
                return message;
            if (command == StompCommand.SUBSCRIBE) {
                var callSubscription = CALL_SUBSCRIPTION.matcher(destination == null ? "" : destination);
                if (callSubscription.matches()) {
                    try {
                        calls.validateActiveCall(Long.valueOf(callSubscription.group(1)), user);
                    } catch (NumberFormatException | VideoCallException ex) {
                        throw new AccessDeniedException("Call destination denied");
                    }
                    return message;
                }
            }
            if (command == StompCommand.SEND && "/app/presence/heartbeat".equals(destination))
                return message;
            var match = (command == StompCommand.SUBSCRIBE ? SUBSCRIPTION : TYPING)
                    .matcher(destination == null ? "" : destination);
            if (!match.matches()) throw new AccessDeniedException("Chat destination denied");
            long conversationId;
            try { conversationId = Long.parseLong(match.group(1)); }
            catch (NumberFormatException ex) { throw new AccessDeniedException("Chat destination denied"); }
            if (command == StompCommand.SUBSCRIBE) access.activeParticipantIds(conversationId, user.userId());
            else access.unblockedPeer(conversationId, user.userId());
        }
        return message;
    }
}
