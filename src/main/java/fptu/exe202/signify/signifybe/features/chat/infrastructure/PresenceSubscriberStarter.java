package fptu.exe202.signify.signifybe.features.chat.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
public class PresenceSubscriberStarter {

    private static final Logger log =
            LoggerFactory.getLogger(PresenceSubscriberStarter.class);

    private final RedisMessageListenerContainer container;

    public PresenceSubscriberStarter(
            @Qualifier("presenceListenerContainer")
            RedisMessageListenerContainer container
    ) {
        this.container = container;
    }

    @Scheduled(fixedDelay = 5_000, initialDelay = 1_000)
    public void ensureSubscribed() {
        if (container.isRunning()) {
            return;
        }

        try {
            container.start();
        } catch (RuntimeException ex) {
            log.warn(
                    "Presence Redis subscription unavailable; retrying",
                    ex
            );

            try {
                container.stop();
            } catch (RuntimeException stopFailure) {
                log.warn(
                        "Could not stop failed presence subscription",
                        stopFailure
                );
            }
        }
    }
}