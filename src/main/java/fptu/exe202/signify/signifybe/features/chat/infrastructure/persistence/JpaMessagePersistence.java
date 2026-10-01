package fptu.exe202.signify.signifybe.features.chat.infrastructure.persistence;

import fptu.exe202.signify.signifybe.features.chat.application.port.out.MessageRepository;
import fptu.exe202.signify.signifybe.features.chat.domain.Message;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JpaMessagePersistence implements MessageRepository {
    JpaMessageRepository jpaRepository;

    @Override
    public Message save(Message message) {
        return jpaRepository.saveAndFlush(message);
    }
}
