package ph.pesowise.planning.service;

import org.springframework.stereotype.Service;
import ph.pesowise.planning.api.BudgetDtos.BucketAllocation;
import ph.pesowise.planning.api.BudgetDtos.SuggestedLine;
import ph.pesowise.planning.api.BudgetDtos.SuggestionResponse;
import ph.pesowise.planning.ledger.LedgerClient;
import ph.pesowise.planning.ledger.LedgerDtos.Bucket;
import ph.pesowise.planning.ledger.LedgerDtos.CategoryTotal;
import ph.pesowise.planning.ledger.LedgerDtos.Kind;
import ph.pesowise.planning.ledger.LedgerDtos.Summary;
import ph.pesowise.planning.web.BadRequestException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns an expected monthly income into a per-category budget using the 70-20-10 method:
 * 70% to needs, 20% to wants, 10% to savings.
 *
 * <p>The method only divides income into three pools. Splitting a pool across the categories in it
 * is the part that decides whether the suggestion is usable, and this does it by
 * <strong>weighting each category by what the user actually spent on it over the last three
 * months</strong>. An even split would hand Rent and Load & Internet the same limit, which nobody
 * would accept — and rejecting the suggestion outright is the same as not having the feature.
 *
 * <p>Categories with no history fall back to an even share of their bucket, so a brand new user
 * still gets a complete budget rather than a page of zeroes.
 */
@Service
public class BudgetSuggester {

    /** The method's split. Must total 100. */
    private static final Map<Bucket, Integer> TARGET_PERCENT = new EnumMap<>(Map.of(
            Bucket.NEEDS, 70,
            Bucket.WANTS, 20,
            Bucket.SAVINGS, 10));

    /** Long enough to smooth a one-off month, short enough to reflect current circumstances. */
    private static final int HISTORY_MONTHS = 3;

    private final LedgerClient ledger;

    public BudgetSuggester(LedgerClient ledger) {
        this.ledger = ledger;
    }

    /**
     * @param expectedIncome the income to divide; when null, last month's actual income is used
     */
    public SuggestionResponse suggest(UUID userId, YearMonth month, BigDecimal expectedIncome) {
        boolean estimated = expectedIncome == null;
        BigDecimal income = estimated ? lastMonthIncome(userId, month) : expectedIncome;

        if (income.signum() <= 0) {
            throw new BadRequestException(
                    "Enter your expected monthly income — there is no income recorded yet to estimate from.");
        }

        // The history window ends with the month before the one being budgeted: including the
        // target month would let a half-finished month drag every limit down.
        YearMonth historyEnd = month.minusMonths(1);
        YearMonth historyStart = historyEnd.minusMonths(HISTORY_MONTHS - 1L);

        List<CategoryTotal> history = ledger.spendByCategory(
                userId, historyStart.atDay(1), historyEnd.atEndOfMonth());

        // Group the expense categories by bucket, preserving the ledger's ordering.
        Map<Bucket, List<CategoryTotal>> byBucket = new EnumMap<>(Bucket.class);
        for (CategoryTotal row : history) {
            if (row.kind() != Kind.EXPENSE || row.bucket() == null) continue;
            byBucket.computeIfAbsent(row.bucket(), key -> new ArrayList<>()).add(row);
        }

        List<BucketAllocation> allocations = new ArrayList<>(3);
        List<SuggestedLine> lines = new ArrayList<>();

        for (Bucket bucket : List.of(Bucket.NEEDS, Bucket.WANTS, Bucket.SAVINGS)) {
            int percent = TARGET_PERCENT.get(bucket);
            BigDecimal pool = income
                    .multiply(BigDecimal.valueOf(percent))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            allocations.add(new BucketAllocation(bucket, percent, pool));
            lines.addAll(split(pool, byBucket.getOrDefault(bucket, List.of())));
        }

        return new SuggestionResponse(month.toString(), income, estimated, allocations, lines);
    }

    /**
     * Divides one bucket's pool across its categories, in proportion to historical spend.
     *
     * <p>The remainder handling matters: dividing ₱21,000 across three categories at 2 decimal
     * places leaves centavos unallocated, and a suggestion whose lines do not add up to the stated
     * bucket total looks broken. The drift is given to the largest line, where it is invisible.
     */
    private static List<SuggestedLine> split(BigDecimal pool, List<CategoryTotal> categories) {
        if (categories.isEmpty() || pool.signum() <= 0) return List.of();

        BigDecimal totalHistory = categories.stream()
                .map(row -> row.total() == null ? BigDecimal.ZERO : row.total())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean weighted = totalHistory.signum() > 0;

        // Preserves insertion order, so the output is stable across identical requests.
        Map<UUID, SuggestedLine> allocated = new LinkedHashMap<>();
        BigDecimal running = BigDecimal.ZERO;

        for (CategoryTotal row : categories) {
            BigDecimal spent = row.total() == null ? BigDecimal.ZERO : row.total();

            BigDecimal share = weighted
                    ? pool.multiply(spent).divide(totalHistory, 2, RoundingMode.HALF_UP)
                    // No history in this bucket at all: an even split beats leaving it blank.
                    : pool.divide(BigDecimal.valueOf(categories.size()), 2, RoundingMode.HALF_UP);

            running = running.add(share);
            allocated.put(row.categoryId(), new SuggestedLine(
                    row.categoryId(), row.categoryName(), row.color(), row.bucket(),
                    share, weighted && spent.signum() > 0));
        }

        List<SuggestedLine> lines = new ArrayList<>(allocated.values());
        BigDecimal drift = pool.subtract(running);

        if (drift.signum() != 0) {
            // Give the rounding remainder to the largest line, where a few centavos do not show.
            int largest = 0;
            for (int i = 1; i < lines.size(); i++) {
                if (lines.get(i).limitAmount().compareTo(lines.get(largest).limitAmount()) > 0) largest = i;
            }
            SuggestedLine target = lines.get(largest);
            lines.set(largest, new SuggestedLine(
                    target.categoryId(), target.categoryName(), target.color(), target.bucket(),
                    target.limitAmount().add(drift), target.fromHistory()));
        }

        // A zero limit is not a budget; drop those rather than saving limits the DB would reject.
        lines.removeIf(line -> line.limitAmount().signum() <= 0);
        lines.sort(Comparator.comparing(SuggestedLine::limitAmount).reversed());
        return lines;
    }

    private BigDecimal lastMonthIncome(UUID userId, YearMonth month) {
        Summary summary = ledger.summary(userId, month.minusMonths(1).toString());
        return summary == null || summary.income() == null ? BigDecimal.ZERO : summary.income();
    }
}
