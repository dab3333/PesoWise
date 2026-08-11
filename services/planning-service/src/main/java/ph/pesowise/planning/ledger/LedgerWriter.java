package ph.pesowise.planning.ledger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ph.pesowise.planning.ledger.LedgerDtos.SourceType;
import ph.pesowise.planning.ledger.LedgerDtos.SourcedTransactionRequest;
import ph.pesowise.planning.ledger.LedgerDtos.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The one place this service writes money to the ledger.
 *
 * <p>Debt payments, goal contributions and posted recurring bills all follow the same pattern, and
 * the ordering rule below is subtle enough that it should be stated once rather than repeated at
 * each call site.
 *
 * <p><strong>Ordering.</strong> Callers must invoke {@link #post} from inside their own
 * {@code @Transactional} method, <em>before</em> it commits. A failure here then throws, Spring
 * rolls the local changes back, and the outcome is "nothing happened" — which is safe to retry. The
 * reverse order would risk a local balance change with no matching transaction, which reads to the
 * user as money that vanished.
 *
 * <p>One window remains open: if this call succeeds and the caller's transaction then fails to
 * commit, the ledger keeps a transaction with nothing behind it. That orphan is discoverable because
 * every row carries {@code source_type} and {@code source_id} — which is what those columns are for.
 * A single-user app does not warrant a saga to close a window this narrow, but it is a real
 * limitation rather than an oversight.
 */
@Component
public class LedgerWriter {

    private static final Logger log = LoggerFactory.getLogger(LedgerWriter.class);

    private final LedgerClient ledger;

    public LedgerWriter(LedgerClient ledger) {
        this.ledger = ledger;
    }

    /**
     * Records the cash movement and returns the ledger transaction id to store locally.
     *
     * @param categoryId chosen by the user, and the thing that decides income versus expense in the
     *                   ledger — so the direction can never contradict the record here
     * @param sourceId   the debt, goal or bill this movement belongs to
     * @return the new transaction's id, or null if the ledger returned no body
     */
    public UUID post(
            UUID userId,
            SourceType sourceType,
            UUID sourceId,
            UUID accountId,
            UUID categoryId,
            BigDecimal amount,
            LocalDate date,
            String note) {

        Transaction created = ledger.createSourcedTransaction(userId, new SourcedTransactionRequest(
                accountId, categoryId, amount, date, note, sourceType, sourceId));

        UUID transactionId = created == null ? null : created.id();
        log.info("Posted {} to the ledger as {} for source {} (txn {})",
                amount, sourceType, sourceId, transactionId);
        return transactionId;
    }

    /**
     * Removes a transaction this service created, when the payment or contribution behind it is
     * undone. Leaving it in place would make the two records disagree, which is worse than either
     * outcome alone.
     *
     * @param ledgerTxnId tolerated as null, so callers need not guard for a payment recorded before
     *                    the id was captured
     */
    public void remove(UUID userId, UUID ledgerTxnId) {
        if (ledgerTxnId == null) return;
        ledger.deleteTransaction(userId, ledgerTxnId);
        log.info("Removed ledger transaction {}", ledgerTxnId);
    }
}
