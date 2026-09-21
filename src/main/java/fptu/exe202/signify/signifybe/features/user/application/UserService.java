package fptu.exe202.signify.signifybe.features.user.application;

import fptu.exe202.signify.signifybe.features.auth.application.AuthResult;
import fptu.exe202.signify.signifybe.features.auth.domain.Account;
import fptu.exe202.signify.signifybe.features.auth.domain.SessionMetadata;
import fptu.exe202.signify.signifybe.features.user.domain.User;
import fptu.exe202.signify.signifybe.features.user.domain.UserProfile;
import fptu.exe202.signify.signifybe.features.auth.domain.exception.AuthException;
import fptu.exe202.signify.signifybe.features.auth.application.port.out.AccountRepository;
import fptu.exe202.signify.signifybe.features.auth.application.TokenService;
import fptu.exe202.signify.signifybe.features.user.application.port.out.UserRepository;
import fptu.exe202.signify.signifybe.features.user.domain.exception.UserException;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Clock;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final AccountRepository accounts;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final Clock clock;
    public UserService(AccountRepository accounts, UserRepository users, PasswordEncoder passwordEncoder,
                       TokenService tokenService, Clock clock) {
        this.accounts = accounts;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.clock = clock;
    }

    @Transactional
    public AuthResult register(String username, String password, String email, String firstName,
                               String lastName, SessionMetadata metadata) {
        validatePassword(password);
        if (accounts.usernameExists(username)) throw UserException.usernameTaken();
        long now = clock.millis();
        User user = users.insertUser(new User(optional(email), optional(firstName), optional(lastName), now));
        Account account = accounts.insertAccount(new Account(user.getId(), username, passwordEncoder.encode(password), now));
        return new AuthResult(UserProfile.of(user, account), tokenService.issueTokens(account, metadata));
    }

    @Transactional(readOnly = true)
    public UserProfile me(long userId) {
        Account account = accounts.findAccountByUserId(userId).orElseThrow(AuthException::invalidAccessToken);
        User user = users.findUserById(userId).orElseThrow(AuthException::accountDisabled);
        if (!account.isActive() || user.isDeleted()) throw AuthException.accountDisabled();
        return UserProfile.of(user, account);
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank() || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) throw UserException.invalidPassword();
    }
    private String optional(String value) { return value == null || value.isBlank() ? null : value.strip(); }
}
