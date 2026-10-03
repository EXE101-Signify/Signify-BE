package fptu.exe202.signify.signifybe.features.chat.application;

import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.chat.api.TypingRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TypingService {
    static long MIN_INTERVAL_MS = 500;

    PrivateChatAccessService access;

    SimpMessagingTemplate messaging;

    Clock clock;

    ConcurrentHashMap<String, Long> lastSent = new ConcurrentHashMap<>();

    public void send(long conversationId, CurrentUser sender, TypingRequest request) {
        if (sender == null) throw new AccessDeniedException("Authentication required");
        long peer = access.unblockedPeer(conversationId, sender.userId());
        String type = request == null ? null : request.type();
        if (!"TYPING_START".equals(type) && !"TYPING_STOP".equals(type))
            throw new IllegalArgumentException("Unsupported typing event");
        long now = clock.millis();
        String key = sender.userId() + ":" + conversationId + ":" + type;
        boolean[] allowed = {false};
        lastSent.compute(key, (ignored, previous) -> {
            if (previous == null || now - previous >= MIN_INTERVAL_MS) {
                allowed[0] = true;
                return now;
            }
            return previous;
        });
        if (!allowed[0]) return;
        if (lastSent.size() > 10_000) lastSent.entrySet().removeIf(e -> now - e.getValue() > 10_000);
        ChatEvent event = new ChatEvent(UUID.randomUUID().toString(), type, conversationId,
                null, sender.userId(), null, null, null, null, null);
        messaging.convertAndSendToUser(Long.toString(peer), "/queue/conversations/" + conversationId, event);
    }
}
