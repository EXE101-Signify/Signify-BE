package fptu.exe202.signify.signifybe.features.auth.application.port.out;

import fptu.exe202.signify.signifybe.features.auth.domain.Account;
import java.util.Optional;

public interface AccountRepository {
    boolean usernameExists(String username);
    Optional<Account> lockAccountByUsername(String username);
    Optional<Account> findAccountByUserId(long userId);
    /** Must be called inside a transaction; serializes this user's session mutations. */
    Optional<Account> lockAccount(long userId);
    Account insertAccount(Account account);
}
