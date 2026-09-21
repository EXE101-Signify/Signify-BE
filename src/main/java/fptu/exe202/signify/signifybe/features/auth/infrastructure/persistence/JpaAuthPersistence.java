package fptu.exe202.signify.signifybe.features.auth.infrastructure.persistence;

import fptu.exe202.signify.signifybe.auth.application.port.out.*;
import fptu.exe202.signify.signifybe.auth.domain.*;
import fptu.exe202.signify.signifybe.features.auth.application.port.out.AccountRepository;
import fptu.exe202.signify.signifybe.features.auth.application.port.out.UserSessionRepository;
import fptu.exe202.signify.signifybe.features.auth.domain.Account;
import fptu.exe202.signify.signifybe.features.auth.domain.UserSession;
import fptu.exe202.signify.signifybe.features.user.domain.exception.UserException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaAuthPersistence implements AccountRepository, UserSessionRepository {
    private final JpaAccountRepository accounts;
    private final JpaUserSessionRepository sessions;

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
    public Optional<UserSession> lockSessionByHash(String hash) {
        return sessions.lockByHash(hash);
    }

    @Override
    public void saveSession(UserSession session) {
        sessions.save(session);
    }

    @Override
    public void revokeAll(long userId, long now) {
        sessions.revokeAll(userId, now);
    }
}
