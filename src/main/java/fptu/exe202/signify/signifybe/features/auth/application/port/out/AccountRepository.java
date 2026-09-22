package fptu.exe202.signify.signifybe.features.auth.application.port.out;

import fptu.exe202.signify.signifybe.features.auth.domain.Account;
import java.util.Optional;

public interface AccountRepository {
    boolean usernameExists(String username);
    Optional<Account> lockAccountByUsername(String username);
    Optional<Account> findAccountByUserId(long userId);
    Optional<Account> lockAccount(long userId);
    Account insertAccount(Account account);
}
