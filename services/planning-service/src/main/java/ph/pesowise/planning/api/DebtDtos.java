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

    public record DebtRequest(
            @NotBlank @Size(max = 80) String name,
            @NotNull Debt.Direction direction,
            @Size(max = 80) String counterparty,
            @NotNull
            @DecimalMin(value = "0.01", message = "must be greater than zero")
            @DecimalMax(value = MAX_AMOUNT, message = "is unrealistically large")
            @Digits(integer = 13, fraction = 2)
            BigDecimal principal,
            /** Recorded for reference only — the MVP does not accrue interest. */
            @DecimalMin(value = "0.0", message = "cannot be negative")
            @Digits(integer = 3, fraction = 3)
            BigDecimal interestRate,
            LocalDate dueDate
    ) {
    }

    /** Only the descriptive fields are editable; the balance moves through payments alone. */
    public record DebtUpdateRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 80) String counterparty,
            @DecimalMin(value = "0.0", message = "cannot be negative")
            @Digits(integer = 3, fraction = 3)
            BigDecimal interestRate,
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
            LocalDate paidOn,
            String note,
            /** The ledger transaction this payment created. */
            UUID ledgerTxnId
    ) {
        public static PaymentResponse from(DebtPayment payment) {
            return new PaymentResponse(
                    payment.getId(), payment.getDebtId(), payment.getAmount(),
                    payment.getPaidOn(), payment.getNote(), payment.getLedgerTxnId());
        }
    }

    /**
     * @param paidAmount    principal − balance, so the UI need not subtract
     * @param percentPaid   progress toward settled
     * @param daysUntilDue  negative when overdue; null when the debt has no due date
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
}
