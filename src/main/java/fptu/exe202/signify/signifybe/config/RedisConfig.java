package fptu.exe202.signify.signifybe.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration providing a custom {@link RedisTemplate} with JSON serialization.
 * <p>
 * The default Spring Boot auto-configured {@code StringRedisTemplate} is still available.
 * This configuration adds a {@code RedisTemplate<String, Object>} that:
 * <ul>
 *   <li>Uses {@link StringRedisSerializer} for keys (human-readable in Redis CLI)</li>
 *   <li>Uses {@link RedisSerializer#json()} for values (stores objects as JSON)</li>
 * </ul>
 * <p>
 * Suitable for storing OTP codes, JWT refresh tokens, session data, and other
 * structured objects. Connection properties are read from {@code spring.data.redis.*}
 * in application.yaml (auto-configured by Spring Boot).
 * </p>
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Keys as plain strings — readable in Redis CLI
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Values as JSON — no Java native serialization
        RedisSerializer<Object> jsonSerializer = RedisSerializer.json();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
