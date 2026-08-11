package ph.pesowise.ledger.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ph.pesowise.ledger.domain.Account;
import ph.pesowise.ledger.domain.Category;
import ph.pesowise.ledger.domain.Enums.AccountType;
import ph.pesowise.ledger.domain.Enums.Bucket;
import ph.pesowise.ledger.domain.Enums.Kind;
import ph.pesowise.ledger.domain.Enums.SourceType;
import ph.pesowise.ledger.domain.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request and response payloads for the ledger endpoints. */
public final class LedgerDtos {

    private LedgerDtos() {
    }

    /** Guards against a stray extra digit turning ₱500 into ₱5,000,000,000. */
    private static final String MAX_AMOUNT = "999999999999.99";

    /* --------------------------------------------------------------- accounts */

    public record AccountRequest(
            @NotBlank @Size(max = 60) String name,
            @NotNull AccountType type,
            // Credit cards legitimately start negative, so no lower bound beyond scale.
            @NotNull @Digits(integer = 13, fraction = 2) BigDecimal openingBalance
    ) {
    }

    public record AccountResponse(
            UUID id,
            String name,
            AccountType type,
            BigDecimal openingBalance,
            /** Derived: opening balance + income − expense. Never stored. */
            BigDecimal balance
    ) {
    }

    /* ------------------------------------------------------------- categories */

    public record CategoryRequest(
            @NotBlank @Size(max = 60) String name,
            @NotNull Kind kind,
            /** Required for EXPENSE, ignored for INCOME — income carries no 70-20-10 bucket. */
            Bucket bucket,
            @NotBlank @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "must be a hex colour like #0f8a6c")
            String color
    ) {
    }

    public record CategoryResponse(
            UUID id, String name, Kind kind, Bucket bucket, String color, boolean system
    ) {
        public static CategoryResponse from(Category category) {
            return new CategoryResponse(
                    category.getId(), category.getName(), category.getKind(),
                    category.getBucket(), category.getColor(), category.isSystem());
        }
    }

    /* ----------------------------------------------------------- transactions */

    public record TransactionRequest(
            @NotNull UUID accountId,
            /** The category decides income vs expense; the client never sends a kind. */
            @NotNull UUID categoryId,
            @NotNull
            @DecimalMin(value = "0.01", message = "must be greater than zero")
            @DecimalMax(value = MAX_AMOUNT, message = "is unrealistically large")
            @Digits(integer = 13, fraction = 2)
            BigDecimal amount,
            @NotNull LocalDate txnDate,
            @Size(max = 255) String note
    ) {
    }

    public record TransactionResponse(
            UUID id,
            UUID accountId,
            String accountName,
            UUID categoryId,
            String categoryName,
            String categoryColor,
            Kind kind,
            BigDecimal amount,
            LocalDate txnDate,
            String note,
            SourceType sourceType,
            UUID sourceId
    ) {
        public static TransactionResponse from(Transaction txn, Account account, Category category) {
            return new TransactionResponse(
                    txn.getId(),
                    txn.getAccountId(), account == null ? "—" : account.getName(),
                    txn.getCategoryId(), category == null ? "—" : category.getName(),
                    category == null ? "#64748b" : category.getColor(),
                    txn.getKind(), txn.getAmount(), txn.getTxnDate(), txn.getNote(),
                    txn.getSourceType(), txn.getSourceId());
        }
    }

    /** Hand-rolled rather than returning Spring's Page, whose JSON shape is unstable. */
    public record PageResponse<T>(
            List<T> content, int page, int size, long totalElements, int totalPages
    ) {
    }

    /* --------------------------------------------------------------- reports */

    public record SummaryResponse(
            String month, BigDecimal income, BigDecimal expense, BigDecimal net
    ) {
    }

    public record CategoryTotalResponse(
            UUID categoryId, String categoryName, String color, Kind kind, BigDecimal total
    ) {
    }

    /**
     * One 70-20-10 bucket: what the method targets against what was actually spent.
     *
     * @param target the share of income the method allocates (70, 20 or 10)
     */
    public record BucketBreakdownResponse(
            Bucket bucket,
            int targetPercent,
            BigDecimal targetAmount,
            BigDecimal actualAmount,
            BigDecimal actualPercent
    ) {
    }

    public record DailyTotalResponse(LocalDate date, BigDecimal income, BigDecimal expense) {
    }

    /**
     * Internal endpoint used by planning-service to post a transaction it is responsible for
     * (a debt payment, goal contribution, or recurring bill).
     */
    public record SourcedTransactionRequest(
            @NotNull UUID accountId,
            @NotNull UUID categoryId,
            @NotNull @DecimalMin("0.01") @DecimalMax(MAX_AMOUNT) @Digits(integer = 13, fraction = 2)
            BigDecimal amount,
            @NotNull LocalDate txnDate,
            @Size(max = 255) String note,
            @NotNull SourceType sourceType,
            @NotNull UUID sourceId
    ) {
    }
}
