package ph.pesowise.admin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ph.pesowise.admin.api.OverviewDtos.OverviewResponse;
import ph.pesowise.admin.api.OverviewDtos.Section;
import ph.pesowise.admin.clients.AuthAdminClient;
import ph.pesowise.admin.clients.AuthAdminDtos.UserStats;
import ph.pesowise.admin.clients.LedgerAdminClient;
import ph.pesowise.admin.clients.LedgerAdminDtos.LedgerStats;
import ph.pesowise.admin.clients.PlanningAdminClient;
import ph.pesowise.admin.clients.PlanningAdminDtos.PlanningStats;

import java.util.function.Supplier;

/**
 * Composes the admin dashboard from four sources: three Feign calls to the services that own the
 * data, plus this service's own feedback counts.
 *
 * <p>Each Feign call is caught independently. One service being down must degrade its own panel,
 * not the whole page — an admin trying to see user growth should not lose that because
 * ledger-service happened to be restarting. There is precedent for this exact shape of decision
 * in the codebase: planning-service already isolates a single bad recurring bill so it cannot
 * stop the rest of the pass; this applies the same idea to a fan-out instead of a loop.
 */
@Service
public class OverviewService {

    private static final Logger log = LoggerFactory.getLogger(OverviewService.class);

    private final AuthAdminClient authClient;
    private final LedgerAdminClient ledgerClient;
    private final PlanningAdminClient planningClient;
    private final FeedbackService feedbackService;

    public OverviewService(
            AuthAdminClient authClient, LedgerAdminClient ledgerClient,
            PlanningAdminClient planningClient, FeedbackService feedbackService) {
        this.authClient = authClient;
        this.ledgerClient = ledgerClient;
        this.planningClient = planningClient;
        this.feedbackService = feedbackService;
    }

    public OverviewResponse fetch() {
        return new OverviewResponse(
                call("auth-service", authClient::stats),
                call("ledger-service", ledgerClient::stats),
                call("planning-service", planningClient::stats),
                feedbackService.counts());
    }

    private <T> Section<T> call(String serviceName, Supplier<T> call) {
        try {
            return Section.ok(call.get());
        } catch (Exception e) {
            log.warn("Overview panel for {} unavailable: {}", serviceName, e.getMessage());
            return Section.unavailable(serviceName + " is unavailable right now.");
        }
    }
}
