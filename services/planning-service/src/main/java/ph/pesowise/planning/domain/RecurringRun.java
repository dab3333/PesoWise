package ph.pesowise.planning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One occurrence of a recurring bill, already dealt with.
 *
 * <p>This is the idempotency guard. The scheduler runs on a timer and a container restart
 * re-triggers it; without a record of which occurrences are done, a bill would be charged again on
 * every restart. A unique index on {@code (bill_id, due_date)} turns that double-charge into a
 * rejected insert.
 */
@Entity
@Table(name = "recurring_runs")
public class RecurringRun {

    @Id
    private UUID id;

    @Column(name = "bill_id", nullable = false, updatable = false)
    private UUID billId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** The occurrence this row satisfies, identified by its scheduled date. */
    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;

    /** Null when the occurrence was skipped rather than posted. */
    @Column(name = "ledger_txn_id")
    private UUID ledgerTxnId;

    @Column(nullable = false)
    private boolean skipped;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RecurringRun() {
        // for JPA
    }

    /** Claims an occurrence before the ledger is called, so a duplicate fails on the insert. */
    public static RecurringRun claim(UUID userId, UUID billId, LocalDate dueDate) {
        RecurringRun run = new RecurringRun();
        run.id = UUID.randomUUID();
        run.billId = billId;
        run.userId = userId;
        run.dueDate = dueDate;
        run.skipped = false;
        run.createdAt = Instant.now();
        return run;
    }

    public static RecurringRun skip(UUID userId, UUID billId, LocalDate dueDate) {
        RecurringRun run = claim(userId, billId, dueDate);
        run.skipped = true;
        return run;
    }

    public void recordTransaction(UUID ledgerTxnId) {
        this.ledgerTxnId = ledgerTxnId;
    }

    /** Reconstructs a run from an export file with its original id and timestamp. */
    public static RecurringRun restore(
            UUID id, UUID userId, UUID billId, LocalDate dueDate, UUID ledgerTxnId, boolean skipped,
            Instant createdAt) {
        RecurringRun run = new RecurringRun();
        run.id = id;
        run.billId = billId;
        run.userId = userId;
        run.dueDate = dueDate;
        run.ledgerTxnId = ledgerTxnId;
        run.skipped = skipped;
        run.createdAt = createdAt;
        return run;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBillId() {
        return billId;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public UUID getLedgerTxnId() {
        return ledgerTxnId;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
