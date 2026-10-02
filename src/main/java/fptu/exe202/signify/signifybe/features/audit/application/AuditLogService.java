package fptu.exe202.signify.signifybe.features.audit.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fptu.exe202.signify.apiresponse.exception.BadRequestException;
import fptu.exe202.signify.signifybe.features.audit.domain.AdminAuditAction;
import fptu.exe202.signify.signifybe.features.audit.domain.AdminAuditLog;
import fptu.exe202.signify.signifybe.features.audit.infrastructure.persistence.JpaAdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditLogService {
    public static final int MAX_PAGE_SIZE = 100;

    private final JpaAdminAuditLogRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public AdminAuditLog record(long adminId, AdminAuditAction action, String targetType,
                                Long targetId, String reason, Map<String, ?> metadata) {
        String serializedMetadata = serialize(metadata);
        return repository.save(new AdminAuditLog(adminId, action, targetType, targetId,
                reason, serializedMetadata, clock.millis()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public Page<AdminAuditLog> list(Long adminId, AdminAuditAction action, String targetType,
                                    Long targetId, Long fromTime, Long toTime, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("Invalid pagination");
        }
        if (fromTime != null && toTime != null && fromTime > toTime) {
            throw new BadRequestException("fromTime must not be greater than toTime");
        }
        String normalizedTargetType = targetType == null || targetType.isBlank()
                ? null : targetType.strip().toUpperCase();
        return repository.search(adminId, action, normalizedTargetType, targetId, fromTime, toTime,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
    }

    private String serialize(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Audit metadata could not be serialized", exception);
        }
    }
}
