package fptu.exe202.signify.signifybe.features.auth.application;

import fptu.exe202.signify.signifybe.features.auth.application.port.out.UserSessionRepository;
import fptu.exe202.signify.signifybe.features.auth.domain.exception.AuthException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SessionAccessService {

    UserSessionRepository sessions;

    Clock clock;

    @Transactional(readOnly = true)
    public void requireActiveSession(long sessionId, long userId) {
        if (sessionId <= 0 || userId <= 0
                || !sessions.isActiveSession(sessionId, userId, clock.millis())) {
            throw AuthException.invalidAccessToken();
        }
    }
}
