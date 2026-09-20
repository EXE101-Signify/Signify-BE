package fptu.exe202.signify.signifybe.auth.infrastructure.security;

import fptu.exe202.signify.signifybe.auth.application.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class AuthConfiguration {
    @Bean public Clock authClock() { return Clock.systemUTC(); }
}
