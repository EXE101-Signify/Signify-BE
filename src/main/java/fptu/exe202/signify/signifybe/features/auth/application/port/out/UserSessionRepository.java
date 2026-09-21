package fptu.exe202.signify.signifybe.features.auth.application.port.out;

import fptu.exe202.signify.signifybe.features.auth.domain.UserSession;
import java.util.Optional;

public interface UserSessionRepository {
    /** Caller must lock the account first, then the session, in that order. */
    Optional<UserSession> lockSessionByHash(String hash);
    void saveSession(UserSession session);
    void revokeAll(long userId, long now);
}
