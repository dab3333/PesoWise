package ph.pesowise.planning.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ph.pesowise.planning.admin.InternalAdminDtos.PlanningStats;
import ph.pesowise.planning.domain.Debt;
import ph.pesowise.planning.repo.BudgetRepository;
import ph.pesowise.planning.repo.DebtRepository;
import ph.pesowise.planning.repo.GoalContributionRepository;
import ph.pesowise.planning.repo.GoalRepository;
import ph.pesowise.planning.repo.RecurringBillRepository;

import java.time.YearMonth;

/**
 * The cross-user aggregate this service was never asked for until an admin overview needed it.
 *
 * <p>Mounted at {@code /internal/admin}, not {@code /api/admin} — the gateway has no route for
 * {@code /internal/**}, so this is unreachable from outside the Compose network. admin-service is
 * the only caller, over the same network trust model this service already relies on to reach
 * ledger-service's {@code /api} endpoints directly.
 */
@RestController
@RequestMapping("/internal/admin")
public class InternalAdminController {

    private final BudgetRepository budgets;
    private final DebtRepository debts;
    private final GoalRepository goals;
    private final GoalContributionRepository goalContributions;
    private final RecurringBillRepository recurringBills;

    public InternalAdminController(
            BudgetRepository budgets, DebtRepository debts, GoalRepository goals,
            GoalContributionRepository goalContributions, RecurringBillRepository recurringBills) {
        this.budgets = budgets;
        this.debts = debts;
        this.goals = goals;
        this.goalContributions = goalContributions;
        this.recurringBills = recurringBills;
    }

    @GetMapping("/stats")
    public PlanningStats stats() {
        return new PlanningStats(
                budgets.countByPeriodMonth(YearMonth.now().atDay(1)),
                debts.countByStatus(Debt.Status.ACTIVE),
                debts.countByStatus(Debt.Status.SETTLED),
                debts.sumActiveBalanceOwedByUsers(),
                debts.sumActiveBalanceOwedToUsers(),
                goals.countByArchivedFalse(),
                goals.sumActiveTargets(),
                goalContributions.sumAllContributions(),
                recurringBills.countByActiveTrue());
    }
}
