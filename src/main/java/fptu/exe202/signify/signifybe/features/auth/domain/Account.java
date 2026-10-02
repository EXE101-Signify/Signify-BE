package fptu.exe202.signify.signifybe.features.auth.domain;

import fptu.exe202.signify.signifybe.features.user.api.dto.UserStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "account")

public class Account {
    @Id
    @Column(name = "user_id")
    private Long userId;
    @Column(nullable = false, length = 100)
    private String username;
    @Column(name = "password_hash", length = 255)
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "created_at")
    private Long createdAt;
    @Column(name = "updated_at")
    private Long updatedAt;

    protected Account() { }

    public Account(long userId, String username, String passwordHash, long now) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = Role.USER;
        this.status = UserStatus.ACTIVE.getValue();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void ban(long now) {
        if ("BANNED".equals(status)) return;
        this.status = "BANNED";
        this.updatedAt = now;
    }
    public void activate(long now) {
        if ("ACTIVE".equals(status)) return;
        this.status = "ACTIVE";
        this.updatedAt = now;
    }
    public boolean isActive() { return UserStatus.ACTIVE.getValue().equals(status); }

    public void updatePassword(String newPasswordHash, long now) {
        this.passwordHash = newPasswordHash;
        this.updatedAt = now;
    }
}
