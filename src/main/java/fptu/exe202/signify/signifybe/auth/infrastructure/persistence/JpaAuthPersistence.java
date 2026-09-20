package fptu.exe202.signify.signifybe.auth.infrastructure.persistence;

import fptu.exe202.signify.signifybe.auth.application.port.out.*;
import fptu.exe202.signify.signifybe.auth.domain.*;
import fptu.exe202.signify.signifybe.auth.domain.exception.AuthException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class JpaAuthPersistence implements AccountRepository, UserRepository, UserSessionRepository {
    private final JpaAccountRepository accounts;
    private final JpaUserRepository users;
    private final JpaUserSessionRepository sessions;

    public JpaAuthPersistence(JpaAccountRepository accounts, JpaUserRepository users, JpaUserSessionRepository sessions) {
        this.accounts = accounts;
        this.users = users;
        this.sessions = sessions;
    }

    @Override public boolean usernameExists(String username) { return accounts.existsByUsername(username); }
    @Override public Optional<Account> lockAccountByUsername(String username) { return accounts.lockByUsername(username); }
    @Override public Optional<Account> findAccountByUserId(long userId) { return accounts.findById(userId); }
    @Override public Optional<Account> lockAccount(long userId) { return accounts.lockByUserId(userId); }
    @Override public Optional<User> findUserById(long userId) { return users.findById(userId); }
    @Override public User insertUser(User user) { return users.saveAndFlush(user); }
    @Override public Account insertAccount(Account account) {
        try {
            // Flush here so concurrent uniqueness violations become a safe API error inside the transaction.
            return accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            throw AuthException.registrationConflict();
        }
    }
    @Override public Optional<UserSession> lockSessionByHash(String hash) { return sessions.lockByHash(hash); }
    @Override public void saveSession(UserSession session) { sessions.save(session); }
    @Override public void revokeAll(long userId, long now) { sessions.revokeAll(userId, now); }
}
