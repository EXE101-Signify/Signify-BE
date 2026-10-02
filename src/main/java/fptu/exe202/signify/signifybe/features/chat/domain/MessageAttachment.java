package fptu.exe202.signify.signifybe.features.chat.domain;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "message_attachment")
@Getter
public class MessageAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "attachment_seq")
    @SequenceGenerator(name = "attachment_seq", sequenceName = "attachment_id_seq", allocationSize = 1)
    @Column(name = "attachment_id")
    private Long id;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    /** Private object key; never returned directly to clients. */
    @Column(name = "file_url", nullable = false, columnDefinition = "TEXT")
    private String fileUrl;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    protected MessageAttachment() { }

    public MessageAttachment(long messageId, String key, String filename, String mimeType, long size, long createdAt) {
        this.messageId = messageId;
        this.fileUrl = key;
        this.fileName = filename;
        this.mimeType = mimeType;
        this.fileSize = size;
        this.createdAt = createdAt;
        this.deleted = false;
    }

    public void markDeleted() { this.deleted = true; }
}
