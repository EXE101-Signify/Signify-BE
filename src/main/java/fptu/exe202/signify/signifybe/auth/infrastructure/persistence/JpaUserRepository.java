package fptu.exe202.signify.signifybe.auth.infrastructure.persistence;

import fptu.exe202.signify.signifybe.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaUserRepository extends JpaRepository<User, Long> { }
