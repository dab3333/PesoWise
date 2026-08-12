package ph.pesowise.planning.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ph.pesowise.planning.domain.Debt.Direction;
import ph.pesowise.planning.domain.Debt.InterestMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The interest math, in isolation from persistence and the scheduler — {@code r = rate / 100 /
 * 12}, SIMPLE accruing on principal only, COMPOUND folding in whatever interest is still unpaid.
 */
class DebtTest {

    private static final UUID USER = UUID.fromString("3f1c9e2a-0000-4000-8000-000000000001");

    private static Debt debt(String principal, String rate, InterestMethod method, LocalDate start) {
        return Debt.create(USER, "Loan", Direction.OWED_BY_ME, null,
                new BigDecimal(principal), rate == null ? null : new BigDecimal(rate), method, start, null);
    }

    @Test
    @DisplayName("a debt with no interest method never accrues, whatever the rate")
    void noInterestMethodMeansNoAccrual() {
        Debt debt = debt("10000.00", "12.000", null, LocalDate.of(2026, 1, 1));

        assertThat(debt.hasInterest()).isFalse();
        assertThat(debt.isAccrualDueOn(LocalDate.of(2027, 1, 1))).isFalse();
    }

    @Test
    @DisplayName("an accrual period is due only once its whole month has elapsed")
    void periodIsDueOnlyAfterItsMonthElapses() {
        Debt debt = debt("10000.00", "12.000", InterestMethod.SIMPLE, LocalDate.of(2026, 1, 1));

        // January is still in progress on the 31st — not due yet.
        assertThat(debt.isAccrualDueOn(LocalDate.of(2026, 1, 31))).isFalse();
        // February 1st: January has fully elapsed.
        assertThat(debt.isAccrualDueOn(LocalDate.of(2026, 2, 1))).isTrue();
    }

    @Test
    @DisplayName("SIMPLE accrues on outstanding principal only, one period")
    void simpleAccrualOnePeriod() {
        Debt debt = debt("10000.00", "12.000", InterestMethod.SIMPLE, LocalDate.of(2026, 1, 1));

        // r = 12 / 100 / 12 = 0.01; 10000 * 0.01 = 100.00
        assertThat(debt.calculateAccrual()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("SIMPLE does not compound: the same balance accrues the same amount every period")
    void simpleAccrualDoesNotCompound() {
        Debt debt = debt("10000.00", "12.000", InterestMethod.SIMPLE, LocalDate.of(2026, 1, 1));

        debt.applyAccrual(debt.calculateAccrual(), debt.nextAccrualPeriod());
        assertThat(debt.getAccruedInterest()).isEqualByComparingTo("100.00");

        // Balance never moved, so the second period accrues the same 100.00, not more.
        assertThat(debt.calculateAccrual()).isEqualByComparingTo("100.00");
        debt.applyAccrual(debt.calculateAccrual(), debt.nextAccrualPeriod());
        assertThat(debt.getAccruedInterest()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("COMPOUND accrues on principal plus whatever interest is still unpaid")
    void compoundAccrualFoldsInUnpaidInterest() {
        Debt debt = debt("10000.00", "12.000", InterestMethod.COMPOUND, LocalDate.of(2026, 1, 1));

        // First period: nothing accrued yet, so it matches SIMPLE — 100.00.
        BigDecimal first = debt.calculateAccrual();
        assertThat(first).isEqualByComparingTo("100.00");
        debt.applyAccrual(first, debt.nextAccrualPeriod());

        // Second period: base is now 10000 + 100 = 10100, so 10100 * 0.01 = 101.00.
        BigDecimal second = debt.calculateAccrual();
        assertThat(second).isEqualByComparingTo("101.00");
        debt.applyAccrual(second, debt.nextAccrualPeriod());

        assertThat(debt.getAccruedInterest()).isEqualByComparingTo("201.00");
    }

    @Test
    @DisplayName("the cursor advances to the month after the one just accrued")
    void cursorAdvancesAfterAccrual() {
        Debt debt = debt("10000.00", "12.000", InterestMethod.SIMPLE, LocalDate.of(2026, 1, 15));

        assertThat(debt.nextAccrualPeriod()).isEqualTo(LocalDate.of(2026, 1, 1));
        debt.applyAccrual(debt.calculateAccrual(), debt.nextAccrualPeriod());
        assertThat(debt.nextAccrualPeriod()).isEqualTo(LocalDate.of(2026, 2, 1));
    }

    @Test
    @DisplayName("advancing the cursor without accruing skips the period without adding interest")
    void advancingCursorWithoutAccrualAddsNothing() {
        Debt debt = debt("10000.00", "12.000", InterestMethod.SIMPLE, LocalDate.of(2026, 1, 1));

        debt.advanceAccrualCursor(debt.nextAccrualPeriod());

        assertThat(debt.getAccruedInterest()).isEqualByComparingTo("0");
        assertThat(debt.nextAccrualPeriod()).isEqualTo(LocalDate.of(2026, 2, 1));
    }

    @Test
    @DisplayName("interest allocation clears accrued interest before touching principal")
    void allocationIsInterestFirst() {
        Debt debt = debt("10000.00", "12.000", InterestMethod.SIMPLE, LocalDate.of(2026, 1, 1));
        debt.applyAccrual(new BigDecimal("100.00"), LocalDate.of(2026, 1, 1));

        BigDecimal[] partial = debt.allocate(new BigDecimal("40.00"));
        assertThat(partial[0]).isEqualByComparingTo("0"); // principal
        assertThat(partial[1]).isEqualByComparingTo("40.00"); // interest

        BigDecimal[] full = debt.allocate(new BigDecimal("300.00"));
        assertThat(full[0]).isEqualByComparingTo("200.00");
        assertThat(full[1]).isEqualByComparingTo("100.00");
    }
}
