package fptu.exe202.signify.signifybe.features.auth.infrastructure.persistence;

import fptu.exe202.signify.signifybe.features.auth.application.port.out.AccountRepository;
import fptu.exe202.signify.signifybe.features.auth.application.port.out.UserSessionRepository;
import fptu.exe202.signify.signifybe.features.auth.domain.Account;
import fptu.exe202.signify.signifybe.features.auth.domain.UserSession;
import fptu.exe202.signify.signifybe.features.user.domain.exception.UserException;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JpaAuthPersistence implements AccountRepository, UserSessionRepository {

    JpaAccountRepository accounts;

    JpaUserSessionRepository sessions;

    public JpaAuthPersistence(JpaAccountRepository accounts, JpaUserSessionRepository sessions) {
        this.accounts = accounts;
        this.sessions = sessions;
    }

    @Override
    public boolean usernameExists(String username) {
        return accounts.existsByUsername(username);
    }

    @Override
    public Optional<Account> lockAccountByUsername(String username) {
        return accounts.lockByUsername(username);
    }

    @Override
    public Optional<Account> findAccountByUserId(long userId) {
        return accounts.findById(userId);
    }

    @Override
    public Optional<Account> lockAccount(long userId) {
        return accounts.lockByUserId(userId);
    }

    @Override
    public Account insertAccount(Account account) {
        try {
            // Flush here so concurrent uniqueness violations become a safe API error inside the transaction.
            return accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            throw UserException.registrationConflict();
        }
    }

    @Override
    public boolean isPasswordExisted(String password) {
        return accounts.existsByPasswordHash(password);
    }

    @Override
    public List<String> listPassword() {
        return accounts.getAllPasswordHash();
    }

    @Override
    public Optional<UserSession> lockSessionByHash(String hash) {
        return sessions.lockByHash(hash);
    }

    @Override
    public UserSession saveSession(UserSession session) {
        return sessions.saveAndFlush(session);
    }

    @Override
    public boolean isActiveSession(long sessionId, long userId, long now) {
        return sessions.existsByIdAndUserIdAndRevokedAtIsNullAndExpiresAtGreaterThan(sessionId, userId, now);
    }

    @Override
    public void revokeAll(long userId, long now) {
        sessions.revokeAll(userId, now);
    }
}
