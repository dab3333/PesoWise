package ph.pesowise.planning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A savings goal.
 *
 * <p>Deliberately stores no running total: the amount saved is {@code SUM(contributions)}, computed
 * on read. A debt keeps a balance column because it has an invariant to protect — you cannot pay
 * more than you owe. A goal has no such bound, since saving more than you targeted is a good outcome
 * rather than an error, so a stored total would be duplication with nothing to guard.
 *
 * <p>"Achieved" is likewise not stored — it is simply {@code saved >= target}.
 */
@Entity
@Table(name = "goals")
public class Goal {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "target_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "target_date")
    private LocalDate targetDate;

    /** Hides the goal without deleting its contribution history. */
    @Column(nullable = false)
    private boolean archived;

    @Column(length = 255)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Goal() {
        // for JPA
    }

    public static Goal create(
            UUID userId, String name, BigDecimal targetAmount, LocalDate targetDate, String note) {
        Goal goal = new Goal();
        goal.id = UUID.randomUUID();
        goal.userId = userId;
        goal.name = name;
        goal.targetAmount = targetAmount;
        goal.targetDate = targetDate;
        goal.note = note;
        goal.archived = false;
        goal.createdAt = Instant.now();
        goal.updatedAt = goal.createdAt;
        return goal;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    /** Reconstructs a goal from an export file with its original id and timestamps. */
    public static Goal restore(
            UUID id, UUID userId, String name, BigDecimal targetAmount, LocalDate targetDate,
            boolean archived, String note, Instant createdAt, Instant updatedAt) {
        Goal goal = new Goal();
        goal.id = id;
        goal.userId = userId;
        goal.name = name;
        goal.targetAmount = targetAmount;
        goal.targetDate = targetDate;
        goal.archived = archived;
        goal.note = note;
        goal.createdAt = createdAt;
        goal.updatedAt = updatedAt;
        return goal;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getTargetAmount() {
        return targetAmount;
    }

    /**
     * The target is editable, unlike a debt's principal: revising what you are saving for is normal,
     * and it invalidates nothing — contributions stay exactly as recorded.
     */
    public void setTargetAmount(BigDecimal targetAmount) {
        this.targetAmount = targetAmount;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public void setTargetDate(LocalDate targetDate) {
        this.targetDate = targetDate;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
