package ph.pesowise.planning.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ph.pesowise.planning.domain.GoalContribution;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class GoalDtos {

    private GoalDtos() {
    }

    private static final String MAX_AMOUNT = "999999999999.99";

    public record GoalRequest(
            @NotBlank @Size(max = 80) String name,
            @NotNull
            @DecimalMin(value = "0.01", message = "must be greater than zero")
            @DecimalMax(value = MAX_AMOUNT, message = "is unrealistically large")
            @Digits(integer = 13, fraction = 2)
            BigDecimal targetAmount,
            LocalDate targetDate,
            @Size(max = 255) String note,
            /** Hides the goal without deleting its contributions. Ignored on create. */
            Boolean archived
    ) {
    }

    public record ContributionRequest(
            @NotNull
            @DecimalMin(value = "0.01", message = "must be greater than zero")
            @DecimalMax(value = MAX_AMOUNT, message = "is unrealistically large")
            @Digits(integer = 13, fraction = 2)
            BigDecimal amount,
            @NotNull LocalDate contributedOn,
            @NotNull UUID accountId,
            @NotNull UUID categoryId,
            @Size(max = 255) String note
    ) {
    }

    public record ContributionResponse(
            UUID id,
            UUID goalId,
            BigDecimal amount,
            LocalDate contributedOn,
            String note,
            UUID ledgerTxnId
    ) {
        public static ContributionResponse from(GoalContribution contribution) {
            return new ContributionResponse(
                    contribution.getId(), contribution.getGoalId(), contribution.getAmount(),
                    contribution.getContributedOn(), contribution.getNote(), contribution.getLedgerTxnId());
        }
    }

    /**
     * @param savedAmount     SUM of contributions — derived, never stored
     * @param remaining       zero once the target is met, never negative: "₱0 to go" is the useful
     *                        reading, and over-saving is not a shortfall
     * @param achieved        savedAmount >= targetAmount
     * @param monthlyNeeded   what must be set aside each remaining month to land on the target date;
     *                        null when there is no target date or the goal is already achieved
     * @param behindSchedule  true when the target date has passed without the goal being met
     */
    public record GoalResponse(
            UUID id,
            String name,
            BigDecimal targetAmount,
            BigDecimal savedAmount,
            BigDecimal remaining,
            BigDecimal percentComplete,
            LocalDate targetDate,
            Long daysUntilTarget,
            BigDecimal monthlyNeeded,
            boolean achieved,
            boolean behindSchedule,
            boolean archived,
            String note,
            int contributionCount
    ) {
    }

    public record GoalOverview(
            BigDecimal totalTarget,
            BigDecimal totalSaved,
            int activeCount,
            int achievedCount,
            List<GoalResponse> goals
    ) {
    }
}
