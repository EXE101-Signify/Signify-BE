package fptu.exe202.signify.signifybe.features.user.infrastructure.persistence;

import fptu.exe202.signify.signifybe.features.user.application.port.out.UserRepository;
import fptu.exe202.signify.signifybe.features.user.domain.User;

import java.util.Optional;

@org.springframework.stereotype.Repository
public class JpaUserPersistence implements UserRepository {
    private final JpaUserRepository users;
    public JpaUserPersistence(JpaUserRepository users) { this.users = users; }

    @Override
    public Optional<User> findUserById(long userId) {
        return users.findById(userId);
    }

    @Override
    public User insertUser(User user) {
        return users.saveAndFlush(user);
    }
}
