package fptu.exe202.signify.signifybe.auth.application;

import fptu.exe202.signify.signifybe.auth.application.port.out.*;
import fptu.exe202.signify.signifybe.auth.domain.*;
import fptu.exe202.signify.signifybe.auth.domain.exception.AuthException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

@Service
public class AuthService {
    private final AccountRepository accounts;
    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthService(AccountRepository accounts, UserRepository users, UserSessionRepository sessions,
                       PasswordEncoder passwordEncoder, JwtService jwtService, Clock clock) {
        this.accounts = accounts;
        this.users = users;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResult register(String username, String password, String email, String firstName,
                               String lastName, SessionMetadata metadata) {
        validatePassword(password);
        if (accounts.usernameExists(username)) throw AuthException.usernameTaken();
        long now = clock.millis();
        User user = users.insertUser(new User(optional(email), optional(firstName), optional(lastName), now));
        Account account = accounts.insertAccount(new Account(user.getId(), username, passwordEncoder.encode(password), now));
        return new AuthResult(UserProfile.of(user, account), issueTokens(account, metadata));
    }

    @Transactional
    public AuthResult login(String username, String password, SessionMetadata metadata) {
        Account account = accounts.lockAccountByUsername(username).orElse(null);
        String hash = account == null || account.getPasswordHash() == null ? dummyPasswordHash : account.getPasswordHash();
        // Always perform BCrypt even for unknown usernames or OAuth-only accounts.
        boolean matches = validLoginPassword(password) && passwordEncoder.matches(password, hash);
        if (account == null || account.getPasswordHash() == null || !matches) throw AuthException.invalidCredentials();
        User user = requireActiveUser(account);
        return new AuthResult(UserProfile.of(user, account), issueTokens(account, metadata));
    }

    @Transactional
    public TokenPair refresh(String refreshToken, SessionMetadata metadata) {
        long userId = jwtService.validateRefreshToken(refreshToken);
        Account account = accounts.lockAccount(userId).orElseThrow(AuthException::invalidRefreshToken);
        requireActiveUser(account);
        UserSession previous = sessions.lockSessionByHash(jwtService.hashRefreshToken(refreshToken))
                .orElseThrow(AuthException::invalidRefreshToken);
        long now = clock.millis();
        if (previous.getUserId() != userId || !previous.isActive(now)) throw AuthException.invalidRefreshToken();
        previous.revoke(now);
        sessions.saveSession(previous);
        SessionMetadata nextMetadata = new SessionMetadata(
                metadata.deviceName() == null ? previous.getDeviceName() : metadata.deviceName(),
                metadata.ipAddress(), metadata.userAgent());
        return issueTokens(account, nextMetadata);
    }

    @Transactional
    public void logout(long authenticatedUserId, String refreshToken) {
        long tokenUserId = jwtService.validateRefreshToken(refreshToken);
        if (tokenUserId != authenticatedUserId) throw AuthException.invalidRefreshToken();
        accounts.lockAccount(authenticatedUserId).orElseThrow(AuthException::invalidRefreshToken);
        UserSession session = sessions.lockSessionByHash(jwtService.hashRefreshToken(refreshToken))
                .orElseThrow(AuthException::invalidRefreshToken);
        if (session.getUserId() != authenticatedUserId) throw AuthException.invalidRefreshToken();
        session.revoke(clock.millis());
        sessions.saveSession(session);
    }

    @Transactional
    public void logoutAll(long userId) {
        accounts.lockAccount(userId).orElseThrow(AuthException::invalidAccessToken);
        sessions.revokeAll(userId, clock.millis());
    }

    @Transactional(readOnly = true)
    public UserProfile me(long userId) {
        Account account = accounts.findAccountByUserId(userId).orElseThrow(AuthException::invalidAccessToken);
        return UserProfile.of(requireActiveUser(account), account);
    }

    private User requireActiveUser(Account account) {
        User user = users.findUserById(account.getUserId()).orElseThrow(AuthException::accountDisabled);
        if (!account.isActive() || user.isDeleted()) throw AuthException.accountDisabled();
        return user;
    }

    private TokenPair issueTokens(Account account, SessionMetadata metadata) {
        var access = jwtService.createAccessToken(account.getUserId(), account.getRole());
        var refresh = jwtService.createRefreshToken(account.getUserId());
        sessions.saveSession(new UserSession(account.getUserId(), jwtService.hashRefreshToken(refresh.value()),
                metadata, clock.millis(), refresh.expiresAt()));
        return new TokenPair(access.value(), refresh.value(), access.expiresAt(), refresh.expiresAt());
    }

    private void validatePassword(String password) {
        if (!validLoginPassword(password) || password.length() < 8) throw AuthException.invalidPassword();
    }

    private boolean validLoginPassword(String password) {
        return password != null && !password.isBlank() && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    private String optional(String value) { return value == null || value.isBlank() ? null : value.strip(); }
}
