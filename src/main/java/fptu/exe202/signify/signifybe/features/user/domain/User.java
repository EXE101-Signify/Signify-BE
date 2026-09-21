package fptu.exe202.signify.signifybe.features.user.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "auth_users")
    @SequenceGenerator(name = "auth_users", sequenceName = "auth_users_id_seq", allocationSize = 1)
    @Column(name = "user_id")
    private Long id;
    @Column(name = "first_name", length = 100)
    private String firstName;
    @Column(name = "last_name", length = 100)
    private String lastName;
    @Column(name = "full_name", length = 200)
    private String fullName;
    @Column(length = 150)
    private String email;
    @Column(columnDefinition = "text")
    private String avatar;
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

    protected User() { }

    public User(String email, String firstName, String lastName, long now) {
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        String name = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).strip();
        this.fullName = name.isEmpty() ? null : name.substring(0, Math.min(name.length(), 200));
        this.emailVerified = false;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getAvatar() { return avatar; }
    public boolean isEmailVerified() { return Boolean.TRUE.equals(emailVerified); }
    public boolean isDeleted() { return deletedAt != null; }
}
