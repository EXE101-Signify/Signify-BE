package fptu.exe202.signify.signifybe.features.chat.application.port.out;

import fptu.exe202.signify.signifybe.features.chat.domain.Message;

public interface MessageRepository {
    Message save(Message message);
}
