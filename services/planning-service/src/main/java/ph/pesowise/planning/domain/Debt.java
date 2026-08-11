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

    /** Recorded and displayed only — the MVP does not accrue interest. */
    @Column(name = "interest_rate", precision = 6, scale = 3)
    private BigDecimal interestRate;

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

    /**
     * Applies a payment. The caller has already checked the amount fits, which is what keeps the
     * balance inside its CHECK constraint.
     */
    public void applyPayment(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);

        if (this.balance.signum() == 0) {
            this.status = Status.SETTLED;
            this.settledAt = Instant.now();
        }
    }

    /** Reverses a payment, reopening the debt if it had been settled. */
    public void reversePayment(BigDecimal amount) {
        this.balance = this.balance.add(amount);

        if (this.status == Status.SETTLED && this.balance.signum() > 0) {
            this.status = Status.ACTIVE;
            this.settledAt = null;
        }
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

    public Instant getSettledAt() {
        return settledAt;
    }
}
