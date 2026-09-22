package fptu.exe202.signify.signifybe.features.user.infrastructure.persistence;

import fptu.exe202.signify.signifybe.features.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

@Repository
public interface JpaUserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);
    boolean existsByEmailAndIdNot(String email, long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId")
    Optional<User> lockById(@Param("userId") long userId);

    @Query("""
            select u from User u join Account a on a.userId = u.id
            where :search = '' or lower(a.username) like lower(concat('%', :search, '%'))
                or lower(u.email) like lower(concat('%', :search, '%'))
                or lower(u.fullName) like lower(concat('%', :search, '%'))
            """)
    Page<User> searchUsers(@Param("search") String search, Pageable pageable);
}
