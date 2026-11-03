package fptu.exe202.signify.signifybe.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.payos.PayOS;

import java.net.URI;

@Configuration
public class PayOSConfig {

    @Value("${spring.payos.client-id}")
    private String clientId;

    @Value("${spring.payos.api-key}")
    private String apiKey;

    @Value("${spring.payos.checksum-key}")
    private String checkSumId;

    @Value("${spring.payos.return-url}")
    private String returnUrl;

    @Value("${spring.payos.cancel-url}")
    private String cancelUrl;

    @Value("${spring.payos.webhook-url}")
    private String webhookUrl;

    @Bean
    public PayOS payOS() {
        if (blank(clientId) || blank(apiKey) || blank(checkSumId))
            throw new IllegalStateException("PayOS credentials must be configured");
        validateUrl(returnUrl, "return");
        validateUrl(cancelUrl, "cancel");
        validateUrl(webhookUrl, "webhook");
        return new PayOS(
                clientId,
                apiKey,
                checkSumId
        );
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static void validateUrl(String value, String name) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()))
                throw new IllegalArgumentException();
            if (uri.getHost() == null) throw new IllegalArgumentException();
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Invalid PayOS " + name + " URL");
        }
    }
}
