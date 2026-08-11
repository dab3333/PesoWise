package ph.pesowise.planning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * A recurring bill: a transaction template plus a cursor.
 *
 * <p>{@code nextRunDate} is the next occurrence not yet satisfied. The scheduler walks it forward,
 * recording each occurrence in {@code recurring_runs}.
 */
@Entity
@Table(name = "recurring_bills")
public class RecurringBill {

    public enum Frequency {
        WEEKLY, MONTHLY, YEARLY
    }

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Frequency frequency;

    /**
     * For MONTHLY, the day of the month the bill falls on. Held separately from
     * {@link #nextRunDate} so a bill due on the 31st returns to the 31st after February, rather than
     * being permanently dragged back to the 28th.
     */
    @Column(name = "day_of_period")
    private Short dayOfPeriod;

    @Column(name = "next_run_date", nullable = false)
    private LocalDate nextRunDate;

    @Column(name = "auto_post", nullable = false)
    private boolean autoPost;

    @Column(nullable = false)
    private boolean active;

    @Column(length = 255)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RecurringBill() {
        // for JPA
    }

    public static RecurringBill create(
            UUID userId,
            String name,
            UUID categoryId,
            UUID accountId,
            BigDecimal amount,
            Frequency frequency,
            LocalDate firstDueDate,
            boolean autoPost,
            String note) {
        RecurringBill bill = new RecurringBill();
        bill.id = UUID.randomUUID();
        bill.userId = userId;
        bill.name = name;
        bill.categoryId = categoryId;
        bill.accountId = accountId;
        bill.amount = amount;
        bill.frequency = frequency;
        // For monthly bills the anchor day comes from the first due date the user picked.
        bill.dayOfPeriod = frequency == Frequency.MONTHLY ? (short) firstDueDate.getDayOfMonth() : null;
        bill.nextRunDate = firstDueDate;
        bill.autoPost = autoPost;
        bill.active = true;
        bill.note = note;
        bill.createdAt = Instant.now();
        bill.updatedAt = bill.createdAt;
        return bill;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    /**
     * Moves the cursor to the occurrence after {@code nextRunDate}.
     *
     * <p>Monthly bills are advanced from the anchor day rather than from the current date, and the
     * day is clamped to the length of the target month. That is what makes a bill due on the 31st
     * land on 28 February and then return to 31 March, instead of drifting to the 28th of every
     * subsequent month.
     */
    public void advance() {
        this.nextRunDate = switch (frequency) {
            case WEEKLY -> nextRunDate.plusWeeks(1);
            case MONTHLY -> {
                YearMonth target = YearMonth.from(nextRunDate).plusMonths(1);
                int anchor = dayOfPeriod == null ? nextRunDate.getDayOfMonth() : dayOfPeriod;
                yield target.atDay(Math.min(anchor, target.lengthOfMonth()));
            }
            // plusYears handles 29 February on a non-leap year by clamping to the 28th.
            case YEARLY -> nextRunDate.plusYears(1);
        };
    }

    public boolean isDueOn(LocalDate date) {
        return active && !nextRunDate.isAfter(date);
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

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Frequency getFrequency() {
        return frequency;
    }

    public Short getDayOfPeriod() {
        return dayOfPeriod;
    }

    public LocalDate getNextRunDate() {
        return nextRunDate;
    }

    /** Rescheduling also re-anchors a monthly bill's day. */
    public void rescheduleTo(LocalDate date) {
        this.nextRunDate = date;
        if (frequency == Frequency.MONTHLY) this.dayOfPeriod = (short) date.getDayOfMonth();
    }

    public boolean isAutoPost() {
        return autoPost;
    }

    public void setAutoPost(boolean autoPost) {
        this.autoPost = autoPost;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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
}
