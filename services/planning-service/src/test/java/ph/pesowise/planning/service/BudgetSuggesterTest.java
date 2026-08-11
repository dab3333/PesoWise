package ph.pesowise.planning.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * The suggester is pure arithmetic over a Feign response, and it is the one place where a rounding
 * mistake would show as "the numbers don't add up" on screen. Covered thoroughly here.
 */
@ExtendWith(MockitoExtension.class)
class BudgetSuggesterTest {

    private static final UUID USER = UUID.fromString("3f1c9e2a-0000-4000-8000-000000000001");
    private static final YearMonth AUGUST = YearMonth.of(2026, 8);

    @Mock
    private LedgerClient ledger;

    private BudgetSuggester suggester;

    @BeforeEach
    void setUp() {
        suggester = new BudgetSuggester(ledger);
    }

    private static CategoryTotal category(String name, Bucket bucket, String total) {
        return new CategoryTotal(
                UUID.randomUUID(), name, "#0f8a6c", Kind.EXPENSE, bucket, new BigDecimal(total));
    }

    private void givenHistory(CategoryTotal... rows) {
        when(ledger.spendByCategory(eq(USER), any(), any())).thenReturn(List.of(rows));
    }

    private static BigDecimal amountFor(SuggestionResponse response, String categoryName) {
        return response.lines().stream()
                .filter(line -> line.categoryName().equals(categoryName))
                .map(SuggestedLine::limitAmount)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no line for " + categoryName));
    }

