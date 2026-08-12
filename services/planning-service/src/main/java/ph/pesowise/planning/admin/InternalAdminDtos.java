package ph.pesowise.planning.admin;

import java.math.BigDecimal;

public final class InternalAdminDtos {

    private InternalAdminDtos() {
    }

    public record PlanningStats(
            long budgetLinesThisMonth,
            long activeDebts,
            long settledDebts,
            BigDecimal totalOwedByUsers,
            BigDecimal totalOwedToUsers,
            long activeGoals,
            BigDecimal totalGoalTargets,
            BigDecimal totalGoalSaved,
            long activeRecurringBills) {
    }
}
