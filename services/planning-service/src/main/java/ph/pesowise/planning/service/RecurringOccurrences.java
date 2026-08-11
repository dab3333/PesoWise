package ph.pesowise.planning.service;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ph.pesowise.planning.domain.RecurringBill;
import ph.pesowise.planning.domain.RecurringRun;
import ph.pesowise.planning.ledger.LedgerDtos.SourceType;
import ph.pesowise.planning.ledger.LedgerWriter;
import ph.pesowise.planning.repo.RecurringBillRepository;
import ph.pesowise.planning.repo.RecurringRunRepository;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Settles a single occurrence of a recurring bill.
 *
 * <p>A separate bean on purpose. {@link RecurringService} loops over many bills and needs each one in
 * its own transaction, so one failure cannot roll back the bills already handled. A
 * {@code REQUIRES_NEW} method called from within the same class would be a plain Java call — the
 * proxy is bypassed and the annotation silently does nothing. Crossing a bean boundary is what makes
 * it real.
 *
 * <p><strong>Not charging twice is the whole problem here.</strong> The scheduler runs on a timer and
 * a container restart re-triggers it, so two guards protect each occurrence:
 *
 * <ol>
 *   <li><b>Claim first.</b> A {@code recurring_runs} row is inserted <em>before</em> the ledger is
 *       called, with a unique index on {@code (bill_id, due_date)}. A duplicate fails on the insert,
 *       before any money is written. Posting first would charge twice and only then discover the
 *       clash.</li>
 *   <li><b>The ledger's own index.</b> If the ledger write succeeds and this transaction then fails
 *       to commit, the claim rolls back and guard 1 is gone — so the ledger also refuses a second
 *       transaction for the same bill and date, answering 409. That is treated as "already recorded"
 *       rather than an error, because retrying could never succeed.</li>
 * </ol>
 */
@Service
public class RecurringOccurrences {

    private static final Logger log = LoggerFactory.getLogger(RecurringOccurrences.class);

    public enum Settlement {
        /** The occurrence was recorded by this call. */
        POSTED,
        /** Already dealt with — the idempotency guard doing its job, not a failure. */
        ALREADY_RECORDED,
        /** The bill is not due, or no longer exists. */
        NOT_DUE
    }

    public record Result(Settlement settlement, RecurringRun run) {
        static Result of(Settlement settlement) {
            return new Result(settlement, null);
        }
    }

    private final RecurringBillRepository bills;
    private final RecurringRunRepository runs;
    private final LedgerWriter ledger;

    public RecurringOccurrences(RecurringBillRepository bills, RecurringRunRepository runs, LedgerWriter ledger) {
        this.bills = bills;
        this.runs = runs;
        this.ledger = ledger;
    }

    /**
     * Records the bill's current occurrence and advances its cursor, in its own transaction.
     *
     * @param today the date the pass is running for; a bill due later than this is left alone
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result settleDue(UUID billId, LocalDate today) {
        RecurringBill bill = bills.findById(billId).orElse(null);
        if (bill == null || !bill.isDueOn(today)) return Result.of(Settlement.NOT_DUE);

        LocalDate dueDate = bill.getNextRunDate();

        RecurringRun run;
        try {
            run = runs.saveAndFlush(RecurringRun.claim(bill.getUserId(), bill.getId(), dueDate));
        } catch (DataIntegrityViolationException e) {
            log.debug("Occurrence {} of bill {} was already recorded", dueDate, billId);
            // Move the cursor on regardless, or the scheduler would retry this occurrence forever.
            bill.advance();
            return Result.of(Settlement.ALREADY_RECORDED);
        }

        try {
            run.recordTransaction(ledger.post(
                    bill.getUserId(), SourceType.RECURRING_BILL, bill.getId(),
                    bill.getAccountId(), bill.getCategoryId(), bill.getAmount(), dueDate,
                    noteFor(bill)));
        } catch (FeignException.Conflict e) {
            // The ledger already holds this occurrence, from an attempt whose commit failed. The
            // claim above is the durable record now, so keep it rather than failing a retry that
            // could never succeed.
            log.warn("Ledger already held occurrence {} of bill {}; keeping the claim", dueDate, billId);
        }

        bill.advance();
        return new Result(Settlement.POSTED, run);
    }

    /**
     * Marks the current occurrence dealt with without posting anything — "I did not pay this one".
     * Its own transaction, for symmetry with {@link #settleDue}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result skipDue(UUID billId, LocalDate today) {
        RecurringBill bill = bills.findById(billId).orElse(null);
        if (bill == null || !bill.isDueOn(today)) return Result.of(Settlement.NOT_DUE);

        LocalDate dueDate = bill.getNextRunDate();

        RecurringRun run;
        try {
            run = runs.saveAndFlush(RecurringRun.skip(bill.getUserId(), bill.getId(), dueDate));
        } catch (DataIntegrityViolationException e) {
            bill.advance();
            return Result.of(Settlement.ALREADY_RECORDED);
        }

        bill.advance();
        return new Result(Settlement.POSTED, run);
    }

    private static String noteFor(RecurringBill bill) {
        return bill.getNote() == null ? bill.getName() : bill.getName() + " — " + bill.getNote();
    }
}
