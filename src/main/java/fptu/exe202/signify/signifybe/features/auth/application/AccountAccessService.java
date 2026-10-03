package fptu.exe202.signify.signifybe.features.auth.application;

import fptu.exe202.signify.signifybe.features.auth.application.port.out.AccountRepository;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.exception.AuthException;
import fptu.exe202.signify.signifybe.features.user.application.port.out.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AccountAccessService {

    AccountRepository accounts;

    UserRepository users;

    @Transactional(readOnly = true)
    public CurrentUser requireActiveUser(long userId, long sessionId) {
        var account = accounts.findAccountByUserId(userId).orElseThrow(AuthException::invalidAccessToken);
        var user = users.findUserById(userId).orElseThrow(AuthException::invalidAccessToken);
        if (!account.isActive() || user.isDeleted() || account.getRole() == null) {
            throw AuthException.invalidAccessToken();
        }
        // Never authorize using a stale role from a previously issued token.
        return new CurrentUser(userId, sessionId, account.getRole());
    }
}
