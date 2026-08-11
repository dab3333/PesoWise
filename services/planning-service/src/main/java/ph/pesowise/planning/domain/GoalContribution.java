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
 * Money put toward a goal. The cash side is a transaction in the ledger database, and
 * {@link #ledgerTxnId} points at it — money is recorded in exactly one place.
 */
@Entity
@Table(name = "goal_contributions")
public class GoalContribution {

    @Id
    private UUID id;

    @Column(name = "goal_id", nullable = false, updatable = false)
    private UUID goalId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "contributed_on", nullable = false, updatable = false)
    private LocalDate contributedOn;

    @Column(length = 255)
    private String note;

    @Column(name = "ledger_txn_id", updatable = false)
    private UUID ledgerTxnId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected GoalContribution() {
        // for JPA
    }

    public static GoalContribution create(
            UUID userId,
            UUID goalId,
            BigDecimal amount,
            LocalDate contributedOn,
            String note,
            UUID ledgerTxnId) {
        GoalContribution contribution = new GoalContribution();
        contribution.id = UUID.randomUUID();
        contribution.goalId = goalId;
        contribution.userId = userId;
        contribution.amount = amount;
        contribution.contributedOn = contributedOn;
        contribution.note = note;
        contribution.ledgerTxnId = ledgerTxnId;
        contribution.createdAt = Instant.now();
        return contribution;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGoalId() {
        return goalId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getContributedOn() {
        return contributedOn;
    }

    public String getNote() {
        return note;
    }

    public UUID getLedgerTxnId() {
        return ledgerTxnId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
