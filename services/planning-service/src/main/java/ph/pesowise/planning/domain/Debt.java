package ph.pesowise.planning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

@Entity
@Table(name = "debts")
public class Debt {

    /** Both directions matter: informal lending is most of what "utang" means in practice. */
    public enum Direction {
        OWED_BY_ME, OWED_TO_ME
    }

    public enum Status {
        ACTIVE, SETTLED
    }

    /** Null means no interest accrues — the original, still-supported "recorded only" mode. */
    public enum InterestMethod {
        /** Accrues on outstanding principal only. */
        SIMPLE,
        /** Accrues on principal plus whatever interest is still unpaid. */
        COMPOUND
    }

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12, updatable = false)
    private Direction direction;

    @Column(length = 80)
    private String counterparty;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal principal;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance;

    /** Meaningless without {@link #interestMethod}; recorded-only when that is null. */
    @Column(name = "interest_rate", precision = 6, scale = 3)
    private BigDecimal interestRate;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    // STRING, not ORDINAL: an ordinal would silently remap every row if a value were ever
    // inserted into the enum.
    @Enumerated(EnumType.STRING)
    @Column(name = "interest_method", length = 10)
    private InterestMethod interestMethod;

    @Column(name = "accrued_interest", nullable = false, precision = 15, scale = 2)
    private BigDecimal accruedInterest;

    @Column(name = "interest_paid_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal interestPaidTotal;

    /** Null until the first accrual; names the last month already accrued, not the next one. */
    @Column(name = "last_accrued_on")
    private LocalDate lastAccruedOn;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    protected Debt() {
        // for JPA
    }

    public static Debt create(
            UUID userId,
            String name,
            Direction direction,
            String counterparty,
            BigDecimal principal,
            BigDecimal interestRate,
            InterestMethod interestMethod,
            LocalDate startDate,
            LocalDate dueDate) {
        Debt debt = new Debt();
        debt.id = UUID.randomUUID();
        debt.userId = userId;
        debt.name = name;
        debt.direction = direction;
        debt.counterparty = counterparty;
        debt.principal = principal;
        // A new debt is wholly outstanding.
        debt.balance = principal;
        debt.interestRate = interestRate;
        debt.interestMethod = interestMethod;
        debt.startDate = startDate;
        debt.accruedInterest = BigDecimal.ZERO;
        debt.interestPaidTotal = BigDecimal.ZERO;
        debt.dueDate = dueDate;
        debt.status = Status.ACTIVE;
        debt.createdAt = Instant.now();
        debt.updatedAt = debt.createdAt;
        return debt;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    /** Reconstructs a debt from an export file with its original id, timestamps, and accrual state. */
    public static Debt restore(
            UUID id, UUID userId, String name, Direction direction, String counterparty, BigDecimal principal,
            BigDecimal balance, BigDecimal interestRate, LocalDate startDate, InterestMethod interestMethod,
            BigDecimal accruedInterest, BigDecimal interestPaidTotal, LocalDate lastAccruedOn, LocalDate dueDate,
            Status status, Instant createdAt, Instant updatedAt, Instant settledAt) {
        Debt debt = new Debt();
        debt.id = id;
        debt.userId = userId;
        debt.name = name;
        debt.direction = direction;
        debt.counterparty = counterparty;
        debt.principal = principal;
        debt.balance = balance;
        debt.interestRate = interestRate;
        debt.startDate = startDate;
        debt.interestMethod = interestMethod;
        debt.accruedInterest = accruedInterest;
        debt.interestPaidTotal = interestPaidTotal;
        debt.lastAccruedOn = lastAccruedOn;
        debt.dueDate = dueDate;
        debt.status = status;
        debt.createdAt = createdAt;
        debt.updatedAt = updatedAt;
        debt.settledAt = settledAt;
        return debt;
    }

    /**
     * Applies a payment already split into its principal and interest parts — interest first,
     * per {@link #allocate}. The caller has already checked the total fits, which is what keeps
     * both columns inside their CHECK constraints.
     */
    public void applyPayment(BigDecimal principalPart, BigDecimal interestPart) {
        this.balance = this.balance.subtract(principalPart);
        this.accruedInterest = this.accruedInterest.subtract(interestPart);
        this.interestPaidTotal = this.interestPaidTotal.add(interestPart);

        if (this.balance.signum() == 0 && this.accruedInterest.signum() == 0) {
            this.status = Status.SETTLED;
            this.settledAt = Instant.now();
        }
    }

    /** Reverses a payment, reopening the debt if it had been settled. */
    public void reversePayment(BigDecimal principalPart, BigDecimal interestPart) {
        this.balance = this.balance.add(principalPart);
        this.accruedInterest = this.accruedInterest.add(interestPart);
        this.interestPaidTotal = this.interestPaidTotal.subtract(interestPart);

        if (this.status == Status.SETTLED && (this.balance.signum() > 0 || this.accruedInterest.signum() > 0)) {
            this.status = Status.ACTIVE;
            this.settledAt = null;
        }
    }

    /**
     * Splits a payment amount into its interest and principal parts — interest first, so a
     * partial payment always clears what's owed for time before it touches what's owed in
     * principal.
     */
    public BigDecimal[] allocate(BigDecimal amount) {
        BigDecimal interestPart = amount.min(accruedInterest);
        BigDecimal principalPart = amount.subtract(interestPart);
        return new BigDecimal[] {principalPart, interestPart};
    }

    public boolean hasInterest() {
        return interestMethod != null;
    }

    public BigDecimal totalOutstanding() {
        return balance.add(accruedInterest);
    }

    /**
     * The month this debt is next due to accrue for — not the next calendar month, but the
     * month after the last one actually accrued (or the debt's start month, before its first
     * accrual ever runs).
     */
    public LocalDate nextAccrualPeriod() {
        LocalDate anchor = lastAccruedOn == null ? startDate : lastAccruedOn.plusMonths(1);
        return YearMonth.from(anchor).atDay(1);
    }

    /**
     * An accrual period is due once its whole month has elapsed — the pass that runs on the 1st
     * accrues for the month that just ended, never the one still in progress.
     */
    public boolean isAccrualDueOn(LocalDate today) {
        return hasInterest() && YearMonth.from(nextAccrualPeriod()).isBefore(YearMonth.from(today));
    }

    /**
     * The interest this debt would accrue for its next due period, at today's rate and balance —
     * a pure calculation, with no side effect. {@code r = rate / 100 / 12}; SIMPLE accrues on
     * outstanding principal only, COMPOUND on principal plus whatever interest is still unpaid.
     */
    public BigDecimal calculateAccrual() {
        BigDecimal base = interestMethod == InterestMethod.COMPOUND ? balance.add(accruedInterest) : balance;
        BigDecimal monthlyRate = interestRate.divide(HUNDRED, 10, RoundingMode.HALF_UP)
                .divide(TWELVE, 10, RoundingMode.HALF_UP);
        return base.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
    }

    /** Records a computed accrual and advances the cursor past the period it was for. */
    public void applyAccrual(BigDecimal amount, LocalDate period) {
        this.accruedInterest = this.accruedInterest.add(amount);
        this.lastAccruedOn = period;
    }

    /** Moves the cursor past an already-recorded period without accruing it a second time. */
    public void advanceAccrualCursor(LocalDate period) {
        this.lastAccruedOn = period;
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

    public Direction getDirection() {
        return direction;
    }

    public String getCounterparty() {
        return counterparty;
    }

    public void setCounterparty(String counterparty) {
        this.counterparty = counterparty;
    }

    public BigDecimal getPrincipal() {
        return principal;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public void setInterestRate(BigDecimal interestRate) {
        this.interestRate = interestRate;
    }

    public InterestMethod getInterestMethod() {
        return interestMethod;
    }

    public void setInterestMethod(InterestMethod interestMethod) {
        this.interestMethod = interestMethod;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public BigDecimal getAccruedInterest() {
        return accruedInterest;
    }

    public BigDecimal getInterestPaidTotal() {
        return interestPaidTotal;
    }

    public LocalDate getLastAccruedOn() {
        return lastAccruedOn;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getSettledAt() {
        return settledAt;
    }
}
