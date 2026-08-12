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

    // Nullable and never asked of accounts created before this field existed — collected at
    // registration purely for the personalization features that will read it later, not
    // anything the current app depends on.
    public enum Gender {
        MALE,
        FEMALE,
        UNSPECIFIED
    }

    public enum Occupation {
        STUDENT,
        EMPLOYED_PRIVATE,
        EMPLOYED_GOVERNMENT,
        SELF_EMPLOYED,
        BUSINESS_OWNER,
        OFW,
        UNEMPLOYED,
        RETIRED,
        OTHER
    }

    @Id
    private UUID id;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "first_name", length = 60)
    private String firstName;

    @Column(name = "last_name", length = 60)
    private String lastName;

    @Column
    private Integer age;

    // STRING, not ORDINAL: the column carries a CHECK constraint on the names, and an ordinal
    // would silently remap every row if a value were ever inserted into the enum.
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Gender gender;

    // STRING, not ORDINAL: the column carries a CHECK constraint on the names, and an ordinal
    // would silently remap every row if a value were ever inserted into the enum.
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Occupation occupation;

    // Only meaningful when occupation == OTHER — the free-text answer for whatever isn't on the
    // fixed list. Left null otherwise rather than repeating the enum's own label there.
    @Column(name = "occupation_other", length = 100)
    private String occupationOther;

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
                 String firstName, String lastName, Integer age, Gender gender,
                 Occupation occupation, String occupationOther,
                 Role role, boolean emailVerified, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.firstName = firstName;
        this.lastName = lastName;
        this.age = age;
        this.gender = gender;
        this.occupation = occupation;
        this.occupationOther = occupationOther;
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
    public static User create(String email, String passwordHash, String firstName, String lastName,
                              Integer age, Gender gender, Occupation occupation, String occupationOther,
                              Role role, boolean emailVerified) {
        String displayName = (firstName + " " + lastName).trim();
        return new User(UUID.randomUUID(), email, passwordHash, displayName,
                firstName, lastName, age, gender, occupation, occupationOther,
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

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public Integer getAge() {
        return age;
    }

    public Gender getGender() {
        return gender;
    }

    public Occupation getOccupation() {
        return occupation;
    }

    public String getOccupationOther() {
        return occupationOther;
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
