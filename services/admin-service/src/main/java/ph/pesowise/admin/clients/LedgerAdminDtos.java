package ph.pesowise.admin.clients;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Mirrors ledger-service's {@code InternalAdminDtos} — the shape of {@code /internal/admin/**}. */
public final class LedgerAdminDtos {

    private LedgerAdminDtos() {
    }

    public record DailyPoint(LocalDate date, BigDecimal income, BigDecimal expense) {
    }

    public record LedgerStats(long transactionCount, long activeUsers, BigDecimal totalIncome,
                              BigDecimal totalExpense, List<DailyPoint> dailyLast30Days) {
    }
}
