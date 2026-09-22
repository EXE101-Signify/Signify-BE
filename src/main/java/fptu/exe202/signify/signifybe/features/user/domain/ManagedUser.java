package fptu.exe202.signify.signifybe.features.user.domain;

import fptu.exe202.signify.signifybe.features.auth.domain.Account;

public record ManagedUser(UserProfile profile, String status, Long createdAt, Long updatedAt, Long deletedAt) {
    public static ManagedUser of(User user, Account account) {
        return new ManagedUser(UserProfile.of(user, account), account.getStatus(),
                user.getCreatedAt(), user.getUpdatedAt(), user.getDeletedAt());
    }
}
