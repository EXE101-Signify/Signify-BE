package fptu.exe202.signify.signifybe.features.audit.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import fptu.exe202.signify.apiresponse.response.ApiResponse;
import fptu.exe202.signify.signifybe.features.audit.application.AuditLogService;
import fptu.exe202.signify.signifybe.features.audit.domain.AdminAuditAction;
import fptu.exe202.signify.signifybe.features.audit.domain.AdminAuditLog;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditLogController {
    private final AuditLogService auditLogs;
    private final ObjectMapper objectMapper;

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
                                   Long targetId, String reason, JsonNode metadata, long createdAt) {
        static AuditLogResponse of(AdminAuditLog log, ObjectMapper objectMapper) {
            return new AuditLogResponse(log.getId(), log.getAdminId(), log.getAction(), log.getTargetType(),
                    log.getTargetId(), log.getReason(), parseMetadata(log.getMetadata(), objectMapper), log.getCreatedAt());
        }

        private static JsonNode parseMetadata(String metadata, ObjectMapper objectMapper) {
            if (metadata == null) return null;
            try {
                return objectMapper.readTree(metadata);
            } catch (JsonProcessingException exception) {
                return TextNode.valueOf(metadata);
            }
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
