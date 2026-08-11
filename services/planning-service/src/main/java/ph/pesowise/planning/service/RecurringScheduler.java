package ph.pesowise.planning.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ph.pesowise.planning.api.RecurringDtos.RunSummary;

/**
 * Fires the recurring-bill pass once a day.
 *
 * <p>Just after midnight, so a bill due today is recorded on the day rather than a day late. The
 * pass itself is idempotent — see {@link RecurringOccurrences} — which is what makes running it on a
 * timer safe: a container restart re-triggers this method, and re-running it charges nobody twice.
 *
 * <p>Disabled by setting {@code pesowise.recurring.scheduler-enabled=false}, which the tests do so a
 * background pass cannot interfere with them.
 */
@Component
@ConditionalOnProperty(
        name = "pesowise.recurring.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class RecurringScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecurringScheduler.class);

    private final RecurringService recurringService;

    public RecurringScheduler(RecurringService recurringService) {
        this.recurringService = recurringService;
    }

    @Scheduled(cron = "${pesowise.recurring.cron:0 5 0 * * *}")
    public void postDueBills() {
        try {
            RunSummary summary = recurringService.runDueBills();
            if (summary.posted() > 0 || !summary.notes().isEmpty()) {
                log.info("Scheduled recurring pass finished: {}", summary);
            }
        } catch (RuntimeException e) {
            // Never let a failure kill the scheduler thread — the next pass should still run, and the
            // pass is safe to repeat.
            log.error("Scheduled recurring pass failed; it will be retried on the next run", e);
        }
    }
}
