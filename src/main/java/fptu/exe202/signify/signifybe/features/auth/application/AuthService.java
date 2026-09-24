package fptu.exe202.signify.signifybe.features.auth.application;

import fptu.exe202.signify.signifybe.common.UserValidation;
import fptu.exe202.signify.signifybe.features.auth.application.port.out.AccountRepository;
import fptu.exe202.signify.signifybe.features.auth.application.port.out.UserSessionRepository;
import fptu.exe202.signify.signifybe.features.auth.domain.Account;
import fptu.exe202.signify.signifybe.features.auth.domain.SessionMetadata;
import fptu.exe202.signify.signifybe.features.auth.domain.UserSession;
import fptu.exe202.signify.signifybe.features.user.application.port.out.UserRepository;
import fptu.exe202.signify.signifybe.features.user.domain.User;
import fptu.exe202.signify.signifybe.features.user.domain.UserProfile;
import fptu.exe202.signify.signifybe.features.auth.domain.exception.AuthException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class AuthService {
    private final AccountRepository accounts;
    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenService tokenService;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthService(AccountRepository accounts, UserRepository users, UserSessionRepository sessions,
                       PasswordEncoder passwordEncoder, JwtService jwtService, Clock clock, TokenService tokenService) {
        this.accounts = accounts;
        this.users = users;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
        this.tokenService = tokenService;
        this.dummyPasswordHash = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResult login(String username, String password, SessionMetadata metadata) {
        Account account = accounts.lockAccountByUsername(username).orElse(null);
        String hash = account == null || account.getPasswordHash() == null ? dummyPasswordHash : account.getPasswordHash();
        // Always perform BCrypt even for unknown usernames or OAuth-only accounts.
        boolean matches = UserValidation.isValidLoginPassword(password) && passwordEncoder.matches(password, hash);
        if (account == null || account.getPasswordHash() == null || !matches) throw AuthException.invalidCredentials();
        User user = requireActiveUser(account);
        return new AuthResult(UserProfile.of(user, account), tokenService.issueTokens(account, metadata));
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
        return tokenService.issueTokens(account, nextMetadata);
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

    private User requireActiveUser(Account account) {
        User user = users.findUserById(account.getUserId()).orElseThrow(AuthException::accountDisabled);
        if (!account.isActive() || user.isDeleted()) throw AuthException.accountDisabled();
        return user;
    }

}
