package ph.pesowise.planning.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ph.pesowise.planning.api.DebtDtos.AccrualSummary;
import ph.pesowise.planning.domain.Debt;
import ph.pesowise.planning.domain.Debt.Direction;
import ph.pesowise.planning.domain.Debt.InterestMethod;
import ph.pesowise.planning.domain.DebtInterestAccrual;
import ph.pesowise.planning.repo.DebtRepository;
import ph.pesowise.planning.service.DebtInterestAccruals.Result;
import ph.pesowise.planning.service.DebtInterestAccruals.Settlement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DebtInterestServiceTest {

    private static final UUID USER = UUID.fromString("3f1c9e2a-0000-4000-8000-000000000001");

    @Mock
    private DebtRepository debts;

    @Mock
    private DebtInterestAccruals accruals;

    private DebtInterestService debtInterestService;

    @BeforeEach
    void setUp() {
        debtInterestService = new DebtInterestService(debts, accruals);
    }

    private static Debt debt(String name, LocalDate start) {
        return Debt.create(USER, name, Direction.OWED_BY_ME, null,
                new BigDecimal("10000.00"), new BigDecimal("12.000"), InterestMethod.SIMPLE, start, null);
    }

    private static Result posted() {
        return new Result(Settlement.POSTED,
                DebtInterestAccrual.claim(USER, UUID.randomUUID(), LocalDate.of(2026, 1, 1),
                        new BigDecimal("100.00"), new BigDecimal("10000.00")));
    }

    private static Result notDue() {
        return new Result(Settlement.NOT_DUE, null);
    }

    @Test
    @DisplayName("an interest-bearing debt is accrued by the pass")
    void interestBearingDebtIsAccrued() {
        Debt debt = debt("Loan", LocalDate.of(2026, 1, 1));
        when(debts.findByStatusAndInterestMethodIsNotNull(Debt.Status.ACTIVE)).thenReturn(List.of(debt));
        when(accruals.accrueDue(eq(debt.getId()), any())).thenReturn(posted(), notDue());

        AccrualSummary summary = debtInterestService.runAccrualPass();

        assertThat(summary.accrued()).isEqualTo(1);
    }

    @Test
    @DisplayName("missed months are caught up, not dropped")
    void catchesUpOnMissedMonths() {
        Debt behind = debt("Loan", LocalDate.of(2025, 10, 1));
        when(debts.findByStatusAndInterestMethodIsNotNull(Debt.Status.ACTIVE)).thenReturn(List.of(behind));
        // Three missed months, then no longer due.
        when(accruals.accrueDue(eq(behind.getId()), any()))
                .thenReturn(posted(), posted(), posted(), notDue());

        AccrualSummary summary = debtInterestService.runAccrualPass();

        assertThat(summary.accrued()).isEqualTo(3);
    }

    @Test
    @DisplayName("catch-up stops at the cap and says so rather than looking finished")
    void catchUpCapIsReported() {
        Debt stale = debt("Loan", LocalDate.of(2015, 1, 1));
        when(debts.findByStatusAndInterestMethodIsNotNull(Debt.Status.ACTIVE)).thenReturn(List.of(stale));
        // Always still due — a debt abandoned for years.
        when(accruals.accrueDue(eq(stale.getId()), any())).thenReturn(posted());
        lenient().when(debts.findById(stale.getId())).thenReturn(Optional.of(stale));

        AccrualSummary summary = debtInterestService.runAccrualPass();

        assertThat(summary.accrued()).isEqualTo(DebtInterestService.MAX_CATCH_UP);
        verify(accruals, times(DebtInterestService.MAX_CATCH_UP)).accrueDue(eq(stale.getId()), any());
        assertThat(summary.notes()).singleElement().asString()
                .contains("Loan").contains("still outstanding");
    }

    @Test
    @DisplayName("periods already recorded are counted apart from newly accrued ones")
    void countsAlreadyRecordedSeparately() {
        Debt restarted = debt("Loan", LocalDate.of(2026, 1, 1));
        when(debts.findByStatusAndInterestMethodIsNotNull(Debt.Status.ACTIVE)).thenReturn(List.of(restarted));
        when(accruals.accrueDue(eq(restarted.getId()), any()))
                .thenReturn(new Result(Settlement.ALREADY_RECORDED, null), notDue());

        AccrualSummary summary = debtInterestService.runAccrualPass();

        assertThat(summary.alreadyRecorded()).isEqualTo(1);
        assertThat(summary.accrued()).isZero();
    }

    @Test
    @DisplayName("one debt's failure does not stop the debts after it, and is reported")
    void oneFailureDoesNotStopThePass() {
        Debt broken = debt("Broken", LocalDate.of(2026, 1, 1));
        Debt fine = debt("Fine", LocalDate.of(2026, 1, 1));
        when(debts.findByStatusAndInterestMethodIsNotNull(Debt.Status.ACTIVE))
                .thenReturn(List.of(broken, fine));
        when(accruals.accrueDue(eq(broken.getId()), any()))
                .thenThrow(new IllegalStateException("unreachable"));
        when(accruals.accrueDue(eq(fine.getId()), any())).thenReturn(posted(), notDue());

        AccrualSummary summary = debtInterestService.runAccrualPass();

        // The healthy debt still went through — a broken one must not hold up the queue.
        assertThat(summary.accrued()).isEqualTo(1);
        // And the failure is surfaced rather than swallowed.
        assertThat(summary.notes()).singleElement().asString()
                .contains("Broken").contains("retried");
    }
}
