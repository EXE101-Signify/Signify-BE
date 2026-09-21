package fptu.exe202.signify.signifybe.features.user.application.port.out;


import fptu.exe202.signify.signifybe.features.user.domain.User;

import java.util.Optional;

public interface UserRepository {
    Optional<User> findUserById(long userId);
    User insertUser(User user);
}
