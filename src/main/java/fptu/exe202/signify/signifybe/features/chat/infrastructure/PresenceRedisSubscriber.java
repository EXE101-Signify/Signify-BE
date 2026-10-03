package fptu.exe202.signify.signifybe.features.chat.infrastructure;

import fptu.exe202.signify.signifybe.features.chat.application.PresenceEvent;
import fptu.exe202.signify.signifybe.features.chat.application.PresenceEventDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class PresenceRedisSubscriber implements MessageListener {
    private final PresenceEventDispatcher dispatcher;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String[] parts = new String(message.getBody(), StandardCharsets.UTF_8).split(":", 3);
        if (parts.length != 3) return;
        try {
            long userId = Long.parseLong(parts[0]);
            boolean online = Boolean.parseBoolean(parts[1]);
            long timestamp = Long.parseLong(parts[2]);
            if (userId > 0 && ("true".equals(parts[1]) || "false".equals(parts[1])))
                dispatcher.dispatch(PresenceEvent.of(userId, online, timestamp));
        } catch (NumberFormatException ignored) { }
    }
}
