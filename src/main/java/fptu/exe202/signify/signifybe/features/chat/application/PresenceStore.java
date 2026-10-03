package fptu.exe202.signify.signifybe.features.chat.application;

import java.util.List;

/** Redis backed connection leases; contains session IDs and user IDs, never tokens. */
public interface PresenceStore {
    boolean connect(long userId, String sessionId, long now);
    boolean heartbeat(long userId, String sessionId, long now);
    boolean disconnect(long userId, String sessionId, long now);
    List<Long> expire(long now);
    boolean isOnline(long userId, long now);
}
