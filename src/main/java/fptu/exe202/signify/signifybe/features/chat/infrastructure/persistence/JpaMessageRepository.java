package fptu.exe202.signify.signifybe.features.chat.infrastructure.persistence;

import fptu.exe202.signify.signifybe.features.chat.domain.Message;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaMessageRepository extends JpaRepository<Message, Long> {
}
