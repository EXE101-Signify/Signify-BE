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
        var access = jwtService.createAccessToken(account.getUserId(), account.getUsername(), account.getRole());
        var refresh = jwtService.createRefreshToken(account.getUserId());
        sessions.saveSession(new UserSession(account.getUserId(), jwtService.hashRefreshToken(refresh.value()),
                metadata, clock.millis(), refresh.expiresAt()));
        return new TokenPair(access.value(), refresh.value(), access.expiresAt(), refresh.expiresAt());
    }

}
