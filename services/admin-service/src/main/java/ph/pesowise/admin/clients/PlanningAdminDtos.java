package ph.pesowise.admin.clients;

import java.math.BigDecimal;

/** Mirrors planning-service's {@code InternalAdminDtos} — the shape of {@code /internal/admin/**}. */
public final class PlanningAdminDtos {

    private PlanningAdminDtos() {
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
