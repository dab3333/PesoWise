package ph.pesowise.planning.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ph.pesowise.planning.api.DataDtos.BudgetExport;
import ph.pesowise.planning.api.DataDtos.DebtExport;
import ph.pesowise.planning.api.DataDtos.DebtPaymentExport;
import ph.pesowise.planning.api.DataDtos.GoalContributionExport;
import ph.pesowise.planning.api.DataDtos.GoalExport;
import ph.pesowise.planning.api.DataDtos.ImportSummary;
import ph.pesowise.planning.api.DataDtos.LedgerIdMap;
import ph.pesowise.planning.api.DataDtos.PlanningExport;
import ph.pesowise.planning.api.DataDtos.RecurringBillExport;
import ph.pesowise.planning.api.DataDtos.RecurringRunExport;
import ph.pesowise.planning.domain.Budget;
import ph.pesowise.planning.domain.Debt;
import ph.pesowise.planning.domain.Debt.Direction;
import ph.pesowise.planning.domain.Debt.InterestMethod;
import ph.pesowise.planning.domain.DebtPayment;
import ph.pesowise.planning.domain.Goal;
import ph.pesowise.planning.domain.GoalContribution;
import ph.pesowise.planning.domain.RecurringBill;
import ph.pesowise.planning.repo.BudgetRepository;
import ph.pesowise.planning.repo.DebtInterestAccrualRepository;
import ph.pesowise.planning.repo.DebtPaymentRepository;
import ph.pesowise.planning.repo.DebtRepository;
import ph.pesowise.planning.repo.GoalContributionRepository;
import ph.pesowise.planning.repo.GoalRepository;
import ph.pesowise.planning.repo.RecurringBillRepository;
import ph.pesowise.planning.repo.RecurringRunRepository;
import ph.pesowise.planning.web.ConflictException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Backs the Settings page's export/import feature — the planning-service half. Unlike
 * ledger-service, deleting the four parent tables cascades their children (V2–V5 migrations),
 * so the ordering that actually matters is on the <em>insert</em> side: a child's foreign key
 * requires its parent to already exist.
 *
 * <p>Reinsertion goes through {@link EntityManager#persist}, generating fresh ids and remapping
 * every reference — a child's parent-id reference (goal/debt/bill) via the map this import
 * builds for its own new rows, and every ledger-owned reference (category, account, ledger
 * transaction) via the {@link LedgerIdMap} ledger-service's own import returned. See
 * ledger-service's {@code DataExportService} javadoc for the full reasoning.
 */
@ExtendWith(MockitoExtension.class)
class DataExportServiceTest {

    private static final UUID USER = UUID.fromString("3f1c9e2a-0000-4000-8000-000000000001");

    @Mock
    private BudgetRepository budgets;

    @Mock
    private GoalRepository goals;

    @Mock
    private GoalContributionRepository goalContributions;

    @Mock
    private DebtRepository debts;

    @Mock
    private DebtPaymentRepository debtPayments;

    @Mock
    private RecurringBillRepository recurringBills;

    @Mock
    private RecurringRunRepository recurringRuns;

    @Mock
    private DebtInterestAccrualRepository debtInterestAccruals;

    @Mock
    private EntityManager entityManager;

    private DataExportService dataExportService;

    @BeforeEach
    void setUp() {
        dataExportService = new DataExportService(
                budgets, goals, goalContributions, debts, debtPayments, recurringBills, recurringRuns,
                debtInterestAccruals, entityManager);
    }

    private static LedgerIdMap emptyLedgerIds() {
        return new LedgerIdMap(Map.of(), Map.of(), Map.of());
    }

    @Test
    @DisplayName("export maps a debt's interest fields, not just its principal/balance")
    void exportMapsDebtInterestFields() {
        Debt debt = Debt.create(
                USER, "Loan", Direction.OWED_BY_ME, null, new BigDecimal("10000.00"),
                new BigDecimal("12.000"), InterestMethod.SIMPLE, LocalDate.of(2026, 1, 1), null);
        Budget budget = Budget.create(USER, UUID.randomUUID(), YearMonth.of(2026, 8), new BigDecimal("500.00"));

        when(budgets.findByUserId(USER)).thenReturn(List.of(budget));
        when(goals.findByUserId(USER)).thenReturn(List.of());
        when(goalContributions.findByUserId(USER)).thenReturn(List.of());
        when(debts.findByUserId(USER)).thenReturn(List.of(debt));
        when(debtPayments.findByUserId(USER)).thenReturn(List.of());
        when(recurringBills.findByUserId(USER)).thenReturn(List.of());
        when(recurringRuns.findByUserId(USER)).thenReturn(List.of());
        when(debtInterestAccruals.findByUserId(USER)).thenReturn(List.of());

        PlanningExport export = dataExportService.export(USER);

        assertThat(export.budgets()).hasSize(1);
        BudgetExport budgetExport = export.budgets().get(0);
        assertThat(budgetExport.limitAmount()).isEqualByComparingTo("500.00");

        assertThat(export.debts()).hasSize(1);
        DebtExport debtExport = export.debts().get(0);
        assertThat(debtExport.id()).isEqualTo(debt.getId());
        assertThat(debtExport.interestMethod()).isEqualTo(InterestMethod.SIMPLE);
        assertThat(debtExport.interestRate()).isEqualByComparingTo("12.000");
        assertThat(debtExport.accruedInterest()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("import persists parents before their children — goals before contributions, debts before payments")
    void importRespectsParentChildOrder() {
        UUID goalId = UUID.randomUUID();
        UUID debtId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();
        Instant now = Instant.now();

        PlanningExport data = new PlanningExport(
                List.of(),
                List.of(new GoalExport(goalId, "Fund", new BigDecimal("1000.00"), null, false, null, now, now)),
                List.of(new GoalContributionExport(UUID.randomUUID(), goalId, new BigDecimal("100.00"), LocalDate.now(), null, null, now)),
                List.of(new DebtExport(
                        debtId, "Loan", Direction.OWED_BY_ME, null, new BigDecimal("1000.00"),
                        new BigDecimal("1000.00"), null, LocalDate.now(), null, BigDecimal.ZERO, BigDecimal.ZERO,
                        null, null, Debt.Status.ACTIVE, now, now, null)),
                List.of(new DebtPaymentExport(
                        UUID.randomUUID(), debtId, new BigDecimal("100.00"), new BigDecimal("100.00"),
                        BigDecimal.ZERO, LocalDate.now(), null, null, now)),
                List.of(new RecurringBillExport(
                        billId, "Rent", UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("500.00"),
                        RecurringBill.Frequency.MONTHLY, (short) 1, LocalDate.now(), false, true, null, now, now)),
                List.of(new RecurringRunExport(UUID.randomUUID(), billId, LocalDate.now(), null, false, now)),
                List.of());

        dataExportService.importAll(USER, data, emptyLedgerIds());

        ArgumentCaptor<Object> persisted = ArgumentCaptor.forClass(Object.class);
        verify(entityManager, times(6)).persist(persisted.capture());
        List<Object> order = persisted.getAllValues();

        assertThat(order.indexOf(order.stream().filter(o -> o instanceof Goal).findFirst().orElseThrow()))
                .isLessThan(order.indexOf(order.stream().filter(o -> o instanceof GoalContribution).findFirst().orElseThrow()));
        assertThat(order.indexOf(order.stream().filter(o -> o instanceof Debt).findFirst().orElseThrow()))
                .isLessThan(order.indexOf(order.stream().filter(o -> o instanceof DebtPayment).findFirst().orElseThrow()));
    }

    @Test
    @DisplayName("import wipes all four parent tables before reinserting anything")
    void importWipesBeforeReinserting() {
        UUID goalId = UUID.randomUUID();
        Instant now = Instant.now();
        PlanningExport data = new PlanningExport(
                List.of(), List.of(new GoalExport(goalId, "Fund", new BigDecimal("1000.00"), null, false, null, now, now)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        dataExportService.importAll(USER, data, emptyLedgerIds());

        InOrder order = inOrder(budgets, goals, debts, recurringBills, entityManager);
        order.verify(budgets).deleteByUserId(USER);
        order.verify(goals).deleteByUserId(USER);
        order.verify(debts).deleteByUserId(USER);
        order.verify(recurringBills).deleteByUserId(USER);
        order.verify(entityManager).persist(org.mockito.ArgumentMatchers.any(Goal.class));
    }

    @Test
    @DisplayName("import generates a fresh id for a debt while preserving its accrual state")
    void importGeneratesFreshIdButPreservesAccrualState() {
        UUID oldDebtId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2025-06-01T00:00:00Z");

        DebtExport debtExport = new DebtExport(
                oldDebtId, "Loan", Direction.OWED_BY_ME, null, new BigDecimal("10000.00"),
                new BigDecimal("6000.00"), new BigDecimal("12.000"), LocalDate.of(2026, 1, 1),
                InterestMethod.SIMPLE, new BigDecimal("150.00"), new BigDecimal("300.00"),
                LocalDate.of(2026, 7, 1), null, Debt.Status.ACTIVE, createdAt, createdAt, null);

        PlanningExport data = new PlanningExport(
                List.of(), List.of(), List.of(), List.of(debtExport), List.of(), List.of(), List.of(), List.of());

        dataExportService.importAll(USER, data, emptyLedgerIds());

        ArgumentCaptor<Debt> captor = ArgumentCaptor.forClass(Debt.class);
        verify(entityManager).persist(captor.capture());
        Debt restored = captor.getValue();
        assertThat(restored.getId()).isNotEqualTo(oldDebtId);
        assertThat(restored.getAccruedInterest()).isEqualByComparingTo("150.00");
        assertThat(restored.getInterestPaidTotal()).isEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("import remaps a recurring bill's category/account onto ledger's regenerated ids")
    void importRemapsLedgerReferences() {
        UUID oldCategoryId = UUID.randomUUID();
        UUID oldAccountId = UUID.randomUUID();
        UUID newCategoryId = UUID.randomUUID();
        UUID newAccountId = UUID.randomUUID();
        Instant now = Instant.now();

        PlanningExport data = new PlanningExport(
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new RecurringBillExport(
                        UUID.randomUUID(), "Rent", oldCategoryId, oldAccountId, new BigDecimal("500.00"),
                        RecurringBill.Frequency.MONTHLY, (short) 1, LocalDate.now(), false, true, null, now, now)),
                List.of(), List.of());

        LedgerIdMap ledgerIds = new LedgerIdMap(
                Map.of(oldCategoryId, newCategoryId), Map.of(oldAccountId, newAccountId), Map.of());

        dataExportService.importAll(USER, data, ledgerIds);

        ArgumentCaptor<RecurringBill> captor = ArgumentCaptor.forClass(RecurringBill.class);
        verify(entityManager).persist(captor.capture());
        RecurringBill restored = captor.getValue();
        assertThat(restored.getCategoryId()).isEqualTo(newCategoryId);
        assertThat(restored.getAccountId()).isEqualTo(newAccountId);
    }

    @Test
    @DisplayName("a goal contribution with no reconciled ledger transaction stays null after remapping")
    void importKeepsANullLedgerTxnIdNull() {
        UUID goalId = UUID.randomUUID();
        Instant now = Instant.now();

        PlanningExport data = new PlanningExport(
                List.of(),
                List.of(new GoalExport(goalId, "Fund", new BigDecimal("1000.00"), null, false, null, now, now)),
                List.of(new GoalContributionExport(UUID.randomUUID(), goalId, new BigDecimal("100.00"), LocalDate.now(), null, null, now)),
                List.of(), List.of(), List.of(), List.of(), List.of());

        dataExportService.importAll(USER, data, emptyLedgerIds());

        ArgumentCaptor<Object> persisted = ArgumentCaptor.forClass(Object.class);
        verify(entityManager, times(2)).persist(persisted.capture());
        GoalContribution restored = (GoalContribution) persisted.getAllValues().stream()
                .filter(o -> o instanceof GoalContribution).findFirst().orElseThrow();
        assertThat(restored.getLedgerTxnId()).isNull();
    }

    @Test
    @DisplayName("the import summary counts every table, not just the ones that changed")
    void importSummaryCountsEveryTable() {
        UUID goalId = UUID.randomUUID();
        Instant now = Instant.now();

        PlanningExport data = new PlanningExport(
                List.of(),
                List.of(new GoalExport(goalId, "Emergency fund", new BigDecimal("50000.00"), null, false, null, now, now)),
                List.of(new GoalContributionExport(UUID.randomUUID(), goalId, new BigDecimal("1000.00"), LocalDate.now(), null, null, now)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        ImportSummary summary = dataExportService.importAll(USER, data, emptyLedgerIds());

        assertThat(summary.goals()).isEqualTo(1);
        assertThat(summary.goalContributions()).isEqualTo(1);
        assertThat(summary.budgets()).isZero();
        assertThat(summary.debts()).isZero();
    }

    @Test
    @DisplayName("a persistence failure surfaces as a clean conflict rather than a raw exception")
    void importSurfacesAPersistenceFailureAsAConflict() {
        // entityManager.flush() is where a deferred INSERT actually executes.
        doThrow(new PersistenceException("constraint violation")).when(entityManager).flush();

        PlanningExport data = new PlanningExport(
                List.of(new BudgetExport(
                        UUID.randomUUID(), UUID.randomUUID(), YearMonth.of(2026, 8), new BigDecimal("500.00"),
                        Instant.now(), Instant.now())),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        assertThatThrownBy(() -> dataExportService.importAll(USER, data, emptyLedgerIds()))
                .isInstanceOf(ConflictException.class);
    }
}
