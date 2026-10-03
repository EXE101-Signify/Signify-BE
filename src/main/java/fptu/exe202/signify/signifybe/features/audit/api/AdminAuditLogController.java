package fptu.exe202.signify.signifybe.features.audit.api;

import tools.jackson.databind.ObjectMapper;
import fptu.exe202.signify.apiresponse.response.ApiResponse;
import fptu.exe202.signify.signifybe.features.audit.application.AuditLogService;
import fptu.exe202.signify.signifybe.features.audit.domain.AdminAuditAction;
import fptu.exe202.signify.signifybe.features.audit.domain.AdminAuditLog;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminAuditLogController {
    AuditLogService auditLogs;

    ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<ApiResponse<AuditLogPage>> list(
            @RequestParam(required = false) @Positive Long adminId,
            @RequestParam(required = false) AdminAuditAction action,
            @RequestParam(required = false) @Size(max = 50) String targetType,
            @RequestParam(required = false) @Positive Long targetId,
            @RequestParam(required = false) @Positive Long fromTime,
            @RequestParam(required = false) @Positive Long toTime,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(AuditLogService.MAX_PAGE_SIZE) int size) {
        Page<AdminAuditLog> result = auditLogs.list(adminId, action, targetType, targetId, fromTime, toTime, page, size);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(AuditLogPage.of(result, objectMapper)));
    }

    public record AuditLogResponse(long id, long adminId, AdminAuditAction action, String targetType,
                                   Long targetId, String reason, Map<String, Object> metadata, long createdAt) {
        static AuditLogResponse of(AdminAuditLog log, ObjectMapper objectMapper) {
            return new AuditLogResponse(log.getId(), log.getAdminId(), log.getAction(), log.getTargetType(),
                    log.getTargetId(), log.getReason(), log.getMetadata(), log.getCreatedAt());
        }
    }

    public record AuditLogPage(List<AuditLogResponse> content, int page, int size,
                               long totalElements, int totalPages) {
        static AuditLogPage of(Page<AdminAuditLog> page, ObjectMapper objectMapper) {
            return new AuditLogPage(page.getContent().stream()
                    .map(log -> AuditLogResponse.of(log, objectMapper)).toList(),
                    page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }
}
