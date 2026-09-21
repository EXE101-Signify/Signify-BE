package fptu.exe202.signify.signifybe.features.user.infrastructure.persistence;

import fptu.exe202.signify.signifybe.features.user.application.port.out.UserRepository;
import fptu.exe202.signify.signifybe.features.user.domain.User;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JpaUserPersistence implements UserRepository {
    JpaUserRepository users;

    @Override
    public Optional<User> findUserById(long userId) {
        return users.findById(userId);
    }

    @Override
    public User addUser(User user) {
        return users.saveAndFlush(user);
    }
}
