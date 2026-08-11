package ph.pesowise.planning.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.pesowise.planning.api.DebtDtos.DebtOverview;
import ph.pesowise.planning.api.DebtDtos.DebtRequest;
import ph.pesowise.planning.api.DebtDtos.DebtResponse;
import ph.pesowise.planning.api.DebtDtos.DebtUpdateRequest;
import ph.pesowise.planning.api.DebtDtos.PaymentRequest;
import ph.pesowise.planning.api.DebtDtos.PaymentResponse;
import ph.pesowise.planning.domain.Debt;
import ph.pesowise.planning.domain.DebtPayment;
import ph.pesowise.planning.ledger.LedgerClient;
import ph.pesowise.planning.ledger.LedgerDtos.SourceType;
import ph.pesowise.planning.ledger.LedgerDtos.SourcedTransactionRequest;
import ph.pesowise.planning.ledger.LedgerDtos.Transaction;
import ph.pesowise.planning.repo.DebtPaymentRepository;
import ph.pesowise.planning.repo.DebtRepository;
import ph.pesowise.planning.web.BadRequestException;
import ph.pesowise.planning.web.ConflictException;
import ph.pesowise.planning.web.NotFoundException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Debt tracking, in both directions.
 *
 * <p>The interesting part is {@link #recordPayment}: a payment has to appear in two places — the
 * debt balance here, and the cash movement in the ledger. Money itself is only ever recorded in the
 * ledger; this service keeps a pointer to that transaction.
 */
@Service
public class DebtService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static final Logger log = LoggerFactory.getLogger(DebtService.class);

    private final DebtRepository debts;
    private final DebtPaymentRepository payments;
    private final LedgerClient ledger;

    public DebtService(DebtRepository debts, DebtPaymentRepository payments, LedgerClient ledger) {
        this.debts = debts;
        this.payments = payments;
        this.ledger = ledger;
    }

    @Transactional(readOnly = true)
    public DebtOverview list(UUID userId) {
        List<Debt> found = debts.findByUserIdOrderByStatusAscDueDateAscCreatedAtAsc(userId);

        BigDecimal owedByMe = BigDecimal.ZERO;
        BigDecimal owedToMe = BigDecimal.ZERO;
        List<DebtResponse> responses = new java.util.ArrayList<>(found.size());

        for (Debt debt : found) {
            responses.add(toResponse(debt, payments.countByDebtId(debt.getId())));

            // Settled debts contribute nothing to the outstanding totals.
            if (debt.getStatus() == Debt.Status.ACTIVE) {
                if (debt.getDirection() == Debt.Direction.OWED_BY_ME) {
                    owedByMe = owedByMe.add(debt.getBalance());
                } else {
                    owedToMe = owedToMe.add(debt.getBalance());
                }
            }
        }

        return new DebtOverview(owedByMe, owedToMe, owedToMe.subtract(owedByMe), responses);
    }

    @Transactional
    public DebtResponse create(UUID userId, DebtRequest request) {
        Debt debt = debts.save(Debt.create(
                userId,
                request.name().trim(),
                request.direction(),
                trimToNull(request.counterparty()),
                request.principal(),
                request.interestRate(),
                request.dueDate()));

        return toResponse(debt, 0);
    }

    /**
     * Updates the descriptive fields only. Principal and direction are fixed: changing either would
     * silently invalidate every payment already recorded against the debt.
     */
    @Transactional
    public DebtResponse update(UUID userId, UUID debtId, DebtUpdateRequest request) {
        Debt debt = require(userId, debtId);

        debt.setName(request.name().trim());
        debt.setCounterparty(trimToNull(request.counterparty()));
        debt.setInterestRate(request.interestRate());
        debt.setDueDate(request.dueDate());

        return toResponse(debt, payments.countByDebtId(debtId));
    }

    /**
     * Deletes the debt and its payment records.
     *
     * <p>The ledger transactions those payments created are deliberately <em>kept</em>: the money
     * really did move, and deleting the record of it would silently rewrite the user's spending
     * history. They remain as ordinary transactions.
     */
    @Transactional
    public void delete(UUID userId, UUID debtId) {
        // ON DELETE CASCADE removes the payment rows.
        debts.delete(require(userId, debtId));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> payments(UUID userId, UUID debtId) {
        require(userId, debtId);
        return payments.findByDebtIdOrderByPaidOnDescCreatedAtDesc(debtId).stream()
                .map(PaymentResponse::from)
                .toList();
    }

    /**
     * Records a payment: reduces the balance here <em>and</em> writes the cash movement to the
     * ledger, so a debt payment shows up in spending reports instead of living in a silo.
     *
     * <p><strong>Ordering.</strong> The ledger is called inside this transaction, before the commit.
     * If that call fails, the exception rolls back the balance change — so the failure mode is
     * "nothing happened", which is recoverable by retrying. The reverse order would risk a reduced
     * balance with no matching transaction, which looks like money that vanished.
     *
     * <p>The remaining window is small but real: if the ledger write succeeds and this transaction
     * then fails to commit, the ledger keeps a transaction with no payment behind it. That orphan is
     * discoverable precisely because the ledger stores {@code source_type} and {@code source_id} —
     * which is what those columns are for. A single-user app does not warrant a saga to close a
     * window this narrow, but it is a real limitation rather than an oversight.
     */
    @Transactional
    public PaymentResponse recordPayment(UUID userId, UUID debtId, PaymentRequest request) {
        Debt debt = require(userId, debtId);

        if (debt.getStatus() == Debt.Status.SETTLED) {
            throw new ConflictException("\"%s\" is already settled.".formatted(debt.getName()));
        }
        // Rejected rather than clamped: an overpayment usually means a typo, and silently
        // absorbing it would hide the mistake.
        if (request.amount().compareTo(debt.getBalance()) > 0) {
            throw new BadRequestException(
                    "That is more than the ₱%s still outstanding.".formatted(debt.getBalance().toPlainString()));
        }

        // Paying a debt is money out; being repaid is money in. The category the client chose
        // carries the direction in the ledger, so the two cannot disagree.
        Transaction ledgerTxn = ledger.createSourcedTransaction(userId, new SourcedTransactionRequest(
                request.accountId(),
                request.categoryId(),
                request.amount(),
                request.paidOn(),
                noteFor(debt, request),
                SourceType.DEBT_PAYMENT,
                debt.getId()));

        DebtPayment payment = payments.save(DebtPayment.create(
                userId, debt.getId(), request.amount(), request.paidOn(),
                trimToNull(request.note()), ledgerTxn == null ? null : ledgerTxn.id()));

        debt.applyPayment(request.amount());

        log.info("Recorded {} against debt {} (ledger txn {})",
                request.amount(), debt.getId(), payment.getLedgerTxnId());

        return PaymentResponse.from(payment);
    }

    /**
     * Reverses a payment: restores the balance, reopens the debt if it had been settled, and removes
     * the ledger transaction the payment created — otherwise the cash movement would linger and the
     * two records would disagree.
     */
    @Transactional
    public void deletePayment(UUID userId, UUID debtId, UUID paymentId) {
        Debt debt = require(userId, debtId);

        DebtPayment payment = payments.findByIdAndUserId(paymentId, userId)
                .filter(found -> found.getDebtId().equals(debtId))
                .orElseThrow(() -> new NotFoundException("Payment"));

        if (payment.getLedgerTxnId() != null) {
            ledger.deleteTransaction(userId, payment.getLedgerTxnId());
        }

        payments.delete(payment);
        debt.reversePayment(payment.getAmount());
    }

    private Debt require(UUID userId, UUID debtId) {
        return debts.findByIdAndUserId(debtId, userId)
                .orElseThrow(() -> new NotFoundException("Debt"));
    }

    /** Gives the ledger transaction a note that explains itself in the transaction list. */
    private static String noteFor(Debt debt, PaymentRequest request) {
        String own = trimToNull(request.note());
        String label = debt.getDirection() == Debt.Direction.OWED_BY_ME
                ? "Payment for " + debt.getName()
                : "Repayment from " + (debt.getCounterparty() == null ? debt.getName() : debt.getCounterparty());

        return own == null ? label : label + " — " + own;
    }

    private static DebtResponse toResponse(Debt debt, long paymentCount) {
        BigDecimal paid = debt.getPrincipal().subtract(debt.getBalance());
        BigDecimal percentPaid = debt.getPrincipal().signum() == 0
                ? BigDecimal.ZERO
                : paid.multiply(HUNDRED).divide(debt.getPrincipal(), 1, RoundingMode.HALF_UP);

        Long daysUntilDue = debt.getDueDate() == null
                ? null
                : ChronoUnit.DAYS.between(LocalDate.now(), debt.getDueDate());

        return new DebtResponse(
                debt.getId(), debt.getName(), debt.getDirection(), debt.getCounterparty(),
                debt.getPrincipal(), debt.getBalance(), paid, percentPaid,
                debt.getInterestRate(), debt.getDueDate(), daysUntilDue,
                // A settled debt is never overdue, however long ago its due date was.
                daysUntilDue != null && daysUntilDue < 0 && debt.getStatus() == Debt.Status.ACTIVE,
                debt.getStatus(), (int) paymentCount);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
