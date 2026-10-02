package fptu.exe202.signify.signifybe.features.chat.infrastructure.persistence;

import fptu.exe202.signify.signifybe.features.chat.domain.MessageAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface JpaMessageAttachmentRepository extends JpaRepository<MessageAttachment, Long> {
    List<MessageAttachment> findByMessageIdAndDeletedFalse(long messageId);
}
