package fptu.exe202.signify.signifybe.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Central Spring Security configuration for the Signify application.
 * <p>
 * Configures:
 * <ul>
 *   <li>Stateless session management (JWT-ready)</li>
 *   <li>CORS integration (delegates to {@link CorsConfig})</li>
 *   <li>CSRF disabled (stateless REST API)</li>
 *   <li>Public endpoints for authentication and Swagger</li>
 *   <li>BCrypt password encoder</li>
 *   <li>{@code @PreAuthorize} support via {@code @EnableMethodSecurity}</li>
 * </ul>
 * <p>
 * <b>JWT Filter Integration:</b> When {@code JwtAuthenticationFilter} is created
 * in the {@code security} package, uncomment the filter registration line below
 * to enable JWT-based authentication.
 * </p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    // TODO: Inject JwtAuthenticationFilter when it is created in the security package.
    //
    // Example:
    //   private final JwtAuthenticationFilter jwtAuthenticationFilter;
    //
    //   public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
    //       this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    //   }

    /**
     * Public endpoints that do not require authentication.
     */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/**",
            "/api/v1/public/**"
    };

    /**
     * Swagger / OpenAPI endpoints.
     */
    private static final String[] SWAGGER_ENDPOINTS = {
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/swagger-resources/**",
            "/webjars/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CORS — delegates to CorsConfigurationSource bean in CorsConfig
                .cors(Customizer.withDefaults())

                // Disable CSRF for stateless REST API
                .csrf(AbstractHttpConfigurer::disable)

                // Stateless session — no server-side session (JWT-based auth)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Endpoint authorization
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(SWAGGER_ENDPOINTS).permitAll()
                        .anyRequest().authenticated()
                );

        // TODO: Register JWT filter before UsernamePasswordAuthenticationFilter
        //       when JwtAuthenticationFilter is implemented.
        //
        // http.addFilterBefore(jwtAuthenticationFilter,
        //         UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
