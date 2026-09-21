package fptu.exe202.signify.signifybe.auth.application;

import fptu.exe202.signify.signifybe.features.auth.application.JwtProperties;
import fptu.exe202.signify.signifybe.features.auth.application.JwtService;
import fptu.exe202.signify.signifybe.features.auth.domain.Role;
import fptu.exe202.signify.signifybe.features.auth.infrastructure.security.AuthConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtConfigurationTest {
    @TempDir Path directory;

    private String newKey() {
        return Base64.getEncoder().encodeToString(io.jsonwebtoken.Jwts.ENC.A256GCM.key().build().getEncoded());
    }

    private ApplicationContextRunner runner(Path envFile) throws IOException {
        // Config Data imports are additive: overriding spring.config.import does not
        // remove the import declared in application.yaml. Relocate that import so
        // a developer's real .env can never satisfy a missing-key test.
        String yaml = new ClassPathResource("application.yaml").getContentAsString(StandardCharsets.UTF_8);
        assertThat(yaml).contains("optional:file:./.env[.properties]");
        Path config = directory.resolve("application.yaml");
        Files.writeString(config, yaml.replace("optional:file:./.env[.properties]",
                "optional:" + envFile.toUri() + "[.properties]"));
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    // Isolate these tests from developer/CI secrets.
                    context.getEnvironment().getPropertySources().remove("systemEnvironment");
                    context.getEnvironment().getPropertySources().remove("systemProperties");
                })
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.config.location=" + config.toUri(),
                        "JWT_SECRET=" + UUID.randomUUID() + UUID.randomUUID())
                .withUserConfiguration(AuthConfiguration.class, JwtService.class);
    }

    @Test void importsUnquotedBase64FromEnvAndStartsJwtService() throws Exception {
        String key = newKey();
        Path envFile = directory.resolve(".env");
        Files.writeString(envFile, "JWT_REFRESH_ENCRYPTION_KEY=" + key + "\n");
        runner(envFile).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(JwtService.class);
            assertThat(context.getBean(JwtProperties.class).refreshEncryptionKey()).isEqualTo(key);
            JwtService jwt = context.getBean(JwtService.class);
            assertThat(jwt.validateAccessToken(jwt.createAccessToken(42, Role.USER).value()).userId()).isEqualTo(42);
            assertThat(jwt.validateRefreshToken(jwt.createRefreshToken(42).value())).isEqualTo(42);
        });
    }

    @Test void environmentOverridesEnvFileIncludingAnExplicitlyEmptyValue() throws Exception {
        Path envFile = directory.resolve(".env");
        Files.writeString(envFile, "JWT_REFRESH_ENCRYPTION_KEY=" + newKey() + "\n");
        String environmentKey = newKey();
        for (String value : new String[]{environmentKey, ""}) {
            runner(envFile).withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                    new SystemEnvironmentPropertySource("testEnvironment", Map.of("JWT_REFRESH_ENCRYPTION_KEY", value))))
                    .run(context -> {
                        if (value.isEmpty()) {
                            assertInvalidKey(context.getStartupFailure());
                        } else {
                            assertThat(context).hasNotFailed();
                            assertThat(context.getBean(JwtProperties.class).refreshEncryptionKey()).isEqualTo(value);
                        }
                    });
        }
    }

    @Test void missingFileMissingVariableAndEmptyVariableFailClearly() throws Exception {
        Path envFile = directory.resolve(".env");
        runner(envFile).run(context -> assertInvalidKey(context.getStartupFailure()));
        for (String content : new String[]{"# no refresh key\n", "JWT_REFRESH_ENCRYPTION_KEY=\n",
                "JWT_REFRESH_ENCRYPTION_KEY=   \n"}) {
            Files.writeString(envFile, content);
            runner(envFile).run(context -> assertInvalidKey(context.getStartupFailure()));
        }
    }

    private void assertInvalidKey(Throwable failure) {
        assertThat(failure).hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("JWT_REFRESH_ENCRYPTION_KEY must be Base64 encoding of exactly 32 random bytes");
    }
}
