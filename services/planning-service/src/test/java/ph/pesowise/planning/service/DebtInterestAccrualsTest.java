package ph.pesowise.planning.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import ph.pesowise.planning.domain.Debt;
import ph.pesowise.planning.domain.Debt.Direction;
import ph.pesowise.planning.domain.Debt.InterestMethod;
import ph.pesowise.planning.domain.DebtInterestAccrual;
import ph.pesowise.planning.repo.DebtInterestAccrualRepository;
import ph.pesowise.planning.repo.DebtRepository;
import ph.pesowise.planning.service.DebtInterestAccruals.Result;
import ph.pesowise.planning.service.DebtInterestAccruals.Settlement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The idempotency guard — the part of this feature that would otherwise accrue the same month's
 * interest twice, exactly the failure mode {@code RecurringOccurrencesTest} covers for bills.
 */
@ExtendWith(MockitoExtension.class)
class DebtInterestAccrualsTest {

    private static final UUID USER = UUID.fromString("3f1c9e2a-0000-4000-8000-000000000001");
    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate TODAY = LocalDate.of(2026, 2, 1);

    @Mock
    private DebtRepository debts;

    @Mock
    private DebtInterestAccrualRepository accruals;

    private DebtInterestAccruals debtInterestAccruals;

    @BeforeEach
    void setUp() {
        debtInterestAccruals = new DebtInterestAccruals(debts, accruals);
    }

    private Debt givenDebt() {
        Debt debt = Debt.create(USER, "Loan", Direction.OWED_BY_ME, null,
                new BigDecimal("10000.00"), new BigDecimal("12.000"), InterestMethod.SIMPLE, START, null);
        when(debts.findById(debt.getId())).thenReturn(Optional.of(debt));
        return debt;
    }

    private void givenClaimSucceeds() {
        when(accruals.saveAndFlush(any(DebtInterestAccrual.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    @DisplayName("a due period is claimed, accrued, and the cursor advances")
    void accruesDuePeriod() {
        Debt debt = givenDebt();
        givenClaimSucceeds();

        Result result = debtInterestAccruals.accrueDue(debt.getId(), TODAY);

        assertThat(result.settlement()).isEqualTo(Settlement.POSTED);
        assertThat(result.accrual().getAmount()).isEqualByComparingTo("100.00");
        assertThat(debt.getAccruedInterest()).isEqualByComparingTo("100.00");
        assertThat(debt.getLastAccruedOn()).isEqualTo(START);
    }

    @Test
    @DisplayName("the period is claimed BEFORE the debt is touched")
    void claimsBeforeMutating() {
        Debt debt = givenDebt();
        givenClaimSucceeds();

        debtInterestAccruals.accrueDue(debt.getId(), TODAY);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(accruals);
        order.verify(accruals).saveAndFlush(any(DebtInterestAccrual.class));
    }

    @Test
    @DisplayName("GUARD: a duplicate claim accrues nothing a second time and still advances the cursor")
    void duplicateClaimAccruesNothing() {
        Debt debt = givenDebt();
        // What a restarted scheduler, or a retried catch-up loop, hits: already recorded.
        when(accruals.saveAndFlush(any(DebtInterestAccrual.class)))
                .thenThrow(new DataIntegrityViolationException("ux_debt_accrual_period"));

        Result result = debtInterestAccruals.accrueDue(debt.getId(), TODAY);

        assertThat(result.settlement()).isEqualTo(Settlement.ALREADY_RECORDED);
        // The crucial assertion: interest was not added a second time.
        assertThat(debt.getAccruedInterest()).isEqualByComparingTo("0");
        // And the cursor still moves, or the pass would retry this period forever.
        assertThat(debt.getLastAccruedOn()).isEqualTo(START);
    }

    @Test
    @DisplayName("a debt with no interest method is left alone")
    void noInterestMethodIsLeftAlone() {
        Debt debt = Debt.create(USER, "No interest", Direction.OWED_BY_ME, null,
                new BigDecimal("1000.00"), null, null, START, null);
        when(debts.findById(debt.getId())).thenReturn(Optional.of(debt));

        assertThat(debtInterestAccruals.accrueDue(debt.getId(), TODAY).settlement())
                .isEqualTo(Settlement.NOT_DUE);
        assertThat(debt.getAccruedInterest()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("a period not yet fully elapsed is left alone")
    void periodNotYetDueIsLeftAlone() {
        Debt debt = givenDebt();

        // January hasn't finished yet on the 15th, so nothing accrues.
        assertThat(debtInterestAccruals.accrueDue(debt.getId(), LocalDate.of(2026, 1, 15)).settlement())
                .isEqualTo(Settlement.NOT_DUE);
    }

    @Test
    @DisplayName("a deleted debt is handled rather than throwing")
    void missingDebtIsHandled() {
        UUID gone = UUID.randomUUID();
        when(debts.findById(gone)).thenReturn(Optional.empty());

        assertThat(debtInterestAccruals.accrueDue(gone, TODAY).settlement()).isEqualTo(Settlement.NOT_DUE);
    }
}
