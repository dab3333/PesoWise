package ph.pesowise.ledger.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.pesowise.ledger.api.LedgerDtos.BucketBreakdownResponse;
import ph.pesowise.ledger.api.LedgerDtos.CategoryTotalResponse;
import ph.pesowise.ledger.api.LedgerDtos.DailyTotalResponse;
import ph.pesowise.ledger.api.LedgerDtos.SummaryResponse;
import ph.pesowise.ledger.domain.Enums.Bucket;
import ph.pesowise.ledger.domain.Enums.Kind;
import ph.pesowise.ledger.repo.Projections.BucketTotal;
import ph.pesowise.ledger.repo.Projections.DailyTotal;
import ph.pesowise.ledger.repo.Projections.PeriodTotals;
import ph.pesowise.ledger.repo.TransactionRepository;
import ph.pesowise.ledger.web.BadRequestException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only aggregates for the dashboard. Every total is computed by a GROUP BY in Postgres;
 * this class only shapes the results and fills gaps.
 */
@Service
public class ReportService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final TransactionRepository transactions;

    public ReportService(TransactionRepository transactions) {
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public SummaryResponse summary(UUID userId, String month) {
        YearMonth period = parseMonth(month);
        PeriodTotals totals = transactions.findTotals(
                userId, period.atDay(1), period.atEndOfMonth());

        BigDecimal income = orZero(totals == null ? null : totals.getIncome());
        BigDecimal expense = orZero(totals == null ? null : totals.getExpense());

        return new SummaryResponse(month, income, expense, income.subtract(expense));
    }

    @Transactional(readOnly = true)
    public List<CategoryTotalResponse> byCategory(UUID userId, LocalDate from, LocalDate to) {
        requireOrderedRange(from, to);

        return transactions.findTotalsByCategory(userId, from, to).stream()
                .map(row -> new CategoryTotalResponse(
                        row.getCategoryId(), row.getCategoryName(), row.getColor(),
                        Kind.valueOf(row.getKind()), orZero(row.getTotal())))
                .toList();
    }

    /**
     * The 70-20-10 breakdown: how the month's spending divided across needs, wants and savings,
     * against the targets the method sets as a share of that month's income.
     *
     * <p>Always returns all three buckets, in method order, even with no activity — a dashboard
     * card that drops a bucket when it is empty reads as a bug.
     */
    @Transactional(readOnly = true)
    public List<BucketBreakdownResponse> byBucket(UUID userId, String month) {
        YearMonth period = parseMonth(month);
        LocalDate from = period.atDay(1);
        LocalDate to = period.atEndOfMonth();

        PeriodTotals totals = transactions.findTotals(userId, from, to);
        BigDecimal income = orZero(totals == null ? null : totals.getIncome());

        Map<Bucket, BigDecimal> actuals = new EnumMap<>(Bucket.class);
        for (BucketTotal row : transactions.findExpenseTotalsByBucket(userId, from, to)) {
            // bucket is never null here: the DB CHECK guarantees expense categories carry one.
            actuals.merge(Bucket.valueOf(row.getBucket()), orZero(row.getTotal()), BigDecimal::add);
        }

        List<BucketBreakdownResponse> breakdown = new ArrayList<>(3);
        for (Bucket bucket : List.of(Bucket.NEEDS, Bucket.WANTS, Bucket.SAVINGS)) {
            int target = CategoryService.targetPercent(bucket);
            BigDecimal actual = actuals.getOrDefault(bucket, BigDecimal.ZERO);

            breakdown.add(new BucketBreakdownResponse(
                    bucket,
                    target,
                    income.multiply(BigDecimal.valueOf(target)).divide(HUNDRED, 2, RoundingMode.HALF_UP),
                    actual,
                    percentOf(actual, income)));
        }
        return breakdown;
    }

    /**
     * One entry per day of the month, including days with no activity — the trend line needs a
     * continuous x-axis or it draws misleading straight segments across gaps.
     */
    @Transactional(readOnly = true)
    public List<DailyTotalResponse> daily(UUID userId, String month) {
        YearMonth period = parseMonth(month);

        Map<LocalDate, DailyTotal> byDay = new java.util.HashMap<>();
        for (DailyTotal row : transactions.findDailyTotals(userId, period.atDay(1), period.atEndOfMonth())) {
            byDay.put(row.getDay(), row);
        }

        List<DailyTotalResponse> series = new ArrayList<>(period.lengthOfMonth());
        for (int day = 1; day <= period.lengthOfMonth(); day++) {
            LocalDate date = period.atDay(day);
            DailyTotal row = byDay.get(date);
            series.add(new DailyTotalResponse(
                    date,
                    row == null ? BigDecimal.ZERO : orZero(row.getIncome()),
                    row == null ? BigDecimal.ZERO : orZero(row.getExpense())));
        }
        return series;
    }

    /** @param month a YYYY-MM key */
    static YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("'%s' is not a valid month. Use YYYY-MM.".formatted(month));
        }
    }

    private static void requireOrderedRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BadRequestException("The start date must not be after the end date.");
        }
    }

    /** COALESCE covers the SUM, but an empty table can still yield a null row. */
    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal percentOf(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) return BigDecimal.ZERO;
        return part.multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_UP);
    }
}
