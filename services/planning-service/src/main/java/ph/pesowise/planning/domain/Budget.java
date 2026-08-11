package ph.pesowise.planning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * A spending limit for one category in one month.
 *
 * <p>Holds no "spent" figure. What has actually been spent is read live from ledger-service on
 * every request, so there is nothing here that can fall out of step with the transactions.
 */
@Entity
@Table(name = "budgets")
public class Budget {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "category_id", nullable = false, updatable = false)
    private UUID categoryId;

    /** Always the first of the month; the day carries no meaning. */
    @Column(name = "period_month", nullable = false, updatable = false)
    private LocalDate periodMonth;

    @Column(name = "limit_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal limitAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Budget() {
        // for JPA
    }

    public static Budget create(UUID userId, UUID categoryId, YearMonth month, BigDecimal limitAmount) {
        Budget budget = new Budget();
        budget.id = UUID.randomUUID();
        budget.userId = userId;
        budget.categoryId = categoryId;
        budget.periodMonth = month.atDay(1);
        budget.limitAmount = limitAmount;
        budget.createdAt = Instant.now();
        budget.updatedAt = budget.createdAt;
        return budget;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public YearMonth getMonth() {
        return YearMonth.from(periodMonth);
    }

    public BigDecimal getLimitAmount() {
        return limitAmount;
    }

    public void setLimitAmount(BigDecimal limitAmount) {
        this.limitAmount = limitAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
