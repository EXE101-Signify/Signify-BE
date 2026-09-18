package fptu.exe202.signify.signifybe.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger configuration for the Signify API.
 * <p>
 * Provides:
 * <ul>
 *   <li>API metadata (title, description, version)</li>
 *   <li>JWT Bearer authentication scheme in Swagger UI</li>
 *   <li>Global security requirement so all endpoints show the lock icon</li>
 * </ul>
 * <p>
 * Swagger UI is accessible at: {@code /swagger-ui.html}
 * <br>
 * OpenAPI docs at: {@code /v3/api-docs}
 * </p>
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "Bearer Authentication";

    @Bean
    public OpenAPI signifyOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Signify API")
                        .description("Signify Backend REST API Documentation")
                        .version("0.0.1-SNAPSHOT")
                )
                .addSecurityItem(new SecurityRequirement()
                        .addList(SECURITY_SCHEME_NAME)
                )
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter your JWT token")
                        )
                );
    }
}
