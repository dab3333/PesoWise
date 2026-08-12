package ph.pesowise.planning.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ph.pesowise.planning.api.DebtDtos.AccrualSummary;
import ph.pesowise.planning.domain.Debt;
import ph.pesowise.planning.repo.DebtRepository;
import ph.pesowise.planning.service.DebtInterestAccruals.Result;
import ph.pesowise.planning.service.DebtInterestAccruals.Settlement;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The monthly interest-accrual pass, over every interest-bearing debt.
 *
 * <p>Each period is settled by {@link DebtInterestAccruals} in its own transaction, so one
 * failing debt cannot roll back the debts already handled. The idempotency guard lives there too.
 */
@Service
public class DebtInterestService {

    private static final Logger log = LoggerFactory.getLogger(DebtInterestService.class);

    /**
     * How many missed monthly periods one debt may catch up on in a single pass — same reasoning
     * and same limit as {@link RecurringService#MAX_CATCH_UP}: a debt left unattended for years
     * should not silently compound a decade in one pass, and a corrupt cursor should not spin
     * forever.
     */
    static final int MAX_CATCH_UP = 12;

    private final DebtRepository debts;
    private final DebtInterestAccruals accruals;

    public DebtInterestService(DebtRepository debts, DebtInterestAccruals accruals) {
        this.debts = debts;
        this.accruals = accruals;
    }

    /**
     * One pass over every active, interest-bearing debt.
     *
     * <p>Deliberately not transactional: each period commits on its own inside
     * {@link DebtInterestAccruals}, so a failure part-way through keeps everything already done.
     */
    public AccrualSummary runAccrualPass() {
        LocalDate today = LocalDate.now();
        List<Debt> candidates = debts.findByStatusAndInterestMethodIsNotNull(Debt.Status.ACTIVE);

        int accrued = 0;
        int alreadyRecorded = 0;
        List<String> notes = new ArrayList<>();

        for (Debt debt : candidates) {
            int settled = 0;
            try {
                for (int attempt = 0; attempt < MAX_CATCH_UP; attempt++) {
                    Result result = accruals.accrueDue(debt.getId(), today);
                    if (result.settlement() == Settlement.NOT_DUE) break;

                    if (result.settlement() == Settlement.POSTED) accrued++;
                    else alreadyRecorded++;
                    settled++;
                }
            } catch (RuntimeException e) {
                // Contained on purpose. One debt cannot stall the debts after it in the list —
                // each period has already committed on its own, and this one is safe to retry.
                String note = "%s: could not accrue interest (%s). It will be retried."
                        .formatted(debt.getName(), e.getClass().getSimpleName());
                log.error("Debt {} failed to accrue during the pass", debt.getId(), e);
                notes.add(note);
                continue;
            }

            if (settled == MAX_CATCH_UP && stillDue(debt.getId(), today)) {
                String note = "%s: stopped after %d months, more are still outstanding."
                        .formatted(debt.getName(), MAX_CATCH_UP);
                log.warn(note);
                notes.add(note);
            }
        }

        if (accrued > 0 || alreadyRecorded > 0) {
            log.info("Interest accrual pass: {} accrued, {} already recorded", accrued, alreadyRecorded);
        }
        return new AccrualSummary(accrued, alreadyRecorded, notes);
    }

    private boolean stillDue(UUID debtId, LocalDate today) {
        return debts.findById(debtId).filter(debt -> debt.isAccrualDueOn(today)).isPresent();
    }
}
