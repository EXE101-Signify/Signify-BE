package fptu.exe202.signify.signifybe.features.auth.application;
import fptu.exe202.signify.signifybe.features.auth.application.port.out.UserSessionRepository;
import fptu.exe202.signify.signifybe.features.auth.domain.Account;
import fptu.exe202.signify.signifybe.features.auth.domain.SessionMetadata;
import fptu.exe202.signify.signifybe.features.auth.domain.UserSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;

@Service
public class TokenService {
    private final UserSessionRepository sessions;
    private final JwtService jwtService;
    private final Clock clock;
    public TokenService(UserSessionRepository sessions, JwtService jwtService, Clock clock) {
        this.sessions = sessions;
        this.jwtService = jwtService;
        this.clock = clock;
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public TokenPair issueTokens(Account account, SessionMetadata metadata) {
        var refresh = jwtService.createRefreshToken(account.getUserId());
        UserSession session = sessions.saveSession(new UserSession(account.getUserId(),
                jwtService.hashRefreshToken(refresh.value()), metadata, clock.millis(), refresh.expiresAt()));
        if (session.getId() == null || session.getId() <= 0) {
            throw new IllegalStateException("Persisted session must have a positive ID before issuing access token");
        }
        var access = jwtService.createAccessToken(account.getUserId(), session.getId(),
                account.getUsername(), account.getRole());
        return new TokenPair(access.value(), refresh.value(), access.expiresAt(), refresh.expiresAt());
    }

}
