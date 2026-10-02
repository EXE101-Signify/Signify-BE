package fptu.exe202.signify.signifybe.features.user.application;

import fptu.exe202.signify.signifybe.common.UserValidation;
import fptu.exe202.signify.apiresponse.exception.ConflictException;
import fptu.exe202.signify.signifybe.features.audit.application.AuditLogService;
import fptu.exe202.signify.signifybe.features.audit.domain.AdminAuditAction;
import fptu.exe202.signify.signifybe.features.auth.application.port.out.AccountRepository;
import fptu.exe202.signify.signifybe.features.auth.application.port.out.UserSessionRepository;
import fptu.exe202.signify.signifybe.features.auth.domain.Account;
import fptu.exe202.signify.signifybe.features.auth.domain.exception.AuthException;
import fptu.exe202.signify.signifybe.features.user.api.dto.UpdateProfileRequest;
import fptu.exe202.signify.signifybe.features.user.application.port.out.UserRepository;
import fptu.exe202.signify.signifybe.features.user.domain.*;
import fptu.exe202.signify.signifybe.features.user.domain.exception.UserException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserManagementService {
    private final AccountRepository accounts;
    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final AuditLogService auditLogs;
    private final Clock clock;

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public Page<ManagedUser> list(String search, int page, int size) {
        if (page < 0 || size < 1 || size > UserValidation.PAGE_MAX_SIZE) throw new IllegalArgumentException("Invalid pagination");
        return users.searchUsers(search == null ? "" : search.strip(), PageRequest.of(page, size, Sort.by("id")))
                .map(user -> ManagedUser.of(user, accounts.findAccountByUserId(user.getId()).orElseThrow(UserException::notFound)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public ManagedUser get(long userId) {
        Account account = accounts.findAccountByUserId(userId).orElseThrow(UserException::notFound);
        User user = users.findUserById(userId).orElseThrow(UserException::notFound);
        return ManagedUser.of(user, account);
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN') and principal.userId == #userId")
    @Transactional
    public UserProfile updateOwn(long userId, UpdateProfileRequest request) {
        return update(userId, request).profile();
    }

    @PreAuthorize("hasRole('ADMIN') and principal.userId == #actorId")
    @Transactional
    public ManagedUser updateByAdmin(long actorId, long userId, UpdateProfileRequest request, String reason) {
        Account account = accounts.lockAccount(userId).orElseThrow(UserException::notFound);
        User user = users.lockUserById(userId).orElseThrow(UserException::notFound);
        Map<String, Object> before = userSnapshot(user);
        ManagedUser result = updateLocked(account, user, request);
        auditLogs.record(actorId, AdminAuditAction.UPDATE_USER, "USER", userId, reason,
                changeMetadata(before, userSnapshot(user)));
        return result;
    }

    private ManagedUser update(long userId, UpdateProfileRequest request) {
        // Consistent account -> user locking order, also used by login/refresh/ban.
        Account account = accounts.lockAccount(userId).orElseThrow(UserException::notFound);
        User user = users.lockUserById(userId).orElseThrow(UserException::notFound);
        return updateLocked(account, user, request);
    }

    private ManagedUser updateLocked(Account account, User user, UpdateProfileRequest request) {
        if (!account.isActive() || user.isDeleted()) throw AuthException.accountDisabled();
        if (request.email() != null && !Objects.equals(request.email(), user.getEmail())) {
            if (users.emailExistsForOtherUser(request.email(), user.getId())) {
                throw new ConflictException("Email already exists");
            }
            user.setEmail(request.email());
            user.setEmailVerified(false);
        }
        if (request.firstName() != null) user.setFirstName(request.firstName().strip());
        if (request.lastName() != null) user.setLastName(request.lastName().strip());
        if (request.firstName() != null || request.lastName() != null) {
            user.setFullName(UserValidation.formatFullName(user.getFirstName(), user.getLastName()));
        }
        if (request.avatar() != null) user.setAvatar(request.avatar().isEmpty() ? null : request.avatar());
        if (request.phone() != null) user.setPhone(request.phone().isEmpty() ? null : request.phone().strip());
        if (request.gender() != null) user.setGender(request.gender());
        if (request.address() != null) user.setAddress(request.address().isEmpty() ? null : request.address().strip());
        user.setUpdatedProfile(true);
        user.setUpdatedAt(clock.millis());
        // The locked entity is managed by JPA. Dirty checking issues UPDATE, never INSERT.
        // Do not map this request into a new User or call the registration/addUser path.
        return ManagedUser.of(user, account);
    }

    @PreAuthorize("hasRole('ADMIN') and principal.userId == #actorId")
    @Transactional
    public ManagedUser ban(long actorId, long userId, String reason) {
        if (actorId == userId) throw UserException.cannotBanSelf();
        Account account = accounts.lockAccount(userId).orElseThrow(UserException::notFound);
        User user = users.lockUserById(userId).orElseThrow(UserException::notFound);
        Map<String, Object> before = lifecycleSnapshot(account, user);
        long now = clock.millis();
        user.ban(now);
        account.ban(now);
        sessions.revokeAll(userId, now);
        ManagedUser result = ManagedUser.of(user, account);
        auditLogs.record(actorId, AdminAuditAction.BAN_USER, "USER", userId, reason,
                changeMetadata(before, lifecycleSnapshot(account, user)));
        return result;
    }

    @PreAuthorize("hasRole('ADMIN') and principal.userId == #actorId")
    @Transactional
    public ManagedUser unban(long actorId, long userId, String reason) {
        Account account = accounts.lockAccount(userId).orElseThrow(UserException::notFound);
        User user = users.lockUserById(userId).orElseThrow(UserException::notFound);
        Map<String, Object> before = lifecycleSnapshot(account, user);
        long now = clock.millis();
        account.activate(now);
        user.restore(now);
        ManagedUser result = ManagedUser.of(user, account);
        auditLogs.record(actorId, AdminAuditAction.UNBAN_USER, "USER", userId, reason,
                changeMetadata(before, lifecycleSnapshot(account, user)));
        return result;
    }

    private Map<String, Object> userSnapshot(User user) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("email", user.getEmail());
        snapshot.put("firstName", user.getFirstName());
        snapshot.put("lastName", user.getLastName());
        snapshot.put("fullName", user.getFullName());
        snapshot.put("avatar", user.getAvatar());
        snapshot.put("phone", user.getPhone());
        snapshot.put("gender", user.getGender());
        snapshot.put("address", user.getAddress());
        snapshot.put("emailVerified", user.getEmailVerified());
        return snapshot;
    }

    private Map<String, Object> lifecycleSnapshot(Account account, User user) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("status", account.getStatus());
        snapshot.put("deletedAt", user.getDeletedAt());
        return snapshot;
    }

    private Map<String, Object> changeMetadata(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("before", before);
        metadata.put("after", after);
        return metadata;
    }
}
