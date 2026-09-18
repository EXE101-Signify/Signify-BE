package fptu.exe202.signify.signifybe.config;

import org.springframework.context.annotation.Configuration;

/**
 * Mail configuration for the Signify application.
 * <p>
 * This class is currently empty because Spring Boot's auto-configuration
 * for {@code JavaMailSender} is sufficient given the properties defined in
 * {@code application.yaml} under {@code spring.mail.*}.
 * </p>
 * <p>
 * Custom beans like a {@code MailService} should be placed in the {@code service} package,
 * not here, to maintain a clean separation of concerns.
 * </p>
 */
@Configuration
public class MailConfig {
    // Spring Boot auto-configuration handles the creation of JavaMailSender bean
    // based on spring.mail.* properties in application.yaml.
}
