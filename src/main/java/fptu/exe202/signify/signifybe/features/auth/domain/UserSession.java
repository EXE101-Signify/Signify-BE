package fptu.exe202.signify.signifybe.features.auth.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "user_sessions")
public class UserSession {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "auth_sessions")
    @SequenceGenerator(name = "auth_sessions", sequenceName = "auth_user_sessions_id_seq", allocationSize = 1)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "refresh_token_hash", nullable = false, length = 255)
    private String refreshTokenHash;
    @Column(name = "device_name", length = 255)
    private String deviceName;
    @Column(name = "ip_address", length = 100)
    private String ipAddress;
    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;
    @Column(name = "created_at", nullable = false)
    private long createdAt;
    @Column(name = "expires_at", nullable = false)
    private long expiresAt;
    @Column(name = "revoked_at")
    private Long revokedAt;

    protected UserSession() { }

    public UserSession(long userId, String refreshTokenHash, SessionMetadata metadata, long now, long expiresAt) {
        this.userId = userId;
        this.refreshTokenHash = refreshTokenHash;
        this.deviceName = metadata.deviceName();
        this.ipAddress = metadata.ipAddress();
        this.userAgent = metadata.userAgent();
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getRefreshTokenHash() { return refreshTokenHash; }
    public String getDeviceName() { return deviceName; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public long getExpiresAt() { return expiresAt; }
    public Long getRevokedAt() { return revokedAt; }
    public boolean isActive(long now) { return revokedAt == null && expiresAt > now; }
    public void revoke(long now) { if (revokedAt == null) revokedAt = now; }
}
