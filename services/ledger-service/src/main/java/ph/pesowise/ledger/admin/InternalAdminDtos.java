package ph.pesowise.ledger.admin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class InternalAdminDtos {

    private InternalAdminDtos() {
    }

    public record DailyPoint(LocalDate date, BigDecimal income, BigDecimal expense) {
    }

    public record LedgerStats(long transactionCount, long activeUsers, BigDecimal totalIncome,
                              BigDecimal totalExpense, List<DailyPoint> dailyLast30Days) {
    }
}
