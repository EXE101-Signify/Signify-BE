package fptu.exe202.signify.signifybe.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.payos.PayOS;

@Configuration
public class PayOSConfig {
    @Bean
    public PayOS payOS() {
        return new PayOS(
                System.getenv("PAYOS_CLIENT_ID"),
                System.getenv("PAYOS_API_KEY"),
                System.getenv("PAYOS_CHECKSUM_KEY"));
    }
}
