package ph.pesowise.planning.service;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import ph.pesowise.planning.domain.RecurringBill;
import ph.pesowise.planning.domain.RecurringBill.Frequency;
import ph.pesowise.planning.domain.RecurringRun;
import ph.pesowise.planning.ledger.LedgerDtos.SourceType;
import ph.pesowise.planning.ledger.LedgerWriter;
import ph.pesowise.planning.repo.RecurringBillRepository;
import ph.pesowise.planning.repo.RecurringRunRepository;
import ph.pesowise.planning.service.RecurringOccurrences.Result;
import ph.pesowise.planning.service.RecurringOccurrences.Settlement;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The idempotency guards — the part of this feature that would otherwise charge people twice. */
@ExtendWith(MockitoExtension.class)
class RecurringOccurrencesTest {

    private static final UUID USER = UUID.fromString("3f1c9e2a-0000-4000-8000-000000000001");
    private static final UUID ACCOUNT = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final UUID CATEGORY = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002");
    private static final UUID LEDGER_TXN = UUID.fromString("cccccccc-0000-4000-8000-000000000003");
    private static final LocalDate DUE = LocalDate.of(2026, 8, 5);
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 11);

    @Mock
    private RecurringBillRepository bills;

    @Mock
    private RecurringRunRepository runs;

    @Mock
    private LedgerWriter ledger;

    private RecurringOccurrences occurrences;

    @BeforeEach
    void setUp() {
        occurrences = new RecurringOccurrences(bills, runs, ledger);
    }

    private RecurringBill givenBill() {
        RecurringBill bill = RecurringBill.create(
                USER, "Rent", CATEGORY, ACCOUNT, new BigDecimal("15000.00"),
                Frequency.MONTHLY, DUE, true, "apartment");
        when(bills.findById(bill.getId())).thenReturn(Optional.of(bill));
        return bill;
    }

    private void givenClaimSucceeds() {
        when(runs.saveAndFlush(any(RecurringRun.class))).thenAnswer(call -> call.getArgument(0));
    }

    /** A 409 from the ledger's own occurrence guard. */
    private static FeignException.Conflict ledgerConflict() {
        return (FeignException.Conflict) FeignException.errorStatus(
                "createSourcedTransaction",
                feign.Response.builder()
                        .status(409)
                        .reason("Conflict")
                        .request(Request.create(Request.HttpMethod.POST, "/api/transactions/sourced",
                                Collections.emptyMap(), null, StandardCharsets.UTF_8, new RequestTemplate()))
                        .headers(Collections.emptyMap())
                        .build());
    }

    @Test
    @DisplayName("a due occurrence is claimed, posted, and the cursor advances")
    void settlesDueOccurrence() {
        RecurringBill bill = givenBill();
        givenClaimSucceeds();
        when(ledger.post(eq(USER), eq(SourceType.RECURRING_BILL), eq(bill.getId()),
                eq(ACCOUNT), eq(CATEGORY), eq(new BigDecimal("15000.00")), eq(DUE), any()))
                .thenReturn(LEDGER_TXN);

        Result result = occurrences.settleDue(bill.getId(), TODAY);

        assertThat(result.settlement()).isEqualTo(Settlement.POSTED);
        assertThat(result.run().getLedgerTxnId()).isEqualTo(LEDGER_TXN);
        assertThat(result.run().getDueDate()).isEqualTo(DUE);
        // Cursor moved on, so the next pass looks at September.
        assertThat(bill.getNextRunDate()).isEqualTo(LocalDate.of(2026, 9, 5));
    }

    @Test
    @DisplayName("the occurrence is claimed BEFORE the ledger is called")
    void claimsBeforePosting() {
        RecurringBill bill = givenBill();
        givenClaimSucceeds();

        occurrences.settleDue(bill.getId(), TODAY);

        // Order matters: posting first would charge twice and only then discover the clash.
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(runs, ledger);
        order.verify(runs).saveAndFlush(any(RecurringRun.class));
        order.verify(ledger).post(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("GUARD 1: a duplicate claim posts nothing and still advances the cursor")
    void duplicateClaimPostsNothing() {
        RecurringBill bill = givenBill();
        // What a restarted scheduler hits: the occurrence is already recorded.
        when(runs.saveAndFlush(any(RecurringRun.class)))
                .thenThrow(new DataIntegrityViolationException("ux_recurring_runs_occurrence"));

        Result result = occurrences.settleDue(bill.getId(), TODAY);

        assertThat(result.settlement()).isEqualTo(Settlement.ALREADY_RECORDED);
        // The crucial assertion: no money was written a second time.
        verify(ledger, never()).post(any(), any(), any(), any(), any(), any(), any(), any());
        // And the cursor still moves, or the scheduler would retry this occurrence forever.
        assertThat(bill.getNextRunDate()).isEqualTo(LocalDate.of(2026, 9, 5));
    }

    @Test
    @DisplayName("GUARD 2: a 409 from the ledger keeps the claim rather than failing the pass")
    void ledgerConflictKeepsTheClaim() {
        RecurringBill bill = givenBill();
        givenClaimSucceeds();
        when(ledger.post(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(ledgerConflict());

        Result result = occurrences.settleDue(bill.getId(), TODAY);

        // The ledger already had this occurrence from an attempt whose commit failed. Retrying could
        // never succeed, so the claim becomes the durable record and the pass moves on.
        assertThat(result.settlement()).isEqualTo(Settlement.POSTED);
        assertThat(result.run().getLedgerTxnId()).isNull();
        assertThat(bill.getNextRunDate()).isEqualTo(LocalDate.of(2026, 9, 5));
    }

    @Test
    @DisplayName("a ledger failure other than 409 propagates, so the claim rolls back with it")
    void otherLedgerFailurePropagates() {
        RecurringBill bill = givenBill();
        givenClaimSucceeds();
        when(ledger.post(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("ledger unreachable"));

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> occurrences.settleDue(bill.getId(), TODAY))
                .isInstanceOf(IllegalStateException.class);

        // Left where it was: the rollback undoes the claim, so the next pass retries cleanly.
        assertThat(bill.getNextRunDate()).isEqualTo(DUE);
    }

    @Test
    @DisplayName("a bill that is not yet due is left alone")
    void notDueIsLeftAlone() {
        RecurringBill bill = RecurringBill.create(
                USER, "Rent", CATEGORY, ACCOUNT, new BigDecimal("15000.00"),
                Frequency.MONTHLY, LocalDate.of(2026, 12, 1), true, null);
        when(bills.findById(bill.getId())).thenReturn(Optional.of(bill));

        Result result = occurrences.settleDue(bill.getId(), TODAY);

        assertThat(result.settlement()).isEqualTo(Settlement.NOT_DUE);
        verify(runs, never()).saveAndFlush(any());
        verify(ledger, never()).post(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("an inactive bill is not settled")
    void inactiveBillIsNotSettled() {
        RecurringBill bill = givenBill();
        bill.setActive(false);

        assertThat(occurrences.settleDue(bill.getId(), TODAY).settlement())
                .isEqualTo(Settlement.NOT_DUE);
    }

    @Test
    @DisplayName("a deleted bill is handled rather than throwing")
    void missingBillIsHandled() {
        UUID gone = UUID.randomUUID();
        when(bills.findById(gone)).thenReturn(Optional.empty());

        assertThat(occurrences.settleDue(gone, TODAY).settlement()).isEqualTo(Settlement.NOT_DUE);
    }

    @Test
    @DisplayName("the note names the bill so the transaction explains itself")
    void noteExplainsTheTransaction() {
        RecurringBill bill = givenBill();
        givenClaimSucceeds();

        occurrences.settleDue(bill.getId(), TODAY);

        org.mockito.ArgumentCaptor<String> note = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(ledger).post(any(), any(), any(), any(), any(), any(), any(), note.capture());
        assertThat(note.getValue()).contains("Rent").contains("apartment");
    }

    @Test
    @DisplayName("skipping records the occurrence with no transaction and advances")
    void skipRecordsNoTransaction() {
        RecurringBill bill = givenBill();
        givenClaimSucceeds();

        Result result = occurrences.skipDue(bill.getId(), TODAY);

        assertThat(result.settlement()).isEqualTo(Settlement.POSTED);
        assertThat(result.run().isSkipped()).isTrue();
        assertThat(result.run().getLedgerTxnId()).isNull();
        verify(ledger, never()).post(any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(bill.getNextRunDate()).isEqualTo(LocalDate.of(2026, 9, 5));
    }

    @Test
    @DisplayName("skipping an already recorded occurrence is reported, not duplicated")
    void skipOnDuplicateIsReported() {
        RecurringBill bill = givenBill();
        when(runs.saveAndFlush(any(RecurringRun.class)))
                .thenThrow(new DataIntegrityViolationException("ux_recurring_runs_occurrence"));

        assertThat(occurrences.skipDue(bill.getId(), TODAY).settlement())
                .isEqualTo(Settlement.ALREADY_RECORDED);
    }
}
