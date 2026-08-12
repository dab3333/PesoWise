package ph.pesowise.planning.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ph.pesowise.planning.api.DebtDtos.AccrualSummary;

/**
 * Fires the interest-accrual pass once a month.
 *
 * <p>00:20 on the 1st — after {@link RecurringScheduler}'s midnight run has had time to finish,
 * so a month's last recurring bill posts before that month's interest is calculated against the
 * balance it leaves behind. The pass itself is idempotent — see {@link DebtInterestAccruals} —
 * which is what makes running it on a timer safe: a container restart re-triggers this method,
 * and re-running it accrues nobody's interest twice.
 *
 * <p>Disabled by setting {@code pesowise.interest.scheduler-enabled=false}, which the tests do so
 * a background pass cannot interfere with them.
 */
@Component
@ConditionalOnProperty(
        name = "pesowise.interest.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class DebtInterestScheduler {

    private static final Logger log = LoggerFactory.getLogger(DebtInterestScheduler.class);

    private final DebtInterestService debtInterestService;

    public DebtInterestScheduler(DebtInterestService debtInterestService) {
        this.debtInterestService = debtInterestService;
    }

    @Scheduled(cron = "${pesowise.interest.cron:0 20 0 1 * *}")
    public void accrueDueInterest() {
        try {
            AccrualSummary summary = debtInterestService.runAccrualPass();
            if (summary.accrued() > 0 || !summary.notes().isEmpty()) {
                log.info("Scheduled interest accrual pass finished: {}", summary);
            }
        } catch (RuntimeException e) {
            // Never let a failure kill the scheduler thread — the next pass should still run, and
            // the pass is safe to repeat.
            log.error("Scheduled interest accrual pass failed; it will be retried on the next run", e);
        }
    }
}
