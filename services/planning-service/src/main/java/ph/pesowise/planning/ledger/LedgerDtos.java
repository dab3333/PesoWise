package ph.pesowise.planning.ledger;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Local mirrors of the ledger-service response shapes.
 *
 * <p>Duplicated rather than shared through a common module: a shared DTO artifact would couple the
 * two services' build and release cycles, which is most of what the service split exists to avoid.
 * Every record is {@code @JsonIgnoreProperties(ignoreUnknown = true)}, so ledger-service can add
 * fields without breaking this service.
 */
public final class LedgerDtos {

    private LedgerDtos() {
    }

    public enum Kind {
        INCOME, EXPENSE
    }

    public enum Bucket {
        NEEDS, WANTS, SAVINGS
    }

    public enum SourceType {
        MANUAL, RECURRING_BILL, DEBT_PAYMENT, GOAL_CONTRIBUTION
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Category(UUID id, String name, Kind kind, Bucket bucket, String color, boolean system) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Account(UUID id, String name, String type, BigDecimal balance) {
    }

    /** @param bucket null for income categories, which carry no 70-20-10 bucket */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CategoryTotal(
            UUID categoryId, String categoryName, String color, Kind kind, Bucket bucket, BigDecimal total) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Summary(String month, BigDecimal income, BigDecimal expense, BigDecimal net) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Transaction(UUID id, BigDecimal amount, LocalDate txnDate, Kind kind) {
    }

    public record SourcedTransactionRequest(
            UUID accountId,
            UUID categoryId,
            BigDecimal amount,
            LocalDate txnDate,
            String note,
            SourceType sourceType,
            UUID sourceId) {
    }
}