    private static BigDecimal bucketTotal(SuggestionResponse response, Bucket bucket) {
        return response.lines().stream()
                .filter(line -> line.bucket() == bucket)
                .map(SuggestedLine::limitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    @DisplayName("the three bucket pools are 70/20/10 of the expected income")
    void bucketPoolsFollowTheMethod() {
        givenHistory(category("Rent", Bucket.NEEDS, "15000"));

        SuggestionResponse response = suggester.suggest(USER, AUGUST, new BigDecimal("30000.00"));

        assertThat(response.buckets()).extracting(BucketAllocation::bucket)
                .containsExactly(Bucket.NEEDS, Bucket.WANTS, Bucket.SAVINGS);
        assertThat(response.buckets()).extracting(BucketAllocation::amount)
                .containsExactly(
                        new BigDecimal("21000.00"), new BigDecimal("6000.00"), new BigDecimal("3000.00"));
    }

    @Test
    @DisplayName("a bucket's pool is split in proportion to historical spending")
    void splitsProportionallyToHistory() {
        // Needs history: 30000 total, so Rent takes 3/4 and Groceries 1/4 of the 21000 pool.
        givenHistory(
                category("Rent", Bucket.NEEDS, "22500"),
                category("Groceries", Bucket.NEEDS, "7500"));

        SuggestionResponse response = suggester.suggest(USER, AUGUST, new BigDecimal("30000.00"));

        assertThat(amountFor(response, "Rent")).isEqualByComparingTo("15750.00");
        assertThat(amountFor(response, "Groceries")).isEqualByComparingTo("5250.00");
    }

    @Test
    @DisplayName("with no history in a bucket, its pool is split evenly")
    void splitsEvenlyWithoutHistory() {
        givenHistory(
                category("Dining Out", Bucket.WANTS, "0"),
                category("Shopping", Bucket.WANTS, "0"));

        SuggestionResponse response = suggester.suggest(USER, AUGUST, new BigDecimal("30000.00"));

        // 6000 pool, two categories, no history to weight by.
        assertThat(amountFor(response, "Dining Out")).isEqualByComparingTo("3000.00");
        assertThat(amountFor(response, "Shopping")).isEqualByComparingTo("3000.00");
        assertThat(response.lines()).allSatisfy(line -> assertThat(line.fromHistory()).isFalse());
    }

    @Test
    @DisplayName("lines sourced from history are flagged, so the UI can say why")
    void flagsHistoryBackedLines() {
        givenHistory(
                category("Rent", Bucket.NEEDS, "15000"),
                category("Health", Bucket.NEEDS, "0"));

        SuggestionResponse response = suggester.suggest(USER, AUGUST, new BigDecimal("30000.00"));

        assertThat(amountFor(response, "Rent")).isEqualByComparingTo("21000.00");
        assertThat(response.lines()).singleElement()
                .satisfies(line -> assertThat(line.fromHistory()).isTrue());
    }

    @Test
    @DisplayName("each bucket's lines sum exactly to its pool, despite rounding")
    void linesSumExactlyToTheirPool() {
        // 21000 / 3 does not divide cleanly at 2dp — this is the rounding-drift case.
        givenHistory(
                category("Rent", Bucket.NEEDS, "1"),
                category("Groceries", Bucket.NEEDS, "1"),
                category("Utilities", Bucket.NEEDS, "1"),
                category("Dining Out", Bucket.WANTS, "1"),
                category("Shopping", Bucket.WANTS, "1"),
                category("Savings", Bucket.SAVINGS, "1"));

        SuggestionResponse response = suggester.suggest(USER, AUGUST, new BigDecimal("10000.00"));

        assertThat(bucketTotal(response, Bucket.NEEDS)).isEqualByComparingTo("7000.00");
        assertThat(bucketTotal(response, Bucket.WANTS)).isEqualByComparingTo("2000.00");
        assertThat(bucketTotal(response, Bucket.SAVINGS)).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("the whole suggestion sums to the expected income")
    void totalMatchesIncome() {
        givenHistory(
                category("Rent", Bucket.NEEDS, "12345.67"),
                category("Groceries", Bucket.NEEDS, "3210.11"),
                category("Dining Out", Bucket.WANTS, "999.99"),
                category("Savings", Bucket.SAVINGS, "1500"));

        BigDecimal income = new BigDecimal("43210.99");
        SuggestionResponse response = suggester.suggest(USER, AUGUST, income);

        BigDecimal total = response.lines().stream()
                .map(SuggestedLine::limitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // The 70/20/10 pools are each rounded to 2dp, so the total can differ from income by at
        // most one centavo per bucket.
        assertThat(total).isCloseTo(income, org.assertj.core.data.Offset.offset(new BigDecimal("0.03")));
    }

    @Test
    @DisplayName("income categories are ignored — the method divides spending, not earnings")
    void ignoresIncomeCategories() {
        when(ledger.spendByCategory(eq(USER), any(), any())).thenReturn(List.of(
                new CategoryTotal(UUID.randomUUID(), "Salary", "#0f8a6c", Kind.INCOME, null,
                        new BigDecimal("45000")),
                category("Rent", Bucket.NEEDS, "15000")));

        SuggestionResponse response = suggester.suggest(USER, AUGUST, new BigDecimal("30000.00"));

        assertThat(response.lines()).extracting(SuggestedLine::categoryName).containsExactly("Rent");
    }

    @Test
    @DisplayName("history excludes the month being budgeted, so a part-finished month cannot skew it")
    void historyWindowEndsBeforeTheTargetMonth() {
        givenHistory(category("Rent", Bucket.NEEDS, "15000"));

        suggester.suggest(USER, AUGUST, new BigDecimal("30000.00"));

        // Three months ending July 2026: 1 May to 31 July.
        org.mockito.Mockito.verify(ledger).spendByCategory(
                USER, java.time.LocalDate.of(2026, 5, 1), java.time.LocalDate.of(2026, 7, 31));
    }

    @Test
    @DisplayName("omitting the income estimates it from last month's actual")
    void estimatesIncomeFromLastMonth() {
        givenHistory(category("Rent", Bucket.NEEDS, "15000"));
        when(ledger.summary(eq(USER), anyString())).thenReturn(
                new Summary("2026-07", new BigDecimal("42000.00"), BigDecimal.ZERO, BigDecimal.ZERO));

        SuggestionResponse response = suggester.suggest(USER, AUGUST, null);

        assertThat(response.incomeWasEstimated()).isTrue();
        assertThat(response.expectedIncome()).isEqualByComparingTo("42000.00");
        assertThat(amountFor(response, "Rent")).isEqualByComparingTo("29400.00");
    }

    @Test
    @DisplayName("with no income to estimate from, the user is asked for one")
    void rejectsWhenIncomeCannotBeEstimated() {
        lenient().when(ledger.spendByCategory(eq(USER), any(), any())).thenReturn(List.of());
        when(ledger.summary(eq(USER), anyString())).thenReturn(
                new Summary("2026-07", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        assertThatThrownBy(() -> suggester.suggest(USER, AUGUST, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expected monthly income");
    }

    @Test
    @DisplayName("zero-limit lines are dropped rather than saved as invalid budgets")
    void dropsZeroLines() {
        // Only Rent has history, so every other needs category would allocate to 0.00.
        givenHistory(
                category("Rent", Bucket.NEEDS, "15000"),
                category("Groceries", Bucket.NEEDS, "0"),
                category("Utilities", Bucket.NEEDS, "0"));

        SuggestionResponse response = suggester.suggest(USER, AUGUST, new BigDecimal("30000.00"));

        assertThat(response.lines()).extracting(SuggestedLine::categoryName).containsExactly("Rent");
        assertThat(response.lines())
                .allSatisfy(line -> assertThat(line.limitAmount()).isGreaterThan(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("lines come back largest first")
    void sortsLargestFirst() {
        givenHistory(
                category("Groceries", Bucket.NEEDS, "5000"),
                category("Rent", Bucket.NEEDS, "20000"));

        SuggestionResponse response = suggester.suggest(USER, AUGUST, new BigDecimal("30000.00"));

        assertThat(response.lines()).extracting(SuggestedLine::categoryName)
                .startsWith("Rent", "Groceries");
    }
}
