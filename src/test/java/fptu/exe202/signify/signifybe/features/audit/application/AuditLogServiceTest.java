package fptu.exe202.signify.signifybe.features.audit.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import fptu.exe202.signify.apiresponse.exception.BadRequestException;
import fptu.exe202.signify.signifybe.features.audit.domain.AdminAuditAction;
import fptu.exe202.signify.signifybe.features.audit.domain.AdminAuditLog;
import fptu.exe202.signify.signifybe.features.audit.infrastructure.persistence.JpaAdminAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {
    @Mock JpaAdminAuditLogRepository repository;
    private AuditLogService service;

    @BeforeEach
    void setUp() {
        service = new AuditLogService(repository, new ObjectMapper(),
                Clock.fixed(Instant.ofEpochMilli(2_000L), ZoneOffset.UTC));
    }

    @Test
    void recordNormalizesReasonAndSerializesMetadata() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.record(1L, AdminAuditAction.BAN_USER, "USER", 2L, "  policy violation  ",
                Map.of("before", Map.of("status", "ACTIVE")));

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(repository).save(captor.capture());
        AdminAuditLog saved = captor.getValue();
        assertEquals("policy violation", saved.getReason());
        assertEquals(2_000L, saved.getCreatedAt());
        assertTrue(saved.getMetadata().contains("\"status\":\"ACTIVE\""));
    }

    @Test
    void listRejectsInvertedTimeRange() {
        assertThrows(BadRequestException.class,
                () -> service.list(null, null, null, null, 2_000L, 1_000L, 0, 20));
        verifyNoInteractions(repository);
    }
}
