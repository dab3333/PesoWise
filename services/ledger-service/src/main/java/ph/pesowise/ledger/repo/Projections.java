package ph.pesowise.ledger.repo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read-only shapes returned by the native aggregate queries. Spring Data maps result columns to
 * these getters by alias, so the alias names in the SQL must match.
 */
public final class Projections {

    private Projections() {
    }

    /** One row per account: the derived current balance. */
    public interface AccountBalance {
        UUID getAccountId();

        BigDecimal getBalance();
    }

    /** Total income and expense for a period. */
    public interface PeriodTotals {
        BigDecimal getIncome();

        BigDecimal getExpense();
    }

    /** Spend (or income) grouped by category — drives the bar chart and every budget lookup. */
    public interface CategoryTotal {
        UUID getCategoryId();

        String getCategoryName();

        String getColor();

        String getKind();

        /** Null for income categories, which carry no 70-20-10 bucket. */
        String getBucket();

        BigDecimal getTotal();
    }

    /** Expense grouped by 70-20-10 bucket. */
    public interface BucketTotal {
        String getBucket();

        BigDecimal getTotal();
    }

    /** One row per day that has activity — drives the trend line. */
    public interface DailyTotal {
        LocalDate getDay();

        BigDecimal getIncome();

        BigDecimal getExpense();
    }

    /** Across every user — backs the admin overview, never a user-facing report. */
    public interface SystemTotals {
        long getTransactionCount();

        long getActiveUsers();

        BigDecimal getIncome();

        BigDecimal getExpense();
    }
}
