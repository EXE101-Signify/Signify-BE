package fptu.exe202.signify.signifybe.features.auth.application;

import fptu.exe202.signify.signifybe.features.auth.application.port.out.AccountRepository;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.exception.AuthException;
import fptu.exe202.signify.signifybe.features.user.application.port.out.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountAccessService {
    private final AccountRepository accounts;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public CurrentUser requireActiveUser(long userId) {
        var account = accounts.findAccountByUserId(userId).orElseThrow(AuthException::invalidAccessToken);
        var user = users.findUserById(userId).orElseThrow(AuthException::invalidAccessToken);
        if (!account.isActive() || user.isDeleted() || account.getRole() == null) {
            throw AuthException.invalidAccessToken();
        }
        // Never authorize using a stale role from a previously issued token.
        return new CurrentUser(userId, account.getRole());
    }
}
