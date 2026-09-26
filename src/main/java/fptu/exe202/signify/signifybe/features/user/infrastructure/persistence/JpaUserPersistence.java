package fptu.exe202.signify.signifybe.features.user.infrastructure.persistence;

import fptu.exe202.signify.signifybe.features.user.application.port.out.UserRepository;
import fptu.exe202.signify.signifybe.features.user.domain.User;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JpaUserPersistence implements UserRepository {
    JpaUserRepository jpaUserRepository;

    @Override
    public Optional<User> lockUserById(long userId) { return jpaUserRepository.lockById(userId); }

    @Override
    public boolean emailExistsForOtherUser(String email, long userId) {
        return jpaUserRepository.existsByEmailAndIdNot(email, userId);
    }

    @Override
    public Page<User> searchUsers(String search, Pageable pageable) {
        return jpaUserRepository.searchUsers(search, pageable);
    }

    @Override
    public Optional<User> findUserById(long userId) {
        return jpaUserRepository.findById(userId);
    }

    @Override
    public User addUser(User user) {
        return jpaUserRepository.saveAndFlush(user);
    }

    @Override
    public boolean isEmailExist(String email) {
        return jpaUserRepository.existsByEmail(email);
    }

    @Override
    public Optional<User> findUserByEmail(String email) {
        return jpaUserRepository.findByEmail(email);
    }
}

