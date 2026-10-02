package fptu.exe202.signify.signifybe.features.chat.infrastructure.persistence;

import fptu.exe202.signify.signifybe.features.chat.domain.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaMessageRepository extends JpaRepository<Message, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Message m where m.id = :messageId")
    Optional<Message> findByIdForUpdate(@Param("messageId") long messageId);

    List<Message> findByConversationIdAndDeletedFalseOrderByIdDesc(long conversationId, Pageable pageable);
    List<Message> findByConversationIdAndDeletedFalseAndIdLessThanOrderByIdDesc(
            long conversationId, long before, Pageable pageable);
}
