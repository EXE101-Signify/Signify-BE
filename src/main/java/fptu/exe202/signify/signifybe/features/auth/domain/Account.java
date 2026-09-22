package fptu.exe202.signify.signifybe.features.auth.domain;

import jakarta.persistence.*;

@Entity
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
        this.status = "ACTIVE";
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public String getStatus() { return status; }
    public void ban(long now) {
        this.status = "BANNED";
        this.updatedAt = now;
    }
    public boolean isActive() { return "ACTIVE".equals(status); }
}
