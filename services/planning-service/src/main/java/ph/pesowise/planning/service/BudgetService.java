package ph.pesowise.planning.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.pesowise.planning.api.BudgetDtos.BudgetLine;
import ph.pesowise.planning.api.BudgetDtos.BudgetOverview;
import ph.pesowise.planning.api.BudgetDtos.BudgetRequest;
import ph.pesowise.planning.domain.Budget;
import ph.pesowise.planning.ledger.LedgerClient;
import ph.pesowise.planning.ledger.LedgerDtos.CategoryTotal;
import ph.pesowise.planning.ledger.LedgerDtos.Kind;
import ph.pesowise.planning.ledger.LedgerDtos.Summary;
import ph.pesowise.planning.repo.BudgetRepository;
import ph.pesowise.planning.web.NotFoundException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Budget limits, and how they are doing.
 *
 * <p>The "spent" side is never stored. Every read fetches live totals from ledger-service and joins
 * them against the stored limits in memory. That is the whole point of the synchronous-REST choice:
 * there is no cache to invalidate, so a budget bar cannot show a stale figure after a transaction
 * is edited or deleted.
 */
@Service
public class BudgetService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BudgetRepository budgets;
    private final LedgerClient ledger;

    public BudgetService(BudgetRepository budgets, LedgerClient ledger) {
        this.budgets = budgets;
        this.ledger = ledger;
    }

    @Transactional(readOnly = true)
    public BudgetOverview overview(UUID userId, YearMonth month) {
        Map<UUID, BigDecimal> limits = new LinkedHashMap<>();
        for (Budget budget : budgets.findByUserIdAndPeriodMonth(userId, month.atDay(1))) {
            limits.put(budget.getCategoryId(), budget.getLimitAmount());
        }

        // Two calls, both needed: spend per category, and income for context.
        List<CategoryTotal> spendRows =
                ledger.spendByCategory(userId, month.atDay(1), month.atEndOfMonth());
        Summary summary = ledger.summary(userId, month.toString());

        List<BudgetLine> budgeted = new ArrayList<>();
        List<BudgetLine> unbudgeted = new ArrayList<>();

        for (CategoryTotal row : spendRows) {
            // Income categories are not budgeted — 70-20-10 divides spending, not earnings.
            if (row.kind() != Kind.EXPENSE) continue;

            BigDecimal spent = orZero(row.total());
            BigDecimal limit = limits.get(row.categoryId());

            if (limit != null) {
                budgeted.add(line(row, limit, spent));
            } else if (spent.signum() > 0) {
                // Only surface unbudgeted categories that were actually used. Listing every
                // untouched category as "unbudgeted" would bury the ones that matter.
                unbudgeted.add(line(row, null, spent));
            }
        }

        // Worst standing first: the categories needing attention are what the page is for.
        budgeted.sort(Comparator.comparing(BudgetLine::percentUsed).reversed());
        unbudgeted.sort(Comparator.comparing(BudgetLine::spent).reversed());

        BigDecimal totalLimit = sum(budgeted, BudgetLine::limitAmount);
        BigDecimal totalSpent = sum(budgeted, BudgetLine::spent);
        BigDecimal unbudgetedSpend = sum(unbudgeted, BudgetLine::spent);

        return new BudgetOverview(
                month.toString(),
                summary == null ? BigDecimal.ZERO : orZero(summary.income()),
                totalLimit,
                totalSpent,
                totalLimit.subtract(totalSpent),
                unbudgetedSpend,
                budgeted,
                unbudgeted);
    }

    /**
     * Creates or updates the limit for one category. Upsert rather than separate create and update
     * endpoints, because "set the budget for Groceries this month" is one intention.
     */
    @Transactional
    public void upsert(UUID userId, YearMonth month, BudgetRequest request) {
        upsertOne(userId, month, request);
    }

    /** Applies many limits in one transaction, so a suggestion is saved all-or-nothing. */
    @Transactional
    public void upsertAll(UUID userId, YearMonth month, List<BudgetRequest> requests) {
        for (BudgetRequest request : requests) {
            upsertOne(userId, month, request);
        }
    }

    private void upsertOne(UUID userId, YearMonth month, BudgetRequest request) {
        budgets.findByUserIdAndCategoryIdAndPeriodMonth(userId, request.categoryId(), month.atDay(1))
                .ifPresentOrElse(
                        existing -> existing.setLimitAmount(request.limitAmount()),
                        () -> {
                            try {
                                budgets.saveAndFlush(Budget.create(
                                        userId, request.categoryId(), month, request.limitAmount()));
                            } catch (DataIntegrityViolationException e) {
                                // Lost a race with a concurrent request; the unique index caught
                                // it. The other writer's value stands, so update it to ours.
                                budgets.findByUserIdAndCategoryIdAndPeriodMonth(
                                                userId, request.categoryId(), month.atDay(1))
                                        .ifPresent(found -> found.setLimitAmount(request.limitAmount()));
                            }
                        });
    }

    @Transactional
    public void delete(UUID userId, YearMonth month, UUID categoryId) {
        Budget budget = budgets
                .findByUserIdAndCategoryIdAndPeriodMonth(userId, categoryId, month.atDay(1))
                .orElseThrow(() -> new NotFoundException("Budget"));
        budgets.delete(budget);
    }

    /**
     * Copies every limit from the previous month. The common case at the start of a month is
     * "same as last time", and retyping fifteen numbers is how people stop budgeting.
     */
    @Transactional
    public int copyFromPreviousMonth(UUID userId, YearMonth month) {
        YearMonth previous = month.minusMonths(1);
        List<Budget> source = budgets.findByUserIdAndPeriodMonth(userId, previous.atDay(1));

        if (source.isEmpty()) {
            throw new NotFoundException("A budget for " + previous);
        }

        upsertAll(userId, month, source.stream()
                .map(budget -> new BudgetRequest(budget.getCategoryId(), budget.getLimitAmount()))
                .toList());
        return source.size();
    }

    private static BudgetLine line(CategoryTotal row, BigDecimal limit, BigDecimal spent) {
        BigDecimal percent = limit == null || limit.signum() == 0
                ? BigDecimal.ZERO
                : spent.multiply(HUNDRED).divide(limit, 1, RoundingMode.HALF_UP);

        return new BudgetLine(
                row.categoryId(),
                row.categoryName(),
                row.color(),
                row.bucket(),
                limit,
                spent,
                // Deliberately allowed to go negative: "−₱1,240" is the useful number when
                // overspent, and clamping it at zero would hide the overspend.
                limit == null ? null : limit.subtract(spent),
                percent,
                limit != null && spent.compareTo(limit) > 0);
    }

    private static BigDecimal sum(
            List<BudgetLine> lines, java.util.function.Function<BudgetLine, BigDecimal> field) {
        return lines.stream()
                .map(field)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
