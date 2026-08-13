package ph.pesowise.planning.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.pesowise.planning.api.DataDtos.BudgetExport;
import ph.pesowise.planning.api.DataDtos.DebtExport;
import ph.pesowise.planning.api.DataDtos.DebtInterestAccrualExport;
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
import ph.pesowise.planning.domain.DebtInterestAccrual;
import ph.pesowise.planning.domain.DebtPayment;
import ph.pesowise.planning.domain.Goal;
import ph.pesowise.planning.domain.GoalContribution;
import ph.pesowise.planning.domain.RecurringBill;
import ph.pesowise.planning.domain.RecurringRun;
import ph.pesowise.planning.repo.BudgetRepository;
import ph.pesowise.planning.repo.DebtInterestAccrualRepository;
import ph.pesowise.planning.repo.DebtPaymentRepository;
import ph.pesowise.planning.repo.DebtRepository;
import ph.pesowise.planning.repo.GoalContributionRepository;
import ph.pesowise.planning.repo.GoalRepository;
import ph.pesowise.planning.repo.RecurringBillRepository;
import ph.pesowise.planning.repo.RecurringRunRepository;
import ph.pesowise.planning.web.ConflictException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Backs the Settings page's "Export data" / "Import data" feature — the planning-service half;
 * see ledger-service's {@code DataExportService} for the other half and the shared design
 * reasoning (import always replaces, never merges; fresh ids are generated rather than reused,
 * so restoring your own backup and loading someone else's export into a different account are
 * the exact same code path).
 *
 * <p>This half additionally has to remap every id it stores that actually belongs to
 * ledger-service — {@code category_id}/{@code account_id} on budgets and recurring bills, and
 * {@code ledger_txn_id} on debt payments, goal contributions, and recurring runs — onto whatever
 * ledger-service's own import just regenerated them as, via the {@link LedgerIdMap} the frontend
 * threads through from ledger's import response.
 *
 * <p>Deleting {@code budgets}, {@code goals}, {@code debts}, and {@code recurring_bills} cascades
 * their children ({@code goal_contributions}, {@code debt_payments} + {@code
 * debt_interest_accruals}, {@code recurring_runs} respectively — all {@code ON DELETE CASCADE}),
 * so the wipe only needs to target the four parent tables. Reinsertion still has to go
 * parent-before-child, both for the foreign key and so this service's own new parent ids exist
 * before their children need to reference them.
 */
@Service
public class DataExportService {

    private final BudgetRepository budgets;
    private final GoalRepository goals;
    private final GoalContributionRepository goalContributions;
    private final DebtRepository debts;
    private final DebtPaymentRepository debtPayments;
    private final RecurringBillRepository recurringBills;
    private final RecurringRunRepository recurringRuns;
    private final DebtInterestAccrualRepository debtInterestAccruals;
    private final EntityManager entityManager;

