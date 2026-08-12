package ph.pesowise.planning.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ph.pesowise.planning.domain.Debt;
import ph.pesowise.planning.domain.DebtInterestAccrual;
import ph.pesowise.planning.repo.DebtInterestAccrualRepository;
import ph.pesowise.planning.repo.DebtRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Settles a single month's interest accrual on a single debt.
 *
 * <p>A separate bean for the same reason {@link RecurringOccurrences} is: {@link DebtInterestService}
 * loops over many debts and needs each one in its own transaction, so one failure cannot roll back
 * the debts already handled — and a {@code REQUIRES_NEW} method called from within the same class
 * would be a plain Java call, bypassing the proxy that makes the annotation do anything.
 *
 * <p>Unlike a recurring bill, no money moves here, so there is only one guard rather than two: a
 * {@code debt_interest_accruals} row is claimed first, on the unique index over
 * {@code (debt_id, period)}. A duplicate fails on the insert, before the debt is touched at all —
 * which is what makes re-running a pass (a restarted scheduler, a retried catch-up loop) safe.
 */
@Service
public class DebtInterestAccruals {

    private static final Logger log = LoggerFactory.getLogger(DebtInterestAccruals.class);

    public enum Settlement {
        /** This period's interest was calculated and recorded by this call. */
        POSTED,
        /** Already dealt with — the idempotency guard doing its job, not a failure. */
        ALREADY_RECORDED,
        /** The debt has no interest switched on, or its next period has not fully elapsed yet. */
        NOT_DUE
    }

    public record Result(Settlement settlement, DebtInterestAccrual accrual) {
        static Result of(Settlement settlement) {
            return new Result(settlement, null);
        }
    }

    private final DebtRepository debts;
    private final DebtInterestAccrualRepository accruals;

    public DebtInterestAccruals(DebtRepository debts, DebtInterestAccrualRepository accruals) {
        this.debts = debts;
        this.accruals = accruals;
    }

    /**
     * Accrues the debt's current due period and advances its cursor, in its own transaction.
     *
     * @param today the date the pass is running for; a period not yet fully elapsed is left alone
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result accrueDue(UUID debtId, LocalDate today) {
        Debt debt = debts.findById(debtId).orElse(null);
        if (debt == null || !debt.isAccrualDueOn(today)) return Result.of(Settlement.NOT_DUE);

        LocalDate period = debt.nextAccrualPeriod();
        BigDecimal amount = debt.calculateAccrual();
        BigDecimal balanceAtAccrual = debt.totalOutstanding();

        DebtInterestAccrual accrual;
        try {
            accrual = accruals.saveAndFlush(
                    DebtInterestAccrual.claim(debt.getUserId(), debt.getId(), period, amount, balanceAtAccrual));
        } catch (DataIntegrityViolationException e) {
            log.debug("Period {} of debt {} was already accrued", period, debtId);
            // Cursor moves on regardless, or the pass would retry this period forever.
            debt.advanceAccrualCursor(period);
            return Result.of(Settlement.ALREADY_RECORDED);
        }

        debt.applyAccrual(amount, period);
        return new Result(Settlement.POSTED, accrual);
    }
}
