package fptu.exe202.signify.signifybe.auth.infrastructure.persistence;

import fptu.exe202.signify.signifybe.auth.application.*;
import fptu.exe202.signify.signifybe.auth.domain.*;
import fptu.exe202.signify.signifybe.auth.domain.exception.AuthException;
import fptu.exe202.signify.signifybe.auth.infrastructure.security.AuthConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

// Liquibase maps TEXT to CLOB on H2; PostgreSQL type validation remains enabled in production.
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=none", "spring.jpa.show-sql=false"})
@Import({JpaAuthPersistence.class, AuthService.class, JwtService.class, AuthConfiguration.class, AuthPersistenceTest.PasswordConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthPersistenceTest {
    private static final String ENCRYPTION_KEY = java.util.Base64.getEncoder()
            .encodeToString(io.jsonwebtoken.Jwts.ENC.A256GCM.key().build().getEncoded());
    @Autowired private AuthService auth;
    @Autowired private JpaUserRepository users;
    @Autowired private JpaAccountRepository accounts;
    @MockitoSpyBean private JpaUserSessionRepository sessions;
    @Autowired private JwtService jwt;
    private static final SessionMetadata METADATA = new SessionMetadata("tests", "127.0.0.1", "test-agent");
    private static final String SECRET = UUID.randomUUID() + "-" + UUID.randomUUID();

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", () -> SECRET);
        registry.add("app.jwt.refresh-encryption-key", () -> ENCRYPTION_KEY);
    }

    @BeforeEach void clean() {
        sessions.deleteAll();
        accounts.deleteAll();
        users.deleteAll();
    }

    @Test void registrationPersistsOnlyHashesAndUsesExistingTables() {
        var result = auth.register("persist-user", "password123", null, null, null, METADATA);
        assertThat(users.count()).isEqualTo(1);
        assertThat(accounts.count()).isEqualTo(1);
        assertThat(sessions.count()).isEqualTo(1);
        assertThat(result.user().userId()).isPositive();
        assertThat(accounts.findByUsername("persist-user").orElseThrow().getPasswordHash()).startsWith("$2");
        assertThat(sessions.findAll().get(0).getRefreshTokenHash()).isEqualTo(jwt.hashRefreshToken(result.tokens().refreshToken()));
    }

    @Test void rotationRevokesOldSessionAndLogoutAllRevokesEveryDevice() {
        var first = auth.register("rotate-user", "password123", null, null, null, METADATA);
        var other = auth.login("rotate-user", "password123", METADATA);
        var rotated = auth.refresh(first.tokens().refreshToken(), METADATA);
        assertThat(sessions.count()).isEqualTo(3);
        assertThatThrownBy(() -> auth.refresh(first.tokens().refreshToken(), METADATA)).isInstanceOf(AuthException.class);
        auth.logoutAll(first.user().userId());
        assertThat(sessions.findAll()).allMatch(session -> session.getRevokedAt() != null);
        assertThatThrownBy(() -> auth.refresh(other.tokens().refreshToken(), METADATA)).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> auth.refresh(rotated.refreshToken(), METADATA)).isInstanceOf(AuthException.class);
        // Access JWTs remain valid until expiry even after server-side refresh sessions are revoked.
        assertThat(jwt.validateAccessToken(rotated.accessToken()).userId()).isEqualTo(first.user().userId());
    }

    @Test void onlyOneConcurrentRefreshCanSucceed() throws Exception {
        var registered = auth.register("concurrent-user", "password123", null, null, null, METADATA);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> refresh = () -> {
            start.await();
            try {
                auth.refresh(registered.tokens().refreshToken(), METADATA);
                return true;
            } catch (AuthException expected) {
                return false;
            }
        };
        try {
            Future<Boolean> first = executor.submit(refresh);
            Future<Boolean> second = executor.submit(refresh);
            start.countDown();
            boolean firstResult = first.get(20, TimeUnit.SECONDS);
            boolean secondResult = second.get(20, TimeUnit.SECONDS);
            assertThat(firstResult ^ secondResult).isTrue();
            assertThat(sessions.count()).isEqualTo(2);
            assertThat(sessions.findAll().stream().filter(s -> s.getRevokedAt() == null)).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test void simultaneousRegistrationCannotCreateDuplicateAccountsOrOrphanUsers() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> register = () -> {
            start.await();
            try {
                auth.register("same-user", "password123", null, null, null, METADATA);
                return true;
            } catch (AuthException expected) {
                return false;
            }
        };
        try {
            Future<Boolean> first = executor.submit(register);
            Future<Boolean> second = executor.submit(register);
            start.countDown();
            boolean firstResult = first.get(20, TimeUnit.SECONDS);
            boolean secondResult = second.get(20, TimeUnit.SECONDS);
            assertThat(firstResult ^ secondResult).isTrue();
            assertThat(users.count()).isEqualTo(1);
            assertThat(accounts.count()).isEqualTo(1);
            assertThat(sessions.count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test void failedSessionCreationRollsBackUserAndAccount() {
        doThrow(new IllegalStateException("simulated persistence failure")).when(sessions).save(any(UserSession.class));
        assertThatThrownBy(() -> auth.register("rollback-user", "password123", null, null, null, METADATA))
                .isInstanceOf(IllegalStateException.class);
        assertThat(users.count()).isZero();
        assertThat(accounts.count()).isZero();
        assertThat(sessions.count()).isZero();
    }

    @Test void failedRotationRollsBackOldSessionRevocation() {
        var registered = auth.register("rollback-refresh", "password123", null, null, null, METADATA);
        doThrow(new IllegalStateException("simulated persistence failure")).when(sessions)
                .save(argThat((UserSession session) -> session.getId() == null));
        assertThatThrownBy(() -> auth.refresh(registered.tokens().refreshToken(), METADATA))
                .isInstanceOf(IllegalStateException.class);
        assertThat(sessions.count()).isEqualTo(1);
        assertThat(sessions.findAll().get(0).getRevokedAt()).isNull();
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class PasswordConfig {
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(4); }
    }
}

