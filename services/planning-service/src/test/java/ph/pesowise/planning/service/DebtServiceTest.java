package ph.pesowise.planning.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ph.pesowise.planning.api.DebtDtos.DebtOverview;
import ph.pesowise.planning.api.DebtDtos.DebtRequest;
import ph.pesowise.planning.api.DebtDtos.DebtResponse;
import ph.pesowise.planning.api.DebtDtos.PaymentRequest;
import ph.pesowise.planning.domain.Debt;
import ph.pesowise.planning.domain.DebtPayment;
import ph.pesowise.planning.ledger.LedgerDtos.SourceType;
import ph.pesowise.planning.ledger.LedgerWriter;
import ph.pesowise.planning.repo.DebtPaymentRepository;
import ph.pesowise.planning.repo.DebtRepository;
import ph.pesowise.planning.web.BadRequestException;
import ph.pesowise.planning.web.ConflictException;
import ph.pesowise.planning.web.NotFoundException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DebtServiceTest {

    private static final UUID USER = UUID.fromString("3f1c9e2a-0000-4000-8000-000000000001");
    private static final UUID ACCOUNT = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final UUID CATEGORY = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002");
    private static final UUID LEDGER_TXN = UUID.fromString("cccccccc-0000-4000-8000-000000000003");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 11);

    @Mock
    private DebtRepository debts;

    @Mock
    private DebtPaymentRepository payments;

    @Mock
    private LedgerWriter ledger;

    private DebtService debtService;

    @BeforeEach
    void setUp() {
        debtService = new DebtService(debts, payments, ledger);
    }

    private Debt givenDebt(Debt.Direction direction, String principal) {
        Debt debt = Debt.create(USER, "Kuya Ben", direction, "Ben Reyes",
                new BigDecimal(principal), null, LocalDate.of(2026, 12, 31));
        when(debts.findByIdAndUserId(debt.getId(), USER)).thenReturn(Optional.of(debt));
        return debt;
    }

    private void givenLedgerAccepts() {
        when(ledger.post(eq(USER), eq(SourceType.DEBT_PAYMENT), any(), any(), any(), any(), any(), any()))
                .thenReturn(LEDGER_TXN);
        when(payments.save(any(DebtPayment.class))).thenAnswer(call -> call.getArgument(0));
    }

    private PaymentRequest payment(String amount) {
        return new PaymentRequest(new BigDecimal(amount), TODAY, ACCOUNT, CATEGORY, "sa GCash");
    }

    @Test
    @DisplayName("a payment reduces the balance and writes a ledger transaction")
    void paymentReducesBalanceAndWritesToLedger() {
        Debt debt = givenDebt(Debt.Direction.OWED_BY_ME, "10000.00");
        givenLedgerAccepts();

        debtService.recordPayment(USER, debt.getId(), payment("2500.00"));

        assertThat(debt.getBalance()).isEqualByComparingTo("7500.00");
        verify(ledger).post(eq(USER), eq(SourceType.DEBT_PAYMENT), eq(debt.getId()),
                eq(ACCOUNT), eq(CATEGORY), eq(new BigDecimal("2500.00")), eq(TODAY), any());
    }

    @Test
    @DisplayName("the ledger transaction is tagged DEBT_PAYMENT and points back at the debt")
    void ledgerTransactionCarriesItsSource() {
        Debt debt = givenDebt(Debt.Direction.OWED_BY_ME, "10000.00");
        givenLedgerAccepts();

        debtService.recordPayment(USER, debt.getId(), payment("2500.00"));

        ArgumentCaptor<String> note = ArgumentCaptor.forClass(String.class);
        // sourceId is the audit link that makes an orphaned transaction discoverable.
        verify(ledger).post(eq(USER), eq(SourceType.DEBT_PAYMENT), eq(debt.getId()),
                eq(ACCOUNT), eq(CATEGORY), eq(new BigDecimal("2500.00")), eq(TODAY), note.capture());

        assertThat(note.getValue()).contains("Kuya Ben").contains("sa GCash");
    }

    @Test
    @DisplayName("the returned ledger transaction id is stored on the payment")
    void storesLedgerTransactionId() {
        Debt debt = givenDebt(Debt.Direction.OWED_BY_ME, "10000.00");
        givenLedgerAccepts();

        assertThat(debtService.recordPayment(USER, debt.getId(), payment("2500.00")).ledgerTxnId())
                .isEqualTo(LEDGER_TXN);
    }

    @Test
    @DisplayName("paying the exact balance settles the debt")
    void payingInFullSettles() {
        Debt debt = givenDebt(Debt.Direction.OWED_BY_ME, "10000.00");
        givenLedgerAccepts();

        debtService.recordPayment(USER, debt.getId(), payment("10000.00"));

        assertThat(debt.getBalance()).isEqualByComparingTo("0");
        assertThat(debt.getStatus()).isEqualTo(Debt.Status.SETTLED);
        assertThat(debt.getSettledAt()).isNotNull();
    }

    @Test
    @DisplayName("overpaying is rejected rather than clamped, and nothing reaches the ledger")
    void rejectsOverpayment() {
        Debt debt = givenDebt(Debt.Direction.OWED_BY_ME, "10000.00");

        assertThatThrownBy(() -> debtService.recordPayment(USER, debt.getId(), payment("10000.01")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("outstanding");

        assertThat(debt.getBalance()).isEqualByComparingTo("10000.00");
        verify(ledger, never()).post(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("paying an already settled debt is a conflict")
    void rejectsPaymentOnSettledDebt() {
        Debt debt = givenDebt(Debt.Direction.OWED_BY_ME, "10000.00");
        givenLedgerAccepts();
        debtService.recordPayment(USER, debt.getId(), payment("10000.00"));

        assertThatThrownBy(() -> debtService.recordPayment(USER, debt.getId(), payment("100.00")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already settled");
    }

    @Test
    @DisplayName("a ledger failure propagates, so the balance change rolls back with the transaction")
    void ledgerFailurePropagates() {
        Debt debt = givenDebt(Debt.Direction.OWED_BY_ME, "10000.00");
        when(ledger.post(eq(USER), eq(SourceType.DEBT_PAYMENT), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("ledger unreachable"));

        assertThatThrownBy(() -> debtService.recordPayment(USER, debt.getId(), payment("2500.00")))
                .isInstanceOf(IllegalStateException.class);

        // The payment row is never written, so Spring's rollback leaves nothing behind.
        verify(payments, never()).save(any());
    }

    @Test
    @DisplayName("deleting a payment restores the balance and removes the ledger transaction")
    void deletingPaymentReversesBothSides() {
        Debt debt = givenDebt(Debt.Direction.OWED_BY_ME, "10000.00");
        givenLedgerAccepts();
        debtService.recordPayment(USER, debt.getId(), payment("2500.00"));

        DebtPayment stored = DebtPayment.create(
                USER, debt.getId(), new BigDecimal("2500.00"), TODAY, null, LEDGER_TXN);
        when(payments.findByIdAndUserId(stored.getId(), USER)).thenReturn(Optional.of(stored));

        debtService.deletePayment(USER, debt.getId(), stored.getId());

        assertThat(debt.getBalance()).isEqualByComparingTo("10000.00");
        // Leaving the transaction behind would make the two records disagree.
        verify(ledger).remove(USER, LEDGER_TXN);
        verify(payments).delete(stored);
    }

    @Test
    @DisplayName("deleting the final payment reopens a settled debt")
    void deletingPaymentReopensSettledDebt() {
        Debt debt = givenDebt(Debt.Direction.OWED_BY_ME, "10000.00");
        givenLedgerAccepts();
        debtService.recordPayment(USER, debt.getId(), payment("10000.00"));
        assertThat(debt.getStatus()).isEqualTo(Debt.Status.SETTLED);

        DebtPayment stored = DebtPayment.create(
                USER, debt.getId(), new BigDecimal("10000.00"), TODAY, null, LEDGER_TXN);
        when(payments.findByIdAndUserId(stored.getId(), USER)).thenReturn(Optional.of(stored));

        debtService.deletePayment(USER, debt.getId(), stored.getId());

        assertThat(debt.getStatus()).isEqualTo(Debt.Status.ACTIVE);
        assertThat(debt.getSettledAt()).isNull();
        assertThat(debt.getBalance()).isEqualByComparingTo("10000.00");
    }

    @Test
    @DisplayName("a payment belonging to another debt is not found")
    void paymentMustBelongToTheDebt() {
        Debt debt = givenDebt(Debt.Direction.OWED_BY_ME, "10000.00");
        DebtPayment other = DebtPayment.create(
                USER, UUID.randomUUID(), new BigDecimal("100"), TODAY, null, LEDGER_TXN);
        when(payments.findByIdAndUserId(other.getId(), USER)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> debtService.deletePayment(USER, debt.getId(), other.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("another user's debt is reported as not found, never as forbidden")
    void scopesByUser() {
        UUID foreign = UUID.randomUUID();
        when(debts.findByIdAndUserId(foreign, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> debtService.recordPayment(USER, foreign, payment("100.00")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("totals separate the two directions and net them")
    void totalsSeparateDirections() {
        Debt owed = Debt.create(USER, "Pag-IBIG", Debt.Direction.OWED_BY_ME, null,
                new BigDecimal("50000"), null, null);
        Debt lent = Debt.create(USER, "Ate Cel", Debt.Direction.OWED_TO_ME, "Cel",
                new BigDecimal("8000"), null, null);
        when(debts.findByUserIdOrderByStatusAscDueDateAscCreatedAtAsc(USER))
                .thenReturn(List.of(owed, lent));

        DebtOverview overview = debtService.list(USER);

        assertThat(overview.totalOwedByMe()).isEqualByComparingTo("50000");
        assertThat(overview.totalOwedToMe()).isEqualByComparingTo("8000");
        assertThat(overview.netPosition()).isEqualByComparingTo("-42000");
    }

    @Test
    @DisplayName("settled debts are excluded from the outstanding totals")
    void settledDebtsExcludedFromTotals() {
        Debt settled = Debt.create(USER, "Paid off", Debt.Direction.OWED_BY_ME, null,
                new BigDecimal("5000"), null, null);
        settled.applyPayment(new BigDecimal("5000"));
        when(debts.findByUserIdOrderByStatusAscDueDateAscCreatedAtAsc(USER))
                .thenReturn(List.of(settled));

        assertThat(debtService.list(USER).totalOwedByMe()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("progress reports the amount paid and the percentage")
    void reportsProgress() {
        Debt debt = Debt.create(USER, "Kuya Ben", Debt.Direction.OWED_BY_ME, null,
                new BigDecimal("10000.00"), null, null);
        debt.applyPayment(new BigDecimal("2500.00"));
        when(debts.findByUserIdOrderByStatusAscDueDateAscCreatedAtAsc(USER))
                .thenReturn(List.of(debt));

        DebtResponse response = debtService.list(USER).debts().getFirst();

        assertThat(response.paidAmount()).isEqualByComparingTo("2500.00");
        assertThat(response.percentPaid()).isEqualByComparingTo("25.0");
        assertThat(response.balance()).isEqualByComparingTo("7500.00");
    }

    @Test
    @DisplayName("a past due date marks an active debt overdue, but never a settled one")
    void overdueOnlyAppliesToActiveDebts() {
        Debt active = Debt.create(USER, "Late", Debt.Direction.OWED_BY_ME, null,
                new BigDecimal("1000"), null, LocalDate.now().minusDays(5));
        Debt settled = Debt.create(USER, "Was late", Debt.Direction.OWED_BY_ME, null,
                new BigDecimal("1000"), null, LocalDate.now().minusDays(5));
        settled.applyPayment(new BigDecimal("1000"));
        when(debts.findByUserIdOrderByStatusAscDueDateAscCreatedAtAsc(USER))
                .thenReturn(List.of(active, settled));

        List<DebtResponse> found = debtService.list(USER).debts();

        assertThat(found.get(0).overdue()).isTrue();
        assertThat(found.get(0).daysUntilDue()).isEqualTo(-5);
        assertThat(found.get(1).overdue()).isFalse();
    }

    @Test
    @DisplayName("a new debt starts wholly outstanding")
    void newDebtStartsOutstanding() {
        when(debts.save(any(Debt.class))).thenAnswer(call -> call.getArgument(0));

        DebtResponse response = debtService.create(USER, new DebtRequest(
                "  Kuya Ben  ", Debt.Direction.OWED_BY_ME, " Ben ",
                new BigDecimal("10000.00"), new BigDecimal("2.500"), LocalDate.of(2026, 12, 31)));

        assertThat(response.name()).isEqualTo("Kuya Ben");
        assertThat(response.counterparty()).isEqualTo("Ben");
        assertThat(response.balance()).isEqualByComparingTo("10000.00");
        assertThat(response.paidAmount()).isEqualByComparingTo("0");
        assertThat(response.status()).isEqualTo(Debt.Status.ACTIVE);
    }
}
