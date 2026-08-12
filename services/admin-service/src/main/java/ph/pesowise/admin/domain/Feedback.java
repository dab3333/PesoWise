package ph.pesowise.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feedback")
public class Feedback {

    public enum Category {
        BUG, IDEA, OTHER
    }

    public enum Status {
        NEW, REVIEWING, RESOLVED
    }

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "user_email", nullable = false, length = 320, updatable = false)
    private String userEmail;

    @Column(name = "user_name", nullable = false, length = 100, updatable = false)
    private String userName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Category category;

    @Column(nullable = false, length = 150, updatable = false)
    private String subject;

    @Column(nullable = false, updatable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status;

    @Column(name = "admin_note")
    private String adminNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Feedback() {
        // for JPA
    }

    public static Feedback submit(
            UUID userId, String userEmail, String userName,
            Category category, String subject, String message) {
        Feedback feedback = new Feedback();
        feedback.id = UUID.randomUUID();
        feedback.userId = userId;
        feedback.userEmail = userEmail;
        feedback.userName = userName;
        feedback.category = category;
        feedback.subject = subject;
        feedback.message = message;
        feedback.status = Status.NEW;
        feedback.createdAt = Instant.now();
        return feedback;
    }

    /** RESOLVED stamps {@code resolvedAt}; moving away from it clears the stamp. */
    public void changeStatus(Status status, String adminNote) {
        this.status = status;
        this.adminNote = adminNote;
        this.resolvedAt = status == Status.RESOLVED ? Instant.now() : null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getUserName() {
        return userName;
    }

    public Category getCategory() {
        return category;
    }

    public String getSubject() {
        return subject;
    }

    public String getMessage() {
        return message;
    }

    public Status getStatus() {
        return status;
    }

    public String getAdminNote() {
        return adminNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
