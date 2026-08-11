package ph.pesowise.planning.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ph.pesowise.planning.api.RecurringDtos.BillOverview;
import ph.pesowise.planning.api.RecurringDtos.RunSummary;
import ph.pesowise.planning.domain.RecurringBill;
import ph.pesowise.planning.domain.RecurringBill.Frequency;
import ph.pesowise.planning.domain.RecurringRun;
import ph.pesowise.planning.repo.RecurringBillRepository;
import ph.pesowise.planning.repo.RecurringRunRepository;
import ph.pesowise.planning.service.RecurringOccurrences.Result;
import ph.pesowise.planning.service.RecurringOccurrences.Settlement;
import ph.pesowise.planning.web.ConflictException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecurringServiceTest {

    private static final UUID USER = UUID.fromString("3f1c9e2a-0000-4000-8000-000000000001");

    @Mock
    private RecurringBillRepository bills;

    @Mock
    private RecurringRunRepository runs;

    @Mock
    private RecurringOccurrences occurrences;

    private RecurringService recurringService;

    @BeforeEach
    void setUp() {
        recurringService = new RecurringService(bills, runs, occurrences);
    }

    private static RecurringBill bill(
            String name, Frequency frequency, String amount, LocalDate due, boolean autoPost) {
        return RecurringBill.create(USER, name, UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal(amount), frequency, due, autoPost, null);
    }

    private static Result posted() {
        return new Result(Settlement.POSTED,
                RecurringRun.claim(USER, UUID.randomUUID(), LocalDate.of(2026, 8, 5)));
    }

    private static Result notDue() {
        return new Result(Settlement.NOT_DUE, null);
    }

    @Test
    @DisplayName("an auto-posting bill is settled by the pass")
    void autoPostingBillIsSettled() {
        RecurringBill auto = bill("Rent", Frequency.MONTHLY, "15000", LocalDate.of(2026, 8, 5), true);
        when(bills.findByActiveTrueAndNextRunDateLessThanEqual(any())).thenReturn(List.of(auto));
        when(occurrences.settleDue(eq(auto.getId()), any())).thenReturn(posted(), notDue());
        lenient().when(bills.findById(auto.getId())).thenReturn(Optional.of(auto));

        RunSummary summary = recurringService.runDueBills();

        assertThat(summary.posted()).isEqualTo(1);
        assertThat(summary.flagged()).isZero();
    }

    @Test
    @DisplayName("a bill with auto-post off is flagged, never posted without asking")
    void manualBillIsOnlyFlagged() {
        RecurringBill manual = bill("Meralco", Frequency.MONTHLY, "3200", LocalDate.of(2026, 8, 5), false);
        when(bills.findByActiveTrueAndNextRunDateLessThanEqual(any())).thenReturn(List.of(manual));

        RunSummary summary = recurringService.runDueBills();

        assertThat(summary.flagged()).isEqualTo(1);
        assertThat(summary.posted()).isZero();
        // The point: a bill whose amount varies is never charged behind the user's back.
        verify(occurrences, never()).settleDue(any(), any());
    }

    @Test
    @DisplayName("missed occurrences are caught up, not dropped")
    void catchesUpOnMissedOccurrences() {
        RecurringBill behind = bill("Rent", Frequency.MONTHLY, "15000", LocalDate.of(2026, 5, 5), true);
        when(bills.findByActiveTrueAndNextRunDateLessThanEqual(any())).thenReturn(List.of(behind));
        // Three missed months, then no longer due.
        when(occurrences.settleDue(eq(behind.getId()), any()))
                .thenReturn(posted(), posted(), posted(), notDue());
        lenient().when(bills.findById(behind.getId())).thenReturn(Optional.of(behind));

        RunSummary summary = recurringService.runDueBills();

        // Rent that was due really was due; skipping it would understate those months.
        assertThat(summary.posted()).isEqualTo(3);
    }

    @Test
    @DisplayName("catch-up stops at the cap and says so rather than looking finished")
    void catchUpCapIsReported() {
        RecurringBill stale = bill("Rent", Frequency.MONTHLY, "15000", LocalDate.of(2020, 1, 5), true);
        when(bills.findByActiveTrueAndNextRunDateLessThanEqual(any())).thenReturn(List.of(stale));
        // Always still due — a bill abandoned for years.
        when(occurrences.settleDue(eq(stale.getId()), any())).thenReturn(posted());
        when(bills.findById(stale.getId())).thenReturn(Optional.of(stale));

        RunSummary summary = recurringService.runDueBills();

        assertThat(summary.posted()).isEqualTo(RecurringService.MAX_CATCH_UP);
        verify(occurrences, times(RecurringService.MAX_CATCH_UP)).settleDue(eq(stale.getId()), any());
        // Never silent about a truncated result.
        assertThat(summary.notes()).singleElement().asString()
                .contains("Rent").contains("still outstanding");
    }

    @Test
    @DisplayName("occurrences already recorded are counted apart from newly posted ones")
    void countsAlreadyRecordedSeparately() {
        RecurringBill restarted = bill("Rent", Frequency.MONTHLY, "15000", LocalDate.of(2026, 8, 5), true);
        when(bills.findByActiveTrueAndNextRunDateLessThanEqual(any())).thenReturn(List.of(restarted));
        when(occurrences.settleDue(eq(restarted.getId()), any()))
                .thenReturn(new Result(Settlement.ALREADY_RECORDED, null), notDue());
        lenient().when(bills.findById(restarted.getId())).thenReturn(Optional.of(restarted));

        RunSummary summary = recurringService.runDueBills();

        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.posted()).isZero();
    }

    @Test
    @DisplayName("one bill's failure does not stop the bills after it, and is reported")
    void oneFailureDoesNotStopThePass() {
        RecurringBill broken = bill("Broken", Frequency.MONTHLY, "100", LocalDate.of(2026, 8, 1), true);
        RecurringBill fine = bill("Fine", Frequency.MONTHLY, "200", LocalDate.of(2026, 8, 2), true);
        when(bills.findByActiveTrueAndNextRunDateLessThanEqual(any()))
                .thenReturn(List.of(broken, fine));
        when(occurrences.settleDue(eq(broken.getId()), any()))
                .thenThrow(new IllegalStateException("archived category"));
        when(occurrences.settleDue(eq(fine.getId()), any())).thenReturn(posted(), notDue());
        lenient().when(bills.findById(fine.getId())).thenReturn(Optional.of(fine));

        RunSummary summary = recurringService.runDueBills();

        // The healthy bill still went through — a broken one must not hold up the queue.
        assertThat(summary.posted()).isEqualTo(1);
        // And the failure is surfaced rather than swallowed.
        assertThat(summary.notes()).singleElement().asString()
                .contains("Broken").contains("retried");
    }

    @Test
    @DisplayName("weekly and yearly bills are normalised to a monthly figure")
    void normalisesToMonthlyTotal() {
        RecurringBill monthly = bill("Rent", Frequency.MONTHLY, "15000.00", LocalDate.of(2026, 9, 5), true);
        RecurringBill weekly = bill("Palengke", Frequency.WEEKLY, "1200.00", LocalDate.of(2026, 8, 17), true);
        RecurringBill yearly = bill("Domain", Frequency.YEARLY, "1200.00", LocalDate.of(2027, 1, 1), true);
        when(bills.findByUserIdOrderByActiveDescNextRunDateAsc(USER))
                .thenReturn(List.of(monthly, weekly, yearly));

        BillOverview overview = recurringService.list(USER);

        // 15000 + (1200 * 52 / 12 = 5200) + (1200 / 12 = 100). Using 4 weeks per month would
        // overstate the weekly bill by about 8%.
        assertThat(overview.monthlyTotal()).isEqualByComparingTo("20300.00");
    }

    @Test
    @DisplayName("inactive bills are excluded from the monthly total")
    void inactiveBillsExcludedFromTotal() {
        RecurringBill paused = bill("Gym", Frequency.MONTHLY, "1500", LocalDate.of(2026, 9, 1), true);
        paused.setActive(false);
        when(bills.findByUserIdOrderByActiveDescNextRunDateAsc(USER)).thenReturn(List.of(paused));

        assertThat(recurringService.list(USER).monthlyTotal()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("due bills are surfaced separately so the UI need not filter")
    void surfacesDueBills() {
        RecurringBill due = bill("Meralco", Frequency.MONTHLY, "3200", LocalDate.now().minusDays(2), false);
        RecurringBill later = bill("Rent", Frequency.MONTHLY, "15000", LocalDate.now().plusDays(20), true);
        when(bills.findByUserIdOrderByActiveDescNextRunDateAsc(USER)).thenReturn(List.of(due, later));

        BillOverview overview = recurringService.list(USER);

        assertThat(overview.dueNow()).singleElement()
                .satisfies(bill -> assertThat(bill.name()).isEqualTo("Meralco"));
        assertThat(overview.bills()).hasSize(2);
    }

    @Test
    @DisplayName("confirming a bill that is not due yet is a conflict")
    void postNowRejectsBillNotYetDue() {
        RecurringBill future = bill("Rent", Frequency.MONTHLY, "15000", LocalDate.now().plusDays(10), false);
        when(bills.findByIdAndUserId(future.getId(), USER)).thenReturn(Optional.of(future));

        assertThatThrownBy(() -> recurringService.postNow(USER, future.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not due until");

        verify(occurrences, never()).settleDue(any(), any());
    }

    @Test
    @DisplayName("confirming an occurrence already recorded is a conflict, not a duplicate")
    void postNowRejectsAlreadyRecorded() {
        RecurringBill due = bill("Meralco", Frequency.MONTHLY, "3200", LocalDate.now().minusDays(1), false);
        when(bills.findByIdAndUserId(due.getId(), USER)).thenReturn(Optional.of(due));
        when(occurrences.settleDue(eq(due.getId()), any()))
                .thenReturn(new Result(Settlement.ALREADY_RECORDED, null));

        assertThatThrownBy(() -> recurringService.postNow(USER, due.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already been recorded");
    }
}
