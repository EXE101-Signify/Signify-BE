package fptu.exe202.signify.signifybe.features.user.application;

import fptu.exe202.signify.apiresponse.exception.ConflictException;
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
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserManagementService {
    private final AccountRepository accounts;
    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final Clock clock;

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public Page<ManagedUser> list(String search, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid pagination");
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

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ManagedUser updateByAdmin(long userId, UpdateProfileRequest request) {
        return update(userId, request);
    }

    private ManagedUser update(long userId, UpdateProfileRequest request) {
        // Consistent account -> user locking order, also used by login/refresh/ban.
        Account account = accounts.lockAccount(userId).orElseThrow(UserException::notFound);
        User user = users.lockUserById(userId).orElseThrow(UserException::notFound);
        if (!account.isActive() || user.isDeleted()) throw AuthException.accountDisabled();
        if (request.email() != null && !Objects.equals(request.email(), user.getEmail())) {
            if (users.emailExistsForOtherUser(request.email(), userId)) {
                throw new ConflictException("Email already exists");
            }
            user.setEmail(request.email());
            user.setEmailVerified(false);
        }
        if (request.firstName() != null) user.setFirstName(request.firstName().strip());
        if (request.lastName() != null) user.setLastName(request.lastName().strip());
        if (request.firstName() != null || request.lastName() != null) {
            String fullName = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                    + (user.getLastName() == null ? "" : user.getLastName())).strip();
            user.setFullName(fullName.isEmpty() ? null : fullName.substring(0, Math.min(200, fullName.length())));
        }
        if (request.avatar() != null) user.setAvatar(request.avatar().isEmpty() ? null : request.avatar());
        user.setUpdatedProfile(true);
        user.setUpdatedAt(clock.millis());
        // The locked entity is managed by JPA. Dirty checking issues UPDATE, never INSERT.
        // Do not map this request into a new User or call the registration/addUser path.
        return ManagedUser.of(user, account);
    }

    @PreAuthorize("hasRole('ADMIN') and principal.userId == #actorId")
    @Transactional
    public ManagedUser ban(long actorId, long userId) {
        if (actorId == userId) throw UserException.cannotBanSelf();
        Account account = accounts.lockAccount(userId).orElseThrow(UserException::notFound);
        User user = users.lockUserById(userId).orElseThrow(UserException::notFound);
        long now = clock.millis();
        if (!user.isDeleted()) {
            user.setDeletedAt(now);
            user.setUpdatedAt(now);
        }
        if (!"BANNED".equals(account.getStatus())) account.ban(now);
        sessions.revokeAll(userId, now);
        return ManagedUser.of(user, account);
    }
}
