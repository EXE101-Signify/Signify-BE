package fptu.exe202.signify.signifybe.features.chat.application;

import fptu.exe202.signify.signifybe.features.user.infrastructure.persistence.JpaUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PresenceStateService {
    private final JpaUserRepository users;

    @Transactional
    public void online(long userId) { users.markOnline(userId); }

    @Transactional
    public void offline(long userId, long at) { users.markOffline(userId, at); }
}
