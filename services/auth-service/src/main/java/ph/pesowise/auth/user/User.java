package ph.pesowise.auth.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    public enum Role {
        USER,
        ADMIN
    }

    @Id
    private UUID id;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    // STRING, not ORDINAL: the column carries a CHECK constraint on the names, and an ordinal
    // would silently remap every row if a value were ever inserted into the enum.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(nullable = false)
    private boolean disabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
        // for JPA
    }

    private User(UUID id, String email, String passwordHash, String displayName,
                 Role role, boolean emailVerified, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.emailVerified = emailVerified;
        this.disabled = false;
        this.createdAt = createdAt;
    }

    /**
     * @param email must already be normalised (lowercased, trimmed) — the unique index is
     *              case-sensitive, so normalising is what actually prevents duplicate accounts
     * @param emailVerified true only when delivery is switched off, so a developer running the
     *                      stack without an SMTP provider is not locked out of their own account
     */
    public static User create(String email, String passwordHash, String displayName,
                              Role role, boolean emailVerified) {
        return new User(UUID.randomUUID(), email, passwordHash, displayName,
                role, emailVerified, Instant.now());
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    /** One-way: an address that has been proven reachable does not become unproven. */
    public void markEmailVerified() {
        this.emailVerified = true;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
