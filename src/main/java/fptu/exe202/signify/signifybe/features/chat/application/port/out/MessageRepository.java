package fptu.exe202.signify.signifybe.features.chat.application.port.out;

import fptu.exe202.signify.signifybe.features.chat.domain.Message;
import java.util.Optional;

public interface MessageRepository {
    Message save(Message message);
    Optional<Message> findById(long messageId);
}
