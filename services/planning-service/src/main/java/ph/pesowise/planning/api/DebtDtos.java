package ph.pesowise.planning.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ph.pesowise.planning.domain.Debt;
import ph.pesowise.planning.domain.DebtPayment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class DebtDtos {

    private DebtDtos() {
    }

    private static final String MAX_AMOUNT = "999999999999.99";

    /**
     * @param accountId  only meaningful for {@code OWED_TO_ME} — which wallet the money actually
     *                   left when it was lent out. Optional: some debts predate the app, or were
     *                   never cash to begin with. Must be paired with {@code categoryId} or left
     *                   out entirely.
     * @param categoryId how that outflow should appear in spending reports, same role as on
     *                   {@link PaymentRequest}
     */
    public record DebtRequest(
            @NotBlank @Size(max = 80) String name,
            @NotNull Debt.Direction direction,
            @Size(max = 80) String counterparty,
            @NotNull
            @DecimalMin(value = "0.01", message = "must be greater than zero")
            @DecimalMax(value = MAX_AMOUNT, message = "is unrealistically large")
            @Digits(integer = 13, fraction = 2)
            BigDecimal principal,
            /** Meaningless without {@code interestMethod} — recorded for reference only then. */
            @DecimalMin(value = "0.0", message = "cannot be negative")
            @Digits(integer = 3, fraction = 3)
            BigDecimal interestRate,
            /** Null means no interest accrues, regardless of what {@code interestRate} holds. */
            Debt.InterestMethod interestMethod,
            /** When accrual starts counting from — immutable once the debt exists. */
            @NotNull LocalDate startDate,
            LocalDate dueDate,
            UUID accountId,
            UUID categoryId
    ) {
    }

    /**
     * Only the descriptive fields are editable; the balance moves through payments alone, and
     * {@code startDate} is fixed — changing it would retroactively change what should already
     * have accrued.
     */
    public record DebtUpdateRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 80) String counterparty,
            @DecimalMin(value = "0.0", message = "cannot be negative")
            @Digits(integer = 3, fraction = 3)
            BigDecimal interestRate,
            Debt.InterestMethod interestMethod,
            LocalDate dueDate
    ) {
    }

    /**
     * @param accountId  which wallet the money moved through — the user's choice, not a guess
     * @param categoryId how the payment should appear in spending reports
     */
    public record PaymentRequest(
            @NotNull
            @DecimalMin(value = "0.01", message = "must be greater than zero")
            @DecimalMax(value = MAX_AMOUNT, message = "is unrealistically large")
            @Digits(integer = 13, fraction = 2)
            BigDecimal amount,
            @NotNull LocalDate paidOn,
            @NotNull UUID accountId,
            @NotNull UUID categoryId,
            @Size(max = 255) String note
    ) {
    }

    public record PaymentResponse(
            UUID id,
            UUID debtId,
            BigDecimal amount,
            BigDecimal principalPart,
            BigDecimal interestPart,
            LocalDate paidOn,
            String note,
            /** The ledger transaction this payment created. */
            UUID ledgerTxnId
    ) {
        public static PaymentResponse from(DebtPayment payment) {
            return new PaymentResponse(
                    payment.getId(), payment.getDebtId(), payment.getAmount(),
                    payment.getPrincipalPart(), payment.getInterestPart(),
                    payment.getPaidOn(), payment.getNote(), payment.getLedgerTxnId());
        }
    }

    /**
     * @param paidAmount        principal − balance, so the UI need not subtract
     * @param percentPaid       percent of principal repaid — interest is reported separately,
     *                          since paying it off does not touch what was originally borrowed
     * @param accruedInterest   currently outstanding, unpaid interest
     * @param interestPaidTotal lifetime interest actually paid — distinct from the above, which
     *                          shrinks as it's paid off
     * @param totalOutstanding  balance + accruedInterest — what settling this debt actually costs
     * @param daysUntilDue      negative when overdue; null when the debt has no due date
     */
    public record DebtResponse(
            UUID id,
            String name,
            Debt.Direction direction,
            String counterparty,
            BigDecimal principal,
            BigDecimal balance,
            BigDecimal paidAmount,
            BigDecimal percentPaid,
            BigDecimal interestRate,
            Debt.InterestMethod interestMethod,
            BigDecimal accruedInterest,
            BigDecimal interestPaidTotal,
            BigDecimal totalOutstanding,
            LocalDate startDate,
            LocalDate dueDate,
            Long daysUntilDue,
            boolean overdue,
            Debt.Status status,
            int paymentCount
    ) {
    }

    /**
     * @param netPosition owedToMe − owedByMe: positive means more is owed to the user than by them
     */
    public record DebtOverview(
            BigDecimal totalOwedByMe,
            BigDecimal totalOwedToMe,
            BigDecimal netPosition,
            List<DebtResponse> debts
    ) {
    }

    /** What one accrual pass did, across every interest-bearing debt — mirrors RunSummary. */
    public record AccrualSummary(int accrued, int alreadyRecorded, List<String> notes) {
    }
}
