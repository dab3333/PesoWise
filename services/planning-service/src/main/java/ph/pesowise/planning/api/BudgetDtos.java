package ph.pesowise.planning.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ph.pesowise.planning.ledger.LedgerDtos.Bucket;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class BudgetDtos {

    private BudgetDtos() {
    }

    private static final String MAX_AMOUNT = "999999999999.99";

    /** Upsert a single category's limit for a month. */
    public record BudgetRequest(
            @NotNull UUID categoryId,
            @NotNull
            @DecimalMin(value = "0.01", message = "must be greater than zero")
            @DecimalMax(value = MAX_AMOUNT, message = "is unrealistically large")
            @Digits(integer = 13, fraction = 2)
            BigDecimal limitAmount
    ) {
    }

    /** Saves many limits at once, so applying a suggestion is one request rather than fifteen. */
    public record BulkBudgetRequest(
            @NotEmpty @Size(max = 200) @Valid List<BudgetRequest> budgets
    ) {
    }

    /**
     * One category's standing this month.
     *
     * @param limitAmount null when the category has no budget set — the row is still returned so
     *                    the page can show untracked spending rather than hiding it
     * @param remaining   negative when overspent, which is what the UI colours red
     */
    public record BudgetLine(
            UUID categoryId,
            String categoryName,
            String color,
            Bucket bucket,
            BigDecimal limitAmount,
            BigDecimal spent,
            BigDecimal remaining,
            BigDecimal percentUsed,
            boolean overBudget
    ) {
    }

    /**
     * @param unbudgetedSpend total spent in categories with no limit — the number that quietly
     *                        breaks a budget if it is not surfaced
     */
    public record BudgetOverview(
            String month,
            BigDecimal income,
            BigDecimal totalLimit,
            BigDecimal totalSpent,
            BigDecimal totalRemaining,
            BigDecimal unbudgetedSpend,
            List<BudgetLine> budgeted,
            List<BudgetLine> unbudgeted
    ) {
    }

    /** @param expectedIncome the income to divide; defaults to last month's actual when omitted */
    public record SuggestionRequest(
            @DecimalMin(value = "0.01", message = "must be greater than zero")
            @DecimalMax(value = MAX_AMOUNT, message = "is unrealistically large")
            @Digits(integer = 13, fraction = 2)
            BigDecimal expectedIncome
    ) {
    }

    public record SuggestedLine(
            UUID categoryId,
            String categoryName,
            String color,
            Bucket bucket,
            BigDecimal limitAmount,
            /** True when the amount came from this category's own spending history. */
            boolean fromHistory
    ) {
    }

    /**
     * A preview, not a saved state — the client applies it via the bulk endpoint, so the user can
     * adjust any line first.
     */
    public record SuggestionResponse(
            String month,
            BigDecimal expectedIncome,
            boolean incomeWasEstimated,
            List<BucketAllocation> buckets,
            List<SuggestedLine> lines
    ) {
    }

    public record BucketAllocation(Bucket bucket, int targetPercent, BigDecimal amount) {
    }
}