    public DataExportService(
            BudgetRepository budgets,
            GoalRepository goals,
            GoalContributionRepository goalContributions,
            DebtRepository debts,
            DebtPaymentRepository debtPayments,
            RecurringBillRepository recurringBills,
            RecurringRunRepository recurringRuns,
            DebtInterestAccrualRepository debtInterestAccruals,
            EntityManager entityManager) {
        this.budgets = budgets;
        this.goals = goals;
        this.goalContributions = goalContributions;
        this.debts = debts;
        this.debtPayments = debtPayments;
        this.recurringBills = recurringBills;
        this.recurringRuns = recurringRuns;
        this.debtInterestAccruals = debtInterestAccruals;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public PlanningExport export(UUID userId) {
        var budgetExports = budgets.findByUserId(userId).stream()
                .map(b -> new BudgetExport(
                        b.getId(), b.getCategoryId(), b.getMonth(), b.getLimitAmount(), b.getCreatedAt(),
                        b.getUpdatedAt()))
                .toList();

        var goalExports = goals.findByUserId(userId).stream()
                .map(g -> new GoalExport(
                        g.getId(), g.getName(), g.getTargetAmount(), g.getTargetDate(), g.isArchived(), g.getNote(),
                        g.getCreatedAt(), g.getUpdatedAt()))
                .toList();

        var goalContributionExports = goalContributions.findByUserId(userId).stream()
                .map(c -> new GoalContributionExport(
                        c.getId(), c.getGoalId(), c.getAmount(), c.getContributedOn(), c.getNote(),
                        c.getLedgerTxnId(), c.getCreatedAt()))
                .toList();

        var debtExports = debts.findByUserId(userId).stream()
                .map(d -> new DebtExport(
                        d.getId(), d.getName(), d.getDirection(), d.getCounterparty(), d.getPrincipal(),
                        d.getBalance(), d.getInterestRate(), d.getStartDate(), d.getInterestMethod(),
                        d.getAccruedInterest(), d.getInterestPaidTotal(), d.getLastAccruedOn(), d.getDueDate(),
                        d.getStatus(), d.getCreatedAt(), d.getUpdatedAt(), d.getSettledAt()))
                .toList();

        var debtPaymentExports = debtPayments.findByUserId(userId).stream()
                .map(p -> new DebtPaymentExport(
                        p.getId(), p.getDebtId(), p.getAmount(), p.getPrincipalPart(), p.getInterestPart(),
                        p.getPaidOn(), p.getNote(), p.getLedgerTxnId(), p.getCreatedAt()))
                .toList();

        var recurringBillExports = recurringBills.findByUserId(userId).stream()
                .map(r -> new RecurringBillExport(
                        r.getId(), r.getName(), r.getCategoryId(), r.getAccountId(), r.getAmount(),
                        r.getFrequency(), r.getDayOfPeriod(), r.getNextRunDate(), r.isAutoPost(), r.isActive(),
                        r.getNote(), r.getCreatedAt(), r.getUpdatedAt()))
                .toList();

        var recurringRunExports = recurringRuns.findByUserId(userId).stream()
                .map(r -> new RecurringRunExport(
                        r.getId(), r.getBillId(), r.getDueDate(), r.getLedgerTxnId(), r.isSkipped(),
                        r.getCreatedAt()))
                .toList();

        var debtInterestAccrualExports = debtInterestAccruals.findByUserId(userId).stream()
                .map(a -> new DebtInterestAccrualExport(
                        a.getId(), a.getDebtId(), a.getPeriod(), a.getAmount(), a.getBalanceAtAccrual(),
                        a.getCreatedAt()))
                .toList();

        return new PlanningExport(
                budgetExports, goalExports, goalContributionExports, debtExports, debtPaymentExports,
                recurringBillExports, recurringRunExports, debtInterestAccrualExports);
    }

    /**
     * Wipes this user's budgets, goals, debts, and recurring bills (cascading their children),
     * then reinserts the file's contents in their place under fresh ids, remapped against
     * {@code ledgerIds} wherever a row references ledger-service's data. One transaction, so a
     * failure partway rolls back to the pre-import state.
     */
    @Transactional
    public ImportSummary importAll(UUID userId, PlanningExport data, LedgerIdMap ledgerIds) {
        budgets.deleteByUserId(userId);
        goals.deleteByUserId(userId);
        debts.deleteByUserId(userId);
        recurringBills.deleteByUserId(userId);

        Map<UUID, UUID> goalIds = new HashMap<>();
        Map<UUID, UUID> debtIds = new HashMap<>();
        Map<UUID, UUID> billIds = new HashMap<>();

        try {
            // Derived deleteByX queries load entities and call EntityManager.remove(), which —
            // like persist() below — is deferred until flush. Hibernate's flush order is always
            // inserts before deletes regardless of call order, so without this flush a
            // re-imported row that would collide with a unique constraint on one just "deleted"
            // fails, because the delete hasn't hit the database yet when the insert runs. Same
            // fix as ledger-service's import.
            entityManager.flush();

            for (BudgetExport b : data.budgets()) {
                entityManager.persist(Budget.restore(
                        UUID.randomUUID(), userId, mapped(ledgerIds.categoryIds(), b.categoryId()), b.month(),
                        b.limitAmount(), b.createdAt(), b.updatedAt()));
            }

            for (GoalExport g : data.goals()) {
                UUID newId = UUID.randomUUID();
                goalIds.put(g.id(), newId);
                entityManager.persist(Goal.restore(
                        newId, userId, g.name(), g.targetAmount(), g.targetDate(), g.archived(), g.note(),
                        g.createdAt(), g.updatedAt()));
            }

            for (GoalContributionExport c : data.goalContributions()) {
                entityManager.persist(GoalContribution.restore(
                        UUID.randomUUID(), userId, goalIds.get(c.goalId()), c.amount(), c.contributedOn(),
                        c.note(), mapped(ledgerIds.transactionIds(), c.ledgerTxnId()), c.createdAt()));
            }

            for (DebtExport d : data.debts()) {
                UUID newId = UUID.randomUUID();
                debtIds.put(d.id(), newId);
                entityManager.persist(Debt.restore(
                        newId, userId, d.name(), d.direction(), d.counterparty(), d.principal(), d.balance(),
                        d.interestRate(), d.startDate(), d.interestMethod(), d.accruedInterest(),
                        d.interestPaidTotal(), d.lastAccruedOn(), d.dueDate(), d.status(), d.createdAt(),
                        d.updatedAt(), d.settledAt()));
            }

            for (DebtPaymentExport p : data.debtPayments()) {
                entityManager.persist(DebtPayment.restore(
                        UUID.randomUUID(), userId, debtIds.get(p.debtId()), p.amount(), p.principalPart(),
                        p.interestPart(), p.paidOn(), p.note(), mapped(ledgerIds.transactionIds(), p.ledgerTxnId()),
                        p.createdAt()));
            }

            for (RecurringBillExport r : data.recurringBills()) {
                UUID newId = UUID.randomUUID();
                billIds.put(r.id(), newId);
                entityManager.persist(RecurringBill.restore(
                        newId, userId, r.name(), mapped(ledgerIds.categoryIds(), r.categoryId()),
                        mapped(ledgerIds.accountIds(), r.accountId()), r.amount(), r.frequency(), r.dayOfPeriod(),
                        r.nextRunDate(), r.autoPost(), r.active(), r.note(), r.createdAt(), r.updatedAt()));
            }

            for (RecurringRunExport r : data.recurringRuns()) {
                entityManager.persist(RecurringRun.restore(
                        UUID.randomUUID(), userId, billIds.get(r.billId()), r.dueDate(),
                        mapped(ledgerIds.transactionIds(), r.ledgerTxnId()), r.skipped(), r.createdAt()));
            }

            for (DebtInterestAccrualExport a : data.debtInterestAccruals()) {
                entityManager.persist(DebtInterestAccrual.restore(
                        UUID.randomUUID(), userId, debtIds.get(a.debtId()), a.period(), a.amount(),
                        a.balanceAtAccrual(), a.createdAt()));
            }

            // Forces the inserts to run now, inside this try block, rather than at commit.
            entityManager.flush();
        } catch (PersistenceException e) {
            throw new ConflictException("This file could not be imported. Check that it's a valid export file.");
        }

        return new ImportSummary(
                data.budgets().size(), data.goals().size(), data.goalContributions().size(), data.debts().size(),
                data.debtPayments().size(), data.recurringBills().size(), data.recurringRuns().size(),
                data.debtInterestAccruals().size());
    }

    /** Null-safe lookup — a null id (e.g. a contribution with no reconciled ledger transaction) stays null. */
    private static UUID mapped(Map<UUID, UUID> map, UUID id) {
        return id == null ? null : map.get(id);
    }
}
