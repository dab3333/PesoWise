package ph.pesowise.admin.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ph.pesowise.admin.api.FeedbackDtos.FeedbackCounts;
import ph.pesowise.admin.clients.AuthAdminClient;
import ph.pesowise.admin.clients.AuthAdminDtos.UserStats;
import ph.pesowise.admin.clients.LedgerAdminClient;
import ph.pesowise.admin.clients.LedgerAdminDtos.LedgerStats;
import ph.pesowise.admin.clients.PlanningAdminClient;
import ph.pesowise.admin.clients.PlanningAdminDtos.PlanningStats;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The point of this class: one dependency failing must degrade only its own panel, never the
 * whole overview. That is the entire reason {@link OverviewService} exists rather than three
 * plain Feign calls inline in the controller.
 */
@ExtendWith(MockitoExtension.class)
class OverviewServiceTest {

    @Mock
    private AuthAdminClient authClient;
    @Mock
    private LedgerAdminClient ledgerClient;
    @Mock
    private PlanningAdminClient planningClient;
    @Mock
    private FeedbackService feedbackService;

    @Test
    @DisplayName("all four sources healthy: every section is available")
    void allAvailable() {
        UserStats users = new UserStats(10, 8, 1, 1, List.of());
        LedgerStats ledger = new LedgerStats(100, 5, BigDecimal.TEN, BigDecimal.ONE, List.of());
        PlanningStats planning = new PlanningStats(1, 2, 3, BigDecimal.ZERO, BigDecimal.ZERO, 1,
                BigDecimal.ZERO, BigDecimal.ZERO, 1);
        FeedbackCounts counts = new FeedbackCounts(1, 2, 3);

        when(authClient.stats()).thenReturn(users);
        when(ledgerClient.stats()).thenReturn(ledger);
        when(planningClient.stats()).thenReturn(planning);
        when(feedbackService.counts()).thenReturn(counts);

        var overview = new OverviewService(authClient, ledgerClient, planningClient, feedbackService).fetch();

        assertThat(overview.users().available()).isTrue();
        assertThat(overview.users().data()).isEqualTo(users);
        assertThat(overview.ledger().available()).isTrue();
        assertThat(overview.planning().available()).isTrue();
        assertThat(overview.feedback()).isEqualTo(counts);
    }

    @Test
    @DisplayName("ledger-service down: only the ledger panel degrades, the rest still populate")
    void oneDependencyDownDegradesOnlyItsPanel() {
        when(authClient.stats()).thenReturn(new UserStats(1, 1, 0, 1, List.of()));
        when(ledgerClient.stats()).thenThrow(new RuntimeException("connection refused"));
        when(planningClient.stats()).thenReturn(new PlanningStats(0, 0, 0, BigDecimal.ZERO,
                BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0));
        when(feedbackService.counts()).thenReturn(new FeedbackCounts(0, 0, 0));

        var overview = new OverviewService(authClient, ledgerClient, planningClient, feedbackService).fetch();

        assertThat(overview.users().available()).isTrue();
        assertThat(overview.planning().available()).isTrue();

        assertThat(overview.ledger().available()).isFalse();
        assertThat(overview.ledger().data()).isNull();
        assertThat(overview.ledger().error()).isNotBlank();
    }

    @Test
    @DisplayName("every dependency down: the call still returns rather than throwing")
    void everythingDownStillReturns() {
        when(authClient.stats()).thenThrow(new RuntimeException("timeout"));
        when(ledgerClient.stats()).thenThrow(new RuntimeException("timeout"));
        when(planningClient.stats()).thenThrow(new RuntimeException("timeout"));
        when(feedbackService.counts()).thenReturn(new FeedbackCounts(0, 0, 0));

        var overview = new OverviewService(authClient, ledgerClient, planningClient, feedbackService).fetch();

        assertThat(overview.users().available()).isFalse();
        assertThat(overview.ledger().available()).isFalse();
        assertThat(overview.planning().available()).isFalse();
        // Feedback is this service's own data, not a Feign call, so it is never a degraded panel.
        assertThat(overview.feedback()).isNotNull();
    }
}
