package fptu.exe202.signify.signifybe.features.user.application.port.out;


import fptu.exe202.signify.signifybe.features.user.domain.User;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserRepository {
    Optional<User> findUserById(long userId);
    User addUser(User user);
    boolean isEmailExist(String email);
    Optional<User> lockUserById(long userId);
    boolean emailExistsForOtherUser(String email, long userId);
    Page<User> searchUsers(String search, Pageable pageable);
}
