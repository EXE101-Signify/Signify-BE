package fptu.exe202.signify.signifybe.features.user.application;

import fptu.exe202.signify.signifybe.features.audit.application.AuditLogService;
import fptu.exe202.signify.signifybe.features.audit.domain.AdminAuditAction;
import fptu.exe202.signify.signifybe.features.auth.application.port.out.AccountRepository;
import fptu.exe202.signify.signifybe.features.auth.application.port.out.UserSessionRepository;
import fptu.exe202.signify.signifybe.features.auth.domain.Account;
import fptu.exe202.signify.signifybe.features.user.application.port.out.UserRepository;
import fptu.exe202.signify.signifybe.features.user.domain.ManagedUser;
import fptu.exe202.signify.signifybe.features.user.domain.User;
import fptu.exe202.signify.signifybe.features.user.domain.exception.UserException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {
    private static final long NOW = 2_000L;

    @Mock AccountRepository accounts;
    @Mock UserRepository users;
    @Mock UserSessionRepository sessions;
    @Mock AuditLogService auditLogs;

    private UserManagementService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);
        service = new UserManagementService(accounts, users, sessions, auditLogs, clock);
    }

    @Test
    void banSoftDeletesAccountAndRevokesSessions() {
        User user = user(2L, 1_000L);
        Account account = account(2L, 1_000L);
        when(accounts.lockAccount(2L)).thenReturn(Optional.of(account));
        when(users.lockUserById(2L)).thenReturn(Optional.of(user));

        ManagedUser result = service.ban(1L, 2L, "policy violation");

        assertEquals("BANNED", result.status());
        assertEquals(NOW, result.deletedAt());
        assertEquals(NOW, result.updatedAt());
        verify(sessions).revokeAll(2L, NOW);
        verify(auditLogs).record(eq(1L), eq(AdminAuditAction.BAN_USER), eq("USER"), eq(2L),
                eq("policy violation"), anyMap());
    }

    @Test
    void banRejectsSelfBeforeAccessingPersistence() {
        assertThrows(UserException.class, () -> service.ban(1L, 1L, null));

        verifyNoInteractions(accounts, users, sessions, auditLogs);
    }

    @Test
    void repeatedBanPreservesOriginalDeletionTimestamp() {
        User user = user(2L, 1_000L);
        Account account = account(2L, 1_000L);
        user.ban(1_500L);
        account.ban(1_500L);
        when(accounts.lockAccount(2L)).thenReturn(Optional.of(account));
        when(users.lockUserById(2L)).thenReturn(Optional.of(user));

        ManagedUser result = service.ban(1L, 2L, null);

        assertEquals(1_500L, result.deletedAt());
        assertEquals(1_500L, result.updatedAt());
        verify(sessions).revokeAll(2L, NOW);
    }

    @Test
    void unbanRestoresAccountAndUserWithoutRestoringSessions() {
        User user = user(2L, 1_000L);
        Account account = account(2L, 1_000L);
        user.ban(1_500L);
        account.ban(1_500L);
        when(accounts.lockAccount(2L)).thenReturn(Optional.of(account));
        when(users.lockUserById(2L)).thenReturn(Optional.of(user));

        ManagedUser result = service.unban(1L, 2L, "appeal accepted");

        assertEquals("ACTIVE", result.status());
        assertNull(result.deletedAt());
        assertEquals(NOW, result.updatedAt());
        verifyNoInteractions(sessions);
        verify(auditLogs).record(eq(1L), eq(AdminAuditAction.UNBAN_USER), eq("USER"), eq(2L),
                eq("appeal accepted"), anyMap());
    }

    @Test
    void repeatedUnbanPreservesActiveUserTimestamp() {
        User user = user(2L, 1_000L);
        Account account = account(2L, 1_000L);
        when(accounts.lockAccount(2L)).thenReturn(Optional.of(account));
        when(users.lockUserById(2L)).thenReturn(Optional.of(user));

        ManagedUser result = service.unban(1L, 2L, null);

        assertEquals("ACTIVE", result.status());
        assertNull(result.deletedAt());
        assertEquals(1_000L, result.updatedAt());
        verifyNoInteractions(sessions);
    }

    @Test
    void unbanMissingUserReturnsNotFound() {
        Account account = account(2L, 1_000L);
        when(accounts.lockAccount(2L)).thenReturn(Optional.of(account));
        when(users.lockUserById(2L)).thenReturn(Optional.empty());

        assertThrows(UserException.class, () -> service.unban(1L, 2L, null));

        verifyNoInteractions(sessions);
    }

    private User user(long id, long now) {
        User user = new User("user@example.com", "Test", "User", now);
        user.setId(id);
        return user;
    }

    private Account account(long userId, long now) {
        return new Account(userId, "user" + userId, "hash", now);
    }
}
