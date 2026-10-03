package fptu.exe202.signify.signifybe.features.chat.infrastructure;

import fptu.exe202.signify.signifybe.features.chat.application.PresenceStore;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RedisPresenceStore implements PresenceStore {
    private static final String ACTIVE = "chat:presence:active-users";
    private static final DefaultRedisScript<Long> CONNECT = script("""
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[3])
            redis.call('SET', KEYS[2], ARGV[1], 'PX', ARGV[5])
            redis.call('ZADD', KEYS[1], ARGV[4], ARGV[2])
            redis.call('PEXPIRE', KEYS[1], ARGV[5] * 2)
            return redis.call('SADD', KEYS[3], ARGV[1])
            """);
    private static final DefaultRedisScript<Long> HEARTBEAT = script("""
            if redis.call('GET', KEYS[2]) ~= ARGV[1] then return 0 end
            redis.call('SET', KEYS[2], ARGV[1], 'PX', ARGV[5])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[3])
            redis.call('ZADD', KEYS[1], ARGV[4], ARGV[2])
            redis.call('PEXPIRE', KEYS[1], ARGV[5] * 2)
            redis.call('SADD', KEYS[3], ARGV[1])
            return 1
            """);
    private static final DefaultRedisScript<Long> DISCONNECT = script("""
            if redis.call('GET', KEYS[2]) == ARGV[1] then redis.call('DEL', KEYS[2]) end
            redis.call('ZREM', KEYS[1], ARGV[2])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[3])
            if redis.call('ZCARD', KEYS[1]) == 0 then
              return redis.call('SREM', KEYS[3], ARGV[1])
            end
            return 0
            """);
    private static final DefaultRedisScript<Long> EXPIRE = script("""
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
            if redis.call('ZCARD', KEYS[1]) == 0 then
              return redis.call('SREM', KEYS[2], ARGV[2])
            end
            return 0
            """);

    private final StringRedisTemplate redis;
    @Value("${chat.presence.ttl-ms:90000}")
    private long ttlMs;

    @Override
    public boolean connect(long userId, String sessionId, long now) {
        return one(redis.execute(CONNECT, keys(userId, sessionId), Long.toString(userId), sessionId,
                Long.toString(now), Long.toString(now + ttlMs), Long.toString(ttlMs)));
    }

    @Override
    public boolean heartbeat(long userId, String sessionId, long now) {
        return one(redis.execute(HEARTBEAT, keys(userId, sessionId), Long.toString(userId), sessionId,
                Long.toString(now), Long.toString(now + ttlMs), Long.toString(ttlMs)));
    }

    @Override
    public boolean disconnect(long userId, String sessionId, long now) {
        return one(redis.execute(DISCONNECT, keys(userId, sessionId), Long.toString(userId), sessionId,
                Long.toString(now)));
    }

    @Override
    public List<Long> expire(long now) {
        Set<String> active = redis.opsForSet().members(ACTIVE);
        if (active == null || active.isEmpty()) return List.of();
        List<Long> offline = new ArrayList<>();
        for (String id : active) {
            long userId;
            try { userId = Long.parseLong(id); }
            catch (NumberFormatException ex) { continue; }
            if (one(redis.execute(EXPIRE, List.of(userKey(userId), ACTIVE),
                    Long.toString(now), id))) offline.add(userId);
        }
        return offline;
    }

    @Override
    public boolean isOnline(long userId, long now) {
        Long count = redis.opsForZSet().count(userKey(userId), now + 1, Double.POSITIVE_INFINITY);
        return count != null && count > 0;
    }

    private List<String> keys(long userId, String sessionId) {
        return List.of(userKey(userId), "chat:presence:session:" + sessionId, ACTIVE);
    }

    private String userKey(long userId) { return "chat:presence:user:" + userId; }
    private boolean one(Long result) { return result != null && result == 1; }
    private static DefaultRedisScript<Long> script(String source) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(source);
        script.setResultType(Long.class);
        return script;
    }
}
