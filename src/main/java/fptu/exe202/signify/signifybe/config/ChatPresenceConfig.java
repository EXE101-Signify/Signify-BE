package fptu.exe202.signify.signifybe.config;

import fptu.exe202.signify.signifybe.features.chat.application.PresenceService;
import fptu.exe202.signify.signifybe.features.chat.infrastructure.PresenceRedisSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class ChatPresenceConfig {
    @Bean
    RedisMessageListenerContainer presenceListenerContainer(RedisConnectionFactory connectionFactory,
                                                            PresenceRedisSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(PresenceService.CHANNEL));
        // A Redis outage must not prevent the REST application from starting.
        container.setAutoStartup(false);
        container.setRecoveryInterval(5_000);
        return container;
    }
}
