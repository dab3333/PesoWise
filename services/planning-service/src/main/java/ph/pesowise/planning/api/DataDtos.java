package ph.pesowise.planning.api;

import ph.pesowise.planning.domain.Debt.Direction;
import ph.pesowise.planning.domain.Debt.InterestMethod;
import ph.pesowise.planning.domain.Debt.Status;
import ph.pesowise.planning.domain.RecurringBill.Frequency;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Payloads for the Settings page's export/import feature — see {@code DataExportService}. */
public final class DataDtos {

    private DataDtos() {
    }

    /**
     * A full copy of one user's planning data. Parents are listed before their children —
     * {@code goals} before {@code goalContributions}, {@code debts} before {@code debtPayments}/
     * {@code debtInterestAccruals}, {@code recurringBills} before {@code recurringRuns} — which
     * is also the order import must insert them in, since the children's foreign keys point at
     * the parents.
     */
    public record PlanningExport(
            List<BudgetExport> budgets,
            List<GoalExport> goals,
            List<GoalContributionExport> goalContributions,
            List<DebtExport> debts,
            List<DebtPaymentExport> debtPayments,
            List<RecurringBillExport> recurringBills,
            List<RecurringRunExport> recurringRuns,
            List<DebtInterestAccrualExport> debtInterestAccruals
    ) {
    }

    public record BudgetExport(
            UUID id, UUID categoryId, YearMonth month, BigDecimal limitAmount, Instant createdAt, Instant updatedAt
    ) {
    }

    public record GoalExport(
            UUID id, String name, BigDecimal targetAmount, LocalDate targetDate, boolean archived, String note,
            Instant createdAt, Instant updatedAt
    ) {
    }

    public record GoalContributionExport(
            UUID id, UUID goalId, BigDecimal amount, LocalDate contributedOn, String note, UUID ledgerTxnId,
            Instant createdAt
    ) {
    }

    public record DebtExport(
            UUID id, String name, Direction direction, String counterparty, BigDecimal principal,
            BigDecimal balance, BigDecimal interestRate, LocalDate startDate, InterestMethod interestMethod,
            BigDecimal accruedInterest, BigDecimal interestPaidTotal, LocalDate lastAccruedOn, LocalDate dueDate,
            Status status, Instant createdAt, Instant updatedAt, Instant settledAt
    ) {
    }

    public record DebtPaymentExport(
            UUID id, UUID debtId, BigDecimal amount, BigDecimal principalPart, BigDecimal interestPart,
            LocalDate paidOn, String note, UUID ledgerTxnId, Instant createdAt
    ) {
    }

    public record RecurringBillExport(
            UUID id, String name, UUID categoryId, UUID accountId, BigDecimal amount, Frequency frequency,
            Short dayOfPeriod, LocalDate nextRunDate, boolean autoPost, boolean active, String note,
            Instant createdAt, Instant updatedAt
    ) {
    }

    public record RecurringRunExport(
            UUID id, UUID billId, LocalDate dueDate, UUID ledgerTxnId, boolean skipped, Instant createdAt
    ) {
    }

    public record DebtInterestAccrualExport(
            UUID id, UUID debtId, LocalDate period, BigDecimal amount, BigDecimal balanceAtAccrual, Instant createdAt
    ) {
    }

    /** One count per table, so the frontend can show real numbers rather than just "done." */
    public record ImportSummary(
            int budgets, int goals, int goalContributions, int debts, int debtPayments, int recurringBills,
            int recurringRuns, int debtInterestAccruals
    ) {
    }

    /**
     * The old-id → new-id maps ledger-service's import returns, since this data references
     * ledger ids of its own — {@code category_id}/{@code account_id} on budgets and recurring
     * bills, {@code ledger_txn_id} on debt payments/goal contributions/recurring runs. Planning's
     * import uses these to remap its own file's references onto what ledger-service just
     * regenerated, in the same overall import.
     */
    public record LedgerIdMap(Map<UUID, UUID> categoryIds, Map<UUID, UUID> accountIds, Map<UUID, UUID> transactionIds) {
    }

    /** The full request body for {@code POST /api/data/planning/import}. */
    public record PlanningImportRequest(PlanningExport data, LedgerIdMap ledgerIds) {
    }
}
