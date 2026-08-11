package ph.pesowise.ledger.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ph.pesowise.ledger.api.LedgerDtos.BucketBreakdownResponse;
import ph.pesowise.ledger.api.LedgerDtos.DailyTotalResponse;
import ph.pesowise.ledger.api.LedgerDtos.SummaryResponse;
import ph.pesowise.ledger.domain.Enums.Bucket;
import ph.pesowise.ledger.repo.Projections.BucketTotal;
import ph.pesowise.ledger.repo.Projections.DailyTotal;
import ph.pesowise.ledger.repo.Projections.PeriodTotals;
import ph.pesowise.ledger.repo.TransactionRepository;
import ph.pesowise.ledger.web.BadRequestException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * The 70-20-10 maths and the daily gap-filling are the parts of reporting that can be silently
 * wrong, so they are covered here against a mocked repository. The SQL itself is covered by the
 * integration suite.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final UUID USER = UUID.fromString("3f1c9e2a-0000-4000-8000-000000000001");

    @Mock
    private TransactionRepository transactions;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(transactions);
    }

    private static PeriodTotals totals(String income, String expense) {
        return new PeriodTotals() {
            @Override
            public BigDecimal getIncome() {
                return new BigDecimal(income);
            }

            @Override
            public BigDecimal getExpense() {
                return new BigDecimal(expense);
            }
        };
    }

    private static BucketTotal bucketTotal(Bucket bucket, String total) {
        return new BucketTotal() {
            @Override
            public String getBucket() {
                return bucket.name();
            }

            @Override
            public BigDecimal getTotal() {
                return new BigDecimal(total);
            }
        };
    }

    private static DailyTotal dailyTotal(LocalDate day, String income, String expense) {
        return new DailyTotal() {
            @Override
            public LocalDate getDay() {
                return day;
            }

            @Override
            public BigDecimal getIncome() {
                return new BigDecimal(income);
            }

            @Override
            public BigDecimal getExpense() {
                return new BigDecimal(expense);
            }
        };
    }

    @Test
    @DisplayName("summary reports income, expense and the net difference")
    void summaryComputesNet() {
        when(transactions.findTotals(eq(USER), any(), any())).thenReturn(totals("45000.00", "31250.50"));

        SummaryResponse summary = reportService.summary(USER, "2026-08");

        assertThat(summary.income()).isEqualByComparingTo("45000.00");
        assertThat(summary.expense()).isEqualByComparingTo("31250.50");
        assertThat(summary.net()).isEqualByComparingTo("13749.50");
    }

    @Test
    @DisplayName("summary treats a month with no transactions as zero, not null")
    void summaryHandlesEmptyMonth() {
        when(transactions.findTotals(eq(USER), any(), any())).thenReturn(null);

        SummaryResponse summary = reportService.summary(USER, "2026-08");

        assertThat(summary.income()).isEqualByComparingTo("0");
        assertThat(summary.expense()).isEqualByComparingTo("0");
        assertThat(summary.net()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("70-20-10 targets are computed from the month's income")
    void bucketTargetsFollowIncome() {
        when(transactions.findTotals(eq(USER), any(), any())).thenReturn(totals("30000.00", "0"));
        when(transactions.findExpenseTotalsByBucket(eq(USER), any(), any())).thenReturn(List.of());

        List<BucketBreakdownResponse> breakdown = reportService.byBucket(USER, "2026-08");

        assertThat(breakdown).extracting(BucketBreakdownResponse::bucket)
                .containsExactly(Bucket.NEEDS, Bucket.WANTS, Bucket.SAVINGS);
        assertThat(breakdown).extracting(BucketBreakdownResponse::targetPercent)
                .containsExactly(70, 20, 10);
        assertThat(breakdown.get(0).targetAmount()).isEqualByComparingTo("21000.00");
        assertThat(breakdown.get(1).targetAmount()).isEqualByComparingTo("6000.00");
        assertThat(breakdown.get(2).targetAmount()).isEqualByComparingTo("3000.00");
    }

    @Test
    @DisplayName("actual percentages are the share of income each bucket consumed")
    void bucketActualPercentages() {
        when(transactions.findTotals(eq(USER), any(), any())).thenReturn(totals("30000.00", "18000.00"));
        when(transactions.findExpenseTotalsByBucket(eq(USER), any(), any())).thenReturn(List.of(
                bucketTotal(Bucket.NEEDS, "15000.00"),
                bucketTotal(Bucket.WANTS, "3000.00")));

        List<BucketBreakdownResponse> breakdown = reportService.byBucket(USER, "2026-08");

        assertThat(breakdown.get(0).actualAmount()).isEqualByComparingTo("15000.00");
        assertThat(breakdown.get(0).actualPercent()).isEqualByComparingTo("50.0");
        assertThat(breakdown.get(1).actualPercent()).isEqualByComparingTo("10.0");
        // No savings rows at all — still present, at zero.
        assertThat(breakdown.get(2).actualAmount()).isEqualByComparingTo("0");
        assertThat(breakdown.get(2).actualPercent()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("spending with zero income does not divide by zero")
    void bucketHandlesZeroIncome() {
        when(transactions.findTotals(eq(USER), any(), any())).thenReturn(totals("0", "5000.00"));
        when(transactions.findExpenseTotalsByBucket(eq(USER), any(), any()))
                .thenReturn(List.of(bucketTotal(Bucket.NEEDS, "5000.00")));

        List<BucketBreakdownResponse> breakdown = reportService.byBucket(USER, "2026-08");

        assertThat(breakdown.get(0).actualAmount()).isEqualByComparingTo("5000.00");
        assertThat(breakdown.get(0).actualPercent()).isEqualByComparingTo("0");
        assertThat(breakdown.get(0).targetAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("all three buckets are always returned, in method order")
    void bucketAlwaysReturnsThree() {
        when(transactions.findTotals(eq(USER), any(), any())).thenReturn(null);
        when(transactions.findExpenseTotalsByBucket(eq(USER), any(), any())).thenReturn(List.of());

        assertThat(reportService.byBucket(USER, "2026-08")).hasSize(3);
    }

    @Test
    @DisplayName("the daily series covers every day of the month, zero-filling quiet days")
    void dailySeriesIsContinuous() {
        when(transactions.findDailyTotals(eq(USER), any(), any())).thenReturn(List.of(
                dailyTotal(LocalDate.of(2026, 2, 3), "0", "500.00"),
                dailyTotal(LocalDate.of(2026, 2, 28), "20000.00", "0")));

        List<DailyTotalResponse> series = reportService.daily(USER, "2026-02");

        // February 2026 is not a leap year: 28 days.
        assertThat(series).hasSize(28);
        assertThat(series.get(0).date()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(series.get(0).expense()).isEqualByComparingTo("0");
        assertThat(series.get(2).expense()).isEqualByComparingTo("500.00");
        assertThat(series.get(27).income()).isEqualByComparingTo("20000.00");
    }

    @Test
    @DisplayName("the daily series covers all 29 days of a leap February")
    void dailySeriesHandlesLeapYear() {
        when(transactions.findDailyTotals(eq(USER), any(), any())).thenReturn(List.of());

        assertThat(reportService.daily(USER, "2028-02")).hasSize(29);
    }

    @Test
    @DisplayName("a malformed month is rejected with a helpful message")
    void rejectsBadMonth() {
        assertThatThrownBy(() -> reportService.summary(USER, "August 2026"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("YYYY-MM");
    }

    @Test
    @DisplayName("a reversed date range is rejected")
    void rejectsReversedRange() {
        lenient().when(transactions.findTotalsByCategory(eq(USER), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> reportService.byCategory(
                USER, LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 1)))
                .isInstanceOf(BadRequestException.class);
    }
}
