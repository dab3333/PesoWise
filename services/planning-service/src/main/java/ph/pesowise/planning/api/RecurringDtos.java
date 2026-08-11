package ph.pesowise.planning.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ph.pesowise.planning.domain.RecurringBill;
import ph.pesowise.planning.domain.RecurringRun;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class RecurringDtos {

    private RecurringDtos() {
    }

    private static final String MAX_AMOUNT = "999999999999.99";

    public record BillRequest(
            @NotBlank @Size(max = 80) String name,
            @NotNull UUID categoryId,
            @NotNull UUID accountId,
            @NotNull
            @DecimalMin(value = "0.01", message = "must be greater than zero")
            @DecimalMax(value = MAX_AMOUNT, message = "is unrealistically large")
            @Digits(integer = 13, fraction = 2)
            BigDecimal amount,
            @NotNull RecurringBill.Frequency frequency,
            /** The first occurrence. For monthly bills it also sets the anchor day. */
            @NotNull LocalDate nextRunDate,
            /** When false, the bill is flagged as due and waits for confirmation. */
            boolean autoPost,
            Boolean active,
            @Size(max = 255) String note
    ) {
    }

    /**
     * @param dueNow        the scheduled occurrence has arrived and has not been dealt with
     * @param daysUntilDue  negative when overdue
     */
    public record BillResponse(
            UUID id,
            String name,
            UUID categoryId,
            UUID accountId,
            BigDecimal amount,
            RecurringBill.Frequency frequency,
            Short dayOfPeriod,
            LocalDate nextRunDate,
            Long daysUntilDue,
            boolean dueNow,
            boolean autoPost,
            boolean active,
            String note,
            int postedCount
    ) {
    }

    /**
     * @param dueNow      bills needing attention now, so the UI need not filter
     * @param monthlyTotal what the active bills come to per month, with weekly and yearly ones
     *                     normalised — the figure that tells someone what is already committed
     */
    public record BillOverview(
            BigDecimal monthlyTotal,
            List<BillResponse> dueNow,
            List<BillResponse> bills
    ) {
    }

    public record RunResponse(
            UUID id,
            LocalDate dueDate,
            UUID ledgerTxnId,
            boolean skipped
    ) {
        public static RunResponse from(RecurringRun run) {
            return new RunResponse(run.getId(), run.getDueDate(), run.getLedgerTxnId(), run.isSkipped());
        }
    }

    /**
     * Result of a scheduler pass.
     *
     * @param posted  occurrences recorded
     * @param flagged bills marked due, awaiting confirmation
     * @param skipped occurrences already dealt with — the idempotency guard doing its job
     */
    public record RunSummary(int posted, int flagged, int skipped, List<String> notes) {
    }
}
