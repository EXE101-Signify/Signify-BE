package fptu.exe202.signify.signifybe.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables Spring's {@code @Async} processing.
 * <p>
 * Used by {@link fptu.exe202.signify.signifybe.features.email.application.EmailService}
 * to send emails on a separate thread pool without blocking the HTTP request.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
