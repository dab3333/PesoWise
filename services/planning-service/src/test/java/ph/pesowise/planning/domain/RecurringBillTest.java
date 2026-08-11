package ph.pesowise.planning.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ph.pesowise.planning.domain.RecurringBill.Frequency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cursor advancement, which is where a recurring bill quietly goes wrong. The month-end cases are
 * the point: a bill due on the 31st must not drift to the 28th for the rest of its life after passing
 * through February.
 */
class RecurringBillTest {

    private static final UUID USER = UUID.fromString("3f1c9e2a-0000-4000-8000-000000000001");

    private static RecurringBill bill(Frequency frequency, LocalDate firstDue) {
        return RecurringBill.create(
                USER, "Rent", UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("15000.00"), frequency, firstDue, true, null);
    }

    @Test
    @DisplayName("a weekly bill advances seven days")
    void weeklyAdvancesSevenDays() {
        RecurringBill weekly = bill(Frequency.WEEKLY, LocalDate.of(2026, 8, 3));

        weekly.advance();
        assertThat(weekly.getNextRunDate()).isEqualTo(LocalDate.of(2026, 8, 10));

        weekly.advance();
        assertThat(weekly.getNextRunDate()).isEqualTo(LocalDate.of(2026, 8, 17));
    }

    @Test
    @DisplayName("a weekly bill crosses a month boundary correctly")
    void weeklyCrossesMonths() {
        RecurringBill weekly = bill(Frequency.WEEKLY, LocalDate.of(2026, 8, 28));

        weekly.advance();
        assertThat(weekly.getNextRunDate()).isEqualTo(LocalDate.of(2026, 9, 4));
    }

    @Test
    @DisplayName("a monthly bill keeps its day of the month")
    void monthlyKeepsItsDay() {
        RecurringBill monthly = bill(Frequency.MONTHLY, LocalDate.of(2026, 8, 5));

        monthly.advance();
        assertThat(monthly.getNextRunDate()).isEqualTo(LocalDate.of(2026, 9, 5));

        monthly.advance();
        assertThat(monthly.getNextRunDate()).isEqualTo(LocalDate.of(2026, 10, 5));
    }

    @Test
    @DisplayName("a bill due on the 31st clamps to a short month, then RETURNS to the 31st")
    void monthlyOnThe31stDoesNotDrift() {
        // The case that matters. Advancing from the clamped date instead of the anchor would leave
        // this bill on the 28th or 30th forever.
        RecurringBill monthly = bill(Frequency.MONTHLY, LocalDate.of(2026, 1, 31));

        monthly.advance();
        assertThat(monthly.getNextRunDate()).isEqualTo(LocalDate.of(2026, 2, 28));

        monthly.advance();
        assertThat(monthly.getNextRunDate()).isEqualTo(LocalDate.of(2026, 3, 31));

        monthly.advance();
        assertThat(monthly.getNextRunDate()).isEqualTo(LocalDate.of(2026, 4, 30));

        monthly.advance();
        assertThat(monthly.getNextRunDate()).isEqualTo(LocalDate.of(2026, 5, 31));
    }

    @Test
    @DisplayName("a bill due on the 31st reaches 29 February in a leap year")
    void monthlyOnThe31stInALeapYear() {
        RecurringBill monthly = bill(Frequency.MONTHLY, LocalDate.of(2028, 1, 31));

        monthly.advance();
        assertThat(monthly.getNextRunDate()).isEqualTo(LocalDate.of(2028, 2, 29));

        monthly.advance();
        assertThat(monthly.getNextRunDate()).isEqualTo(LocalDate.of(2028, 3, 31));
    }

    @Test
    @DisplayName("a bill due on the 30th clamps in February and returns to the 30th")
    void monthlyOnThe30th() {
        RecurringBill monthly = bill(Frequency.MONTHLY, LocalDate.of(2026, 1, 30));

        monthly.advance();
        assertThat(monthly.getNextRunDate()).isEqualTo(LocalDate.of(2026, 2, 28));

        monthly.advance();
        assertThat(monthly.getNextRunDate()).isEqualTo(LocalDate.of(2026, 3, 30));
    }

    @Test
    @DisplayName("a monthly bill crosses the year boundary")
    void monthlyCrossesYears() {
        RecurringBill monthly = bill(Frequency.MONTHLY, LocalDate.of(2026, 12, 15));

        monthly.advance();
        assertThat(monthly.getNextRunDate()).isEqualTo(LocalDate.of(2027, 1, 15));
    }

    @Test
    @DisplayName("a yearly bill advances twelve months")
    void yearlyAdvancesOneYear() {
        RecurringBill yearly = bill(Frequency.YEARLY, LocalDate.of(2026, 6, 30));

        yearly.advance();
        assertThat(yearly.getNextRunDate()).isEqualTo(LocalDate.of(2027, 6, 30));
    }

    @Test
    @DisplayName("a yearly bill on 29 February clamps to the 28th in a non-leap year")
    void yearlyOnLeapDay() {
        RecurringBill yearly = bill(Frequency.YEARLY, LocalDate.of(2028, 2, 29));

        yearly.advance();
        assertThat(yearly.getNextRunDate()).isEqualTo(LocalDate.of(2029, 2, 28));
    }

    @Test
    @DisplayName("only monthly bills carry an anchor day")
    void onlyMonthlyHasAnAnchorDay() {
        assertThat(bill(Frequency.MONTHLY, LocalDate.of(2026, 8, 17)).getDayOfPeriod()).isEqualTo((short) 17);
        assertThat(bill(Frequency.WEEKLY, LocalDate.of(2026, 8, 17)).getDayOfPeriod()).isNull();
        assertThat(bill(Frequency.YEARLY, LocalDate.of(2026, 8, 17)).getDayOfPeriod()).isNull();
    }

    @Test
    @DisplayName("rescheduling re-anchors a monthly bill's day")
    void reschedulingReAnchors() {
        RecurringBill monthly = bill(Frequency.MONTHLY, LocalDate.of(2026, 8, 5));

        monthly.rescheduleTo(LocalDate.of(2026, 8, 20));
        assertThat(monthly.getDayOfPeriod()).isEqualTo((short) 20);

        monthly.advance();
        assertThat(monthly.getNextRunDate()).isEqualTo(LocalDate.of(2026, 9, 20));
    }

    @Test
    @DisplayName("a bill is due on and after its scheduled date, but not before")
    void dueOnOrAfterTheScheduledDate() {
        RecurringBill monthly = bill(Frequency.MONTHLY, LocalDate.of(2026, 8, 10));

        assertThat(monthly.isDueOn(LocalDate.of(2026, 8, 9))).isFalse();
        assertThat(monthly.isDueOn(LocalDate.of(2026, 8, 10))).isTrue();
        assertThat(monthly.isDueOn(LocalDate.of(2026, 9, 1))).isTrue();
    }

    @Test
    @DisplayName("an inactive bill is never due")
    void inactiveBillIsNeverDue() {
        RecurringBill monthly = bill(Frequency.MONTHLY, LocalDate.of(2026, 8, 10));
        monthly.setActive(false);

        assertThat(monthly.isDueOn(LocalDate.of(2026, 12, 1))).isFalse();
    }
}
