package ph.pesowise.planning.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ph.pesowise.planning.api.BudgetDtos.BudgetLine;
import ph.pesowise.planning.api.BudgetDtos.BudgetOverview;
import ph.pesowise.planning.api.BudgetDtos.BudgetRequest;
import ph.pesowise.planning.domain.Budget;
import ph.pesowise.planning.ledger.LedgerClient;
import ph.pesowise.planning.ledger.LedgerDtos.Bucket;
import ph.pesowise.planning.ledger.LedgerDtos.CategoryTotal;
import ph.pesowise.planning.ledger.LedgerDtos.Kind;
import ph.pesowise.planning.ledger.LedgerDtos.Summary;
import ph.pesowise.planning.repo.BudgetRepository;
import ph.pesowise.planning.web.NotFoundException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    private static final UUID USER = UUID.fromString("3f1c9e2a-0000-4000-8000-000000000001");
    private static final YearMonth AUGUST = YearMonth.of(2026, 8);

    private static final UUID GROCERIES = UUID.fromString("11111111-0000-4000-8000-000000000001");
    private static final UUID RENT = UUID.fromString("22222222-0000-4000-8000-000000000002");
    private static final UUID DINING = UUID.fromString("33333333-0000-4000-8000-000000000003");
    private static final UUID SALARY = UUID.fromString("44444444-0000-4000-8000-000000000004");

    @Mock
    private BudgetRepository budgets;

    @Mock
    private LedgerClient ledger;

    private BudgetService budgetService;

    @BeforeEach
    void setUp() {
        budgetService = new BudgetService(budgets, ledger);
    }

    private static CategoryTotal spend(UUID id, String name, Bucket bucket, String total) {
        return new CategoryTotal(id, name, "#0f8a6c", Kind.EXPENSE, bucket, new BigDecimal(total));
    }

    private void givenLimits(Budget... rows) {
        when(budgets.findByUserIdAndPeriodMonth(USER, AUGUST.atDay(1))).thenReturn(List.of(rows));
    }

    private void givenSpend(CategoryTotal... rows) {
        when(ledger.spendByCategory(eq(USER), any(), any())).thenReturn(List.of(rows));
        lenient().when(ledger.summary(eq(USER), anyString())).thenReturn(
                new Summary("2026-08", new BigDecimal("45000"), BigDecimal.ZERO, BigDecimal.ZERO));
    }

    private static BudgetLine lineFor(List<BudgetLine> lines, String name) {
        return lines.stream()
                .filter(line -> line.categoryName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no line for " + name));
    }

    @Test
    @DisplayName("spent, remaining and percent used are computed from live ledger totals")
    void computesProgressFromLedger() {
        givenLimits(Budget.create(USER, GROCERIES, AUGUST, new BigDecimal("8000.00")));
        givenSpend(spend(GROCERIES, "Groceries", Bucket.NEEDS, "6000.00"));

        BudgetOverview overview = budgetService.overview(USER, AUGUST);

        BudgetLine line = lineFor(overview.budgeted(), "Groceries");
        assertThat(line.limitAmount()).isEqualByComparingTo("8000.00");
        assertThat(line.spent()).isEqualByComparingTo("6000.00");
        assertThat(line.remaining()).isEqualByComparingTo("2000.00");
        assertThat(line.percentUsed()).isEqualByComparingTo("75.0");
        assertThat(line.overBudget()).isFalse();
    }

    @Test
    @DisplayName("an overspent category reports a negative remaining and is flagged")
    void reportsOverspendAsNegative() {
        givenLimits(Budget.create(USER, GROCERIES, AUGUST, new BigDecimal("5000.00")));
        givenSpend(spend(GROCERIES, "Groceries", Bucket.NEEDS, "6240.00"));

        BudgetLine line = lineFor(budgetService.overview(USER, AUGUST).budgeted(), "Groceries");

        // Negative on purpose: "−₱1,240" is the useful number, and clamping to zero hides it.
        assertThat(line.remaining()).isEqualByComparingTo("-1240.00");
        assertThat(line.percentUsed()).isEqualByComparingTo("124.8");
        assertThat(line.overBudget()).isTrue();
    }

    @Test
    @DisplayName("a budgeted category with no spending yet shows the full limit remaining")
    void handlesUnspentBudget() {
        givenLimits(Budget.create(USER, RENT, AUGUST, new BigDecimal("15000.00")));
        givenSpend(spend(RENT, "Rent", Bucket.NEEDS, "0"));

        BudgetLine line = lineFor(budgetService.overview(USER, AUGUST).budgeted(), "Rent");

        assertThat(line.spent()).isEqualByComparingTo("0");
        assertThat(line.remaining()).isEqualByComparingTo("15000.00");
        assertThat(line.percentUsed()).isEqualByComparingTo("0.0");
    }

    @Test
    @DisplayName("spending in an unbudgeted category is surfaced separately, not hidden")
    void surfacesUnbudgetedSpending() {
        givenLimits(Budget.create(USER, GROCERIES, AUGUST, new BigDecimal("8000.00")));
        givenSpend(
                spend(GROCERIES, "Groceries", Bucket.NEEDS, "6000.00"),
                spend(DINING, "Dining Out", Bucket.WANTS, "4500.00"));

        BudgetOverview overview = budgetService.overview(USER, AUGUST);

        assertThat(overview.budgeted()).extracting(BudgetLine::categoryName).containsExactly("Groceries");
        assertThat(overview.unbudgeted()).extracting(BudgetLine::categoryName).containsExactly("Dining Out");
        // The number that quietly breaks a budget if it is not shown.
        assertThat(overview.unbudgetedSpend()).isEqualByComparingTo("4500.00");
        assertThat(lineFor(overview.unbudgeted(), "Dining Out").limitAmount()).isNull();
    }

    @Test
    @DisplayName("untouched categories with no budget are omitted rather than listed as noise")
    void omitsUntouchedUnbudgetedCategories() {
        givenLimits();
        givenSpend(
                spend(DINING, "Dining Out", Bucket.WANTS, "0"),
                spend(RENT, "Rent", Bucket.NEEDS, "15000.00"));

        BudgetOverview overview = budgetService.overview(USER, AUGUST);

        assertThat(overview.unbudgeted()).extracting(BudgetLine::categoryName).containsExactly("Rent");
    }

    @Test
    @DisplayName("income categories are never budgeted")
    void ignoresIncomeCategories() {
        givenLimits();
        when(ledger.spendByCategory(eq(USER), any(), any())).thenReturn(List.of(
                new CategoryTotal(SALARY, "Salary", "#0f8a6c", Kind.INCOME, null, new BigDecimal("45000")),
                spend(RENT, "Rent", Bucket.NEEDS, "15000.00")));
        lenient().when(ledger.summary(eq(USER), anyString())).thenReturn(
                new Summary("2026-08", new BigDecimal("45000"), BigDecimal.ZERO, BigDecimal.ZERO));

        BudgetOverview overview = budgetService.overview(USER, AUGUST);

        assertThat(overview.budgeted()).isEmpty();
        assertThat(overview.unbudgeted()).extracting(BudgetLine::categoryName).containsExactly("Rent");
    }

    @Test
    @DisplayName("totals cover budgeted lines only, with unbudgeted spend reported apart")
    void totalsSeparateBudgetedFromUnbudgeted() {
        givenLimits(
                Budget.create(USER, GROCERIES, AUGUST, new BigDecimal("8000.00")),
                Budget.create(USER, RENT, AUGUST, new BigDecimal("15000.00")));
        givenSpend(
                spend(GROCERIES, "Groceries", Bucket.NEEDS, "6000.00"),
                spend(RENT, "Rent", Bucket.NEEDS, "15000.00"),
                spend(DINING, "Dining Out", Bucket.WANTS, "4500.00"));

        BudgetOverview overview = budgetService.overview(USER, AUGUST);

        assertThat(overview.totalLimit()).isEqualByComparingTo("23000.00");
        assertThat(overview.totalSpent()).isEqualByComparingTo("21000.00");
        assertThat(overview.totalRemaining()).isEqualByComparingTo("2000.00");
        assertThat(overview.unbudgetedSpend()).isEqualByComparingTo("4500.00");
        assertThat(overview.income()).isEqualByComparingTo("45000");
    }

    @Test
    @DisplayName("budgeted lines come back worst-standing first")
    void sortsWorstFirst() {
        givenLimits(
                Budget.create(USER, GROCERIES, AUGUST, new BigDecimal("8000.00")),
                Budget.create(USER, RENT, AUGUST, new BigDecimal("15000.00")));
        givenSpend(
                spend(GROCERIES, "Groceries", Bucket.NEEDS, "2000.00"),
                spend(RENT, "Rent", Bucket.NEEDS, "15000.00"));

        BudgetOverview overview = budgetService.overview(USER, AUGUST);

        assertThat(overview.budgeted()).extracting(BudgetLine::categoryName)
                .containsExactly("Rent", "Groceries");
    }

    @Test
    @DisplayName("upsert updates an existing limit rather than inserting a duplicate")
    void upsertUpdatesExisting() {
        Budget existing = Budget.create(USER, GROCERIES, AUGUST, new BigDecimal("8000.00"));
        when(budgets.findByUserIdAndCategoryIdAndPeriodMonth(USER, GROCERIES, AUGUST.atDay(1)))
                .thenReturn(Optional.of(existing));

        budgetService.upsert(USER, AUGUST, new BudgetRequest(GROCERIES, new BigDecimal("9500.00")));

        assertThat(existing.getLimitAmount()).isEqualByComparingTo("9500.00");
        verify(budgets, org.mockito.Mockito.never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("upsert inserts when no limit exists yet")
    void upsertInsertsWhenAbsent() {
        when(budgets.findByUserIdAndCategoryIdAndPeriodMonth(USER, GROCERIES, AUGUST.atDay(1)))
                .thenReturn(Optional.empty());

        budgetService.upsert(USER, AUGUST, new BudgetRequest(GROCERIES, new BigDecimal("9500.00")));

        verify(budgets).saveAndFlush(any(Budget.class));
    }

    @Test
    @DisplayName("copying the previous month carries every limit forward")
    void copiesPreviousMonth() {
        YearMonth july = AUGUST.minusMonths(1);
        when(budgets.findByUserIdAndPeriodMonth(USER, july.atDay(1))).thenReturn(List.of(
                Budget.create(USER, GROCERIES, july, new BigDecimal("8000.00")),
                Budget.create(USER, RENT, july, new BigDecimal("15000.00"))));
        when(budgets.findByUserIdAndCategoryIdAndPeriodMonth(eq(USER), any(), eq(AUGUST.atDay(1))))
                .thenReturn(Optional.empty());

        List<Budget> saved = new ArrayList<>();
        when(budgets.saveAndFlush(any(Budget.class))).thenAnswer(call -> {
            Budget budget = call.getArgument(0, Budget.class);
            saved.add(budget);
            return budget;
        });

        assertThat(budgetService.copyFromPreviousMonth(USER, AUGUST)).isEqualTo(2);
        assertThat(saved).extracting(Budget::getMonth).containsOnly(AUGUST);
        assertThat(saved).extracting(Budget::getLimitAmount)
                .containsExactlyInAnyOrder(new BigDecimal("8000.00"), new BigDecimal("15000.00"));
    }

    @Test
    @DisplayName("copying reports 404 when the previous month has no budget")
    void copyFailsWithoutPreviousMonth() {
        when(budgets.findByUserIdAndPeriodMonth(USER, AUGUST.minusMonths(1).atDay(1)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> budgetService.copyFromPreviousMonth(USER, AUGUST))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("2026-07");
    }

    @Test
    @DisplayName("deleting a budget that does not exist is a 404")
    void deleteRequiresExistingBudget() {
        when(budgets.findByUserIdAndCategoryIdAndPeriodMonth(USER, GROCERIES, AUGUST.atDay(1)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.delete(USER, AUGUST, GROCERIES))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("the ledger is queried for exactly the selected month")
    void queriesTheSelectedMonthOnly() {
        givenLimits();
        givenSpend();

        budgetService.overview(USER, AUGUST);

        verify(ledger).spendByCategory(USER, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
    }
}
