package ph.pesowise.planning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One month's interest accrual on one debt — the claim row behind the idempotency guard in
 * {@code ux_debt_accrual_period}, exactly the role {@code RecurringRun} plays for bills. No
 * ledger transaction ever points at this: interest owed is not money that has moved.
 */
@Entity
@Table(name = "debt_interest_accruals")
public class DebtInterestAccrual {

    @Id
    private UUID id;

    @Column(name = "debt_id", nullable = false, updatable = false)
    private UUID debtId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private LocalDate period;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "balance_at_accrual", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal balanceAtAccrual;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DebtInterestAccrual() {
        // for JPA
    }

    public static DebtInterestAccrual claim(
            UUID userId, UUID debtId, LocalDate period, BigDecimal amount, BigDecimal balanceAtAccrual) {
        DebtInterestAccrual accrual = new DebtInterestAccrual();
        accrual.id = UUID.randomUUID();
        accrual.debtId = debtId;
        accrual.userId = userId;
        accrual.period = period;
        accrual.amount = amount;
        accrual.balanceAtAccrual = balanceAtAccrual;
        accrual.createdAt = Instant.now();
        return accrual;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDebtId() {
        return debtId;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getPeriod() {
        return period;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAtAccrual() {
        return balanceAtAccrual;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
