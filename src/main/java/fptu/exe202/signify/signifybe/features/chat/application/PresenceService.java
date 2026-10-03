package fptu.exe202.signify.signifybe.features.chat.application;

import fptu.exe202.signify.signifybe.features.user.infrastructure.persistence.JpaUserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class PresenceService {
    public static final String CHANNEL = "chat:presence:transitions";
    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);
    private final PresenceStore store;
    private final PresenceStateService state;
    private final PresenceEventDispatcher localDispatcher;
    private final StringRedisTemplate redis;
    private final JpaUserRepository users;
    private final PrivateChatAccessService access;
    private final Clock clock;

    public void connect(long userId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return;
        try {
            long now = clock.millis();
            if (store.connect(userId, sessionId, now)) transition(userId, true, now);
        } catch (RuntimeException ex) { log.warn("Presence connect unavailable for user {}", userId, ex); }
    }

    public void heartbeat(long userId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return;
        try {
            long now = clock.millis();
            if (!store.heartbeat(userId, sessionId, now) && store.connect(userId, sessionId, now))
                transition(userId, true, now);
        } catch (RuntimeException ex) { log.warn("Presence heartbeat unavailable for user {}", userId, ex); }
    }

    public void disconnect(long userId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return;
        try {
            long now = clock.millis();
            if (store.disconnect(userId, sessionId, now)) transition(userId, false, now);
        } catch (RuntimeException ex) { log.warn("Presence disconnect unavailable for user {}", userId, ex); }
    }

    @Scheduled(fixedDelayString = "${chat.presence.sweep-ms:15000}")
    public void expire() {
        try {
            long now = clock.millis();
            for (long userId : store.expire(now)) transition(userId, false, now);
        } catch (RuntimeException ex) { log.warn("Presence expiry sweep unavailable", ex); }
    }

    @Scheduled(fixedDelayString = "${chat.presence.reconcile-ms:60000}")
    public void reconcile() {
        try {
            long now = clock.millis();
            // Recover from a Redis restart that erased the active-user set.
            for (long userId : users.findOnlineUserIds()) {
                if (!store.isOnline(userId, now)) {
                    transition(userId, false, now);
                    if (store.isOnline(userId, clock.millis())) transition(userId, true, clock.millis());
                }
            }
        } catch (RuntimeException ex) { log.warn("Presence reconciliation unavailable", ex); }
    }

    public PeerPresence peerPresence(long conversationId, long requesterId) {
        long peer = access.unblockedPeer(conversationId, requesterId);
        Long lastSeenAt = users.findById(peer).map(u -> u.getLastSeenAt()).orElse(null);
        try {
            return new PeerPresence(conversationId, peer,
                    store.isOnline(peer, clock.millis()) ? "ONLINE" : "OFFLINE", lastSeenAt);
        } catch (RuntimeException ex) {
            log.warn("Presence lookup unavailable for user {}", peer, ex);
            return new PeerPresence(conversationId, peer, "UNKNOWN", lastSeenAt);
        }
    }

    private void transition(long userId, boolean online, long now) {
        try { if (online) state.online(userId); else state.offline(userId, now); }
        catch (RuntimeException ex) { log.warn("Could not persist presence transition for user {}", userId, ex); }
        PresenceEvent event = PresenceEvent.of(userId, online, now);
        boolean localFallback = false;
        try {
            Long listeners = redis.convertAndSend(CHANNEL, userId + ":" + online + ":" + now);
            localFallback = listeners == null || listeners == 0;
        } catch (RuntimeException ex) {
            log.warn("Could not publish presence transition for user {}", userId, ex);
            localFallback = true;
        }
        if (localFallback) {
            try { localDispatcher.dispatch(event); }
            catch (RuntimeException ex) { log.warn("Could not deliver local presence transition for user {}", userId, ex); }
        }
    }

    public record PeerPresence(long conversationId, long userId, String status, Long lastSeenAt) { }
}
