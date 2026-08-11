package ph.pesowise.planning.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ph.pesowise.planning.api.GoalDtos.ContributionRequest;
import ph.pesowise.planning.api.GoalDtos.GoalOverview;
import ph.pesowise.planning.api.GoalDtos.GoalRequest;
import ph.pesowise.planning.api.GoalDtos.GoalResponse;
import ph.pesowise.planning.domain.Goal;
import ph.pesowise.planning.domain.GoalContribution;
import ph.pesowise.planning.ledger.LedgerDtos.SourceType;
import ph.pesowise.planning.ledger.LedgerWriter;
import ph.pesowise.planning.repo.GoalContributionRepository;
import ph.pesowise.planning.repo.GoalContributionRepository.GoalSaved;
import ph.pesowise.planning.repo.GoalRepository;
import ph.pesowise.planning.web.NotFoundException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    private static final UUID USER = UUID.fromString("3f1c9e2a-0000-4000-8000-000000000001");
    private static final UUID ACCOUNT = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final UUID CATEGORY = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002");
    private static final UUID LEDGER_TXN = UUID.fromString("cccccccc-0000-4000-8000-000000000003");

    @Mock
    private GoalRepository goals;

    @Mock
    private GoalContributionRepository contributions;

    @Mock
    private LedgerWriter ledger;

    private GoalService goalService;

    @BeforeEach
    void setUp() {
        goalService = new GoalService(goals, contributions, ledger);
    }

    private static GoalSaved saved(UUID goalId, String total, int count) {
        return new GoalSaved() {
            @Override
            public UUID getGoalId() {
                return goalId;
            }

            @Override
            public BigDecimal getTotal() {
                return new BigDecimal(total);
            }

            @Override
            public int getContributionCount() {
                return count;
            }
        };
    }

    private Goal givenGoal(String target, LocalDate targetDate) {
        Goal goal = Goal.create(USER, "Bagong laptop", new BigDecimal(target), targetDate, null);
        lenient().when(goals.findByIdAndUserId(goal.getId(), USER)).thenReturn(Optional.of(goal));
        return goal;
    }

    private void givenListing(Goal goal, GoalSaved... totals) {
        when(goals.findByUserIdOrderByArchivedAscTargetDateAscCreatedAtAsc(USER))
                .thenReturn(List.of(goal));
        when(contributions.findSavedTotalsByUserId(USER)).thenReturn(List.of(totals));
    }

    private void givenLedgerAccepts() {
        when(ledger.post(eq(USER), eq(SourceType.GOAL_CONTRIBUTION), any(), any(), any(), any(), any(), any()))
                .thenReturn(LEDGER_TXN);
        when(contributions.save(any(GoalContribution.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static ContributionRequest contribution(String amount) {
        return new ContributionRequest(
                new BigDecimal(amount), LocalDate.of(2026, 8, 11), ACCOUNT, CATEGORY, "from 13th month");
    }

    @Test
    @DisplayName("the saved amount is derived from contributions, never stored")
    void savedAmountIsDerived() {
        Goal goal = givenGoal("50000.00", null);
        givenListing(goal, saved(goal.getId(), "12500.00", 3));

        GoalResponse response = goalService.list(USER).goals().getFirst();

        assertThat(response.savedAmount()).isEqualByComparingTo("12500.00");
        assertThat(response.remaining()).isEqualByComparingTo("37500.00");
        assertThat(response.percentComplete()).isEqualByComparingTo("25.0");
        assertThat(response.contributionCount()).isEqualTo(3);
        assertThat(response.achieved()).isFalse();
    }

    @Test
    @DisplayName("a goal with no contributions reads as zero rather than null")
    void handlesGoalWithNoContributions() {
        Goal goal = givenGoal("50000.00", null);
        givenListing(goal);

        GoalResponse response = goalService.list(USER).goals().getFirst();

        assertThat(response.savedAmount()).isEqualByComparingTo("0");
        assertThat(response.remaining()).isEqualByComparingTo("50000.00");
        assertThat(response.contributionCount()).isZero();
    }

    @Test
    @DisplayName("reaching the target marks the goal achieved")
    void reachingTargetAchievesGoal() {
        Goal goal = givenGoal("50000.00", null);
        givenListing(goal, saved(goal.getId(), "50000.00", 4));

        GoalResponse response = goalService.list(USER).goals().getFirst();

        assertThat(response.achieved()).isTrue();
        assertThat(response.remaining()).isEqualByComparingTo("0");
        assertThat(response.percentComplete()).isEqualByComparingTo("100.0");
    }

    @Test
    @DisplayName("over-saving is allowed and never reports a negative remaining")
    void overSavingIsAllowed() {
        Goal goal = givenGoal("50000.00", null);
        givenListing(goal, saved(goal.getId(), "57000.00", 5));

        GoalResponse response = goalService.list(USER).goals().getFirst();

        // Saving more than planned is a good outcome, not a shortfall of -7000.
        assertThat(response.remaining()).isEqualByComparingTo("0");
        assertThat(response.percentComplete()).isEqualByComparingTo("114.0");
        assertThat(response.achieved()).isTrue();
    }

    @Test
    @DisplayName("monthlyNeeded spreads the shortfall over the remaining months, inclusive")
    void computesMonthlyNeeded() {
        // Five months inclusive from the current month.
        LocalDate target = YearMonth.now().plusMonths(4).atEndOfMonth();
        Goal goal = givenGoal("50000.00", target);
        givenListing(goal, saved(goal.getId(), "10000.00", 2));

        GoalResponse response = goalService.list(USER).goals().getFirst();

        // 40000 remaining over 5 months.
        assertThat(response.monthlyNeeded()).isEqualByComparingTo("8000.00");
    }

    @Test
    @DisplayName("a target inside the current month asks for the whole shortfall, not a division by zero")
    void monthlyNeededWithinCurrentMonth() {
        Goal goal = givenGoal("50000.00", YearMonth.now().atEndOfMonth());
        givenListing(goal, saved(goal.getId(), "10000.00", 1));

        assertThat(goalService.list(USER).goals().getFirst().monthlyNeeded())
                .isEqualByComparingTo("40000.00");
    }

    @Test
    @DisplayName("monthlyNeeded rounds up, so following it always reaches the target")
    void monthlyNeededRoundsUp() {
        // 10000 over 3 months is 3333.333…; rounding down would land short.
        LocalDate target = YearMonth.now().plusMonths(2).atEndOfMonth();
        Goal goal = givenGoal("10000.00", target);
        givenListing(goal);

        assertThat(goalService.list(USER).goals().getFirst().monthlyNeeded())
                .isEqualByComparingTo("3333.34");
    }

    @Test
    @DisplayName("an achieved goal needs nothing more per month")
    void noMonthlyNeededOnceAchieved() {
        Goal goal = givenGoal("50000.00", YearMonth.now().plusMonths(3).atEndOfMonth());
        givenListing(goal, saved(goal.getId(), "50000.00", 4));

        assertThat(goalService.list(USER).goals().getFirst().monthlyNeeded()).isNull();
    }

    @Test
    @DisplayName("a goal with no target date has no monthly figure")
    void noMonthlyNeededWithoutTargetDate() {
        Goal goal = givenGoal("50000.00", null);
        givenListing(goal);

        GoalResponse response = goalService.list(USER).goals().getFirst();
        assertThat(response.monthlyNeeded()).isNull();
        assertThat(response.daysUntilTarget()).isNull();
        assertThat(response.behindSchedule()).isFalse();
    }

    @Test
    @DisplayName("a passed target date marks an unmet goal behind schedule, but never an achieved one")
    void behindScheduleOnlyAppliesToUnmetGoals() {
        Goal missed = Goal.create(USER, "Missed", new BigDecimal("1000"),
                LocalDate.now().minusDays(10), null);
        Goal met = Goal.create(USER, "Met late", new BigDecimal("1000"),
                LocalDate.now().minusDays(10), null);
        when(goals.findByUserIdOrderByArchivedAscTargetDateAscCreatedAtAsc(USER))
                .thenReturn(List.of(missed, met));
        when(contributions.findSavedTotalsByUserId(USER))
                .thenReturn(List.of(saved(met.getId(), "1000", 1)));

        List<GoalResponse> found = goalService.list(USER).goals();

        assertThat(found.get(0).behindSchedule()).isTrue();
        assertThat(found.get(0).daysUntilTarget()).isEqualTo(-10);
        assertThat(found.get(1).behindSchedule()).isFalse();
    }

    @Test
    @DisplayName("a contribution writes a ledger transaction tagged GOAL_CONTRIBUTION")
    void contributionWritesToLedger() {
        Goal goal = givenGoal("50000.00", null);
        givenLedgerAccepts();

        assertThat(goalService.contribute(USER, goal.getId(), contribution("5000.00")).ledgerTxnId())
                .isEqualTo(LEDGER_TXN);

        ArgumentCaptor<String> note = ArgumentCaptor.forClass(String.class);
        verify(ledger).post(eq(USER), eq(SourceType.GOAL_CONTRIBUTION), eq(goal.getId()),
                eq(ACCOUNT), eq(CATEGORY), eq(new BigDecimal("5000.00")),
                eq(LocalDate.of(2026, 8, 11)), note.capture());

        assertThat(note.getValue()).contains("Bagong laptop").contains("13th month");
    }

    @Test
    @DisplayName("contributing beyond the target is accepted — over-saving is not an error")
    void allowsContributionBeyondTarget() {
        Goal goal = givenGoal("1000.00", null);
        givenLedgerAccepts();

        assertThat(goalService.contribute(USER, goal.getId(), contribution("99999.00"))).isNotNull();
    }

    @Test
    @DisplayName("a ledger failure propagates, so nothing is stored locally")
    void ledgerFailurePropagates() {
        Goal goal = givenGoal("50000.00", null);
        when(ledger.post(eq(USER), eq(SourceType.GOAL_CONTRIBUTION), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("ledger unreachable"));

        assertThatThrownBy(() -> goalService.contribute(USER, goal.getId(), contribution("5000.00")))
                .isInstanceOf(IllegalStateException.class);

        verify(contributions, never()).save(any());
    }

    @Test
    @DisplayName("undoing a contribution removes the ledger transaction too")
    void undoRemovesLedgerTransaction() {
        Goal goal = givenGoal("50000.00", null);
        GoalContribution stored = GoalContribution.create(
                USER, goal.getId(), new BigDecimal("5000.00"), LocalDate.of(2026, 8, 11), null, LEDGER_TXN);
        when(contributions.findByIdAndUserId(stored.getId(), USER)).thenReturn(Optional.of(stored));

        goalService.deleteContribution(USER, goal.getId(), stored.getId());

        verify(ledger).remove(USER, LEDGER_TXN);
        verify(contributions).delete(stored);
    }

    @Test
    @DisplayName("a contribution belonging to another goal is not found")
    void contributionMustBelongToTheGoal() {
        Goal goal = givenGoal("50000.00", null);
        GoalContribution other = GoalContribution.create(
                USER, UUID.randomUUID(), new BigDecimal("100"), LocalDate.now(), null, LEDGER_TXN);
        when(contributions.findByIdAndUserId(other.getId(), USER)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> goalService.deleteContribution(USER, goal.getId(), other.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("another user's goal is reported as not found, never as forbidden")
    void scopesByUser() {
        UUID foreign = UUID.randomUUID();
        when(goals.findByIdAndUserId(foreign, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.contribute(USER, foreign, contribution("100.00")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("archived goals are excluded from the headline totals")
    void archivedGoalsExcludedFromTotals() {
        Goal live = Goal.create(USER, "Live", new BigDecimal("10000"), null, null);
        Goal archived = Goal.create(USER, "Old", new BigDecimal("99000"), null, null);
        archived.setArchived(true);
        when(goals.findByUserIdOrderByArchivedAscTargetDateAscCreatedAtAsc(USER))
                .thenReturn(List.of(live, archived));
        when(contributions.findSavedTotalsByUserId(USER)).thenReturn(List.of(
                saved(live.getId(), "2500", 1), saved(archived.getId(), "99000", 9)));

        GoalOverview overview = goalService.list(USER);

        assertThat(overview.totalTarget()).isEqualByComparingTo("10000");
        assertThat(overview.totalSaved()).isEqualByComparingTo("2500");
        assertThat(overview.activeCount()).isEqualTo(1);
        assertThat(overview.achievedCount()).isZero();
        // Still listed, just not counted.
        assertThat(overview.goals()).hasSize(2);
    }

    @Test
    @DisplayName("active and achieved goals are counted separately")
    void countsActiveAndAchieved() {
        Goal ongoing = Goal.create(USER, "Ongoing", new BigDecimal("10000"), null, null);
        Goal done = Goal.create(USER, "Done", new BigDecimal("5000"), null, null);
        when(goals.findByUserIdOrderByArchivedAscTargetDateAscCreatedAtAsc(USER))
                .thenReturn(List.of(ongoing, done));
        when(contributions.findSavedTotalsByUserId(USER)).thenReturn(List.of(
                saved(ongoing.getId(), "1000", 1), saved(done.getId(), "5000", 2)));

        GoalOverview overview = goalService.list(USER);

        assertThat(overview.activeCount()).isEqualTo(1);
        assertThat(overview.achievedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a new goal starts at zero saved")
    void newGoalStartsEmpty() {
        when(goals.save(any(Goal.class))).thenAnswer(call -> call.getArgument(0));

        GoalResponse response = goalService.create(USER, new GoalRequest(
                "  Bagong laptop  ", new BigDecimal("50000.00"), LocalDate.of(2027, 6, 30), " para sa work ", null));

        assertThat(response.name()).isEqualTo("Bagong laptop");
        assertThat(response.note()).isEqualTo("para sa work");
        assertThat(response.savedAmount()).isEqualByComparingTo("0");
        assertThat(response.achieved()).isFalse();
    }

    @Test
    @DisplayName("the target amount is editable, unlike a debt's principal")
    void targetIsEditable() {
        Goal goal = givenGoal("50000.00", null);
        when(contributions.findSavedTotalsByUserId(USER))
                .thenReturn(List.of(saved(goal.getId(), "12500.00", 3)));

        GoalResponse response = goalService.update(USER, goal.getId(), new GoalRequest(
                "Bagong laptop", new BigDecimal("40000.00"), null, null, null));

        assertThat(response.targetAmount()).isEqualByComparingTo("40000.00");
        // Contributions stay exactly as recorded; only the goalpost moved.
        assertThat(response.savedAmount()).isEqualByComparingTo("12500.00");
        assertThat(response.percentComplete()).isEqualByComparingTo("31.3");
    }
}
