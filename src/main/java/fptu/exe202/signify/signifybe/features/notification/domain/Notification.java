package fptu.exe202.signify.signifybe.features.notification.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "notification_id")
    @SequenceGenerator(name = "notification_id", sequenceName = "notification_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Column(length = 255)
    private String title;

    @Column(columnDefinition = "text")
    private String content;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "read_at")
    private Long readAt;

    @Column(name = "deduplication_key", length = 180, unique = true)
    private String deduplicationKey;

    protected Notification() { }

    public Notification(long userId, NotificationType type, String title, String content,
                        String referenceType, Long referenceId, String deduplicationKey, long createdAt) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.content = content;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.deduplicationKey = deduplicationKey;
        this.createdAt = createdAt;
    }

    public void markRead(long now) {
        if (read) return;
        read = true;
        readAt = now;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public NotificationType getType() { return type; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getReferenceType() { return referenceType; }
    public Long getReferenceId() { return referenceId; }
    public boolean isRead() { return read; }
    public Long getCreatedAt() { return createdAt; }
    public Long getReadAt() { return readAt; }
    public String getDeduplicationKey() { return deduplicationKey; }
}
