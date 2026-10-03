package fptu.exe202.signify.signifybe.features.user.domain;

import fptu.exe202.signify.signifybe.common.UserValidation;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "auth_users")
    @SequenceGenerator(name = "auth_users", sequenceName = "auth_users_id_seq", allocationSize = 1)
    @Column(name = "user_id")
    private Long id;
    @Column(name = "first_name", length = UserValidation.NAME_MAX_LENGTH)
    private String firstName;
    @Column(name = "last_name", length = UserValidation.NAME_MAX_LENGTH)
    private String lastName;
    @Column(name = "full_name", length = UserValidation.FULL_NAME_MAX_LENGTH)
    private String fullName;
    @Column(length = UserValidation.EMAIL_MAX_LENGTH)
    private String email;
    @Column(columnDefinition = "text")
    private String avatar;
    @Column(length = UserValidation.PHONE_MAX_LENGTH)
    private String phone;
    private Boolean gender;
    @Column(length = UserValidation.ADDRESS_MAX_LENGTH)
    private String address;
    @Column(name = "email_verified")
    private Boolean emailVerified;
    @Column(name = "is_updated_profile", nullable = false)
    private boolean updatedProfile;
    @Column(name = "created_at")
    private Long createdAt;
    @Column(name = "updated_at")
    private Long updatedAt;
    @Column(name = "deleted_at")
    private Long deletedAt;
    @Column(name = "is_online", nullable = false)
    private boolean online;
    @Column(name = "last_seen_at")
    private Long lastSeenAt;

    protected User() { }

    public User(String email, String firstName, String lastName, long now) {
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.fullName = UserValidation.formatFullName(firstName, lastName);
        this.emailVerified = false;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean isEmailVerified() { return Boolean.TRUE.equals(emailVerified); }
    public boolean isDeleted() { return deletedAt != null; }

    public void ban(long now) {
        if (deletedAt != null) return;
        deletedAt = now;
        updatedAt = now;
    }

    public void restore(long now) {
        if (deletedAt == null) return;
        deletedAt = null;
        updatedAt = now;
    }
}
