package ph.pesowise.ledger.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ph.pesowise.ledger.admin.InternalAdminDtos.DailyPoint;
import ph.pesowise.ledger.admin.InternalAdminDtos.LedgerStats;
import ph.pesowise.ledger.repo.TransactionRepository;

/**
 * The cross-user aggregate this service was never asked for until an admin overview needed it.
 *
 * <p>Mounted at {@code /internal/admin}, not {@code /api/admin} — the gateway has no route for
 * {@code /internal/**}, so this is unreachable from outside the Compose network. admin-service is
 * the only caller, over the same network trust model planning-service already uses to reach
 * {@code /api/**} on this service directly.
 */
@RestController
@RequestMapping("/internal/admin")
public class InternalAdminController {

    private final TransactionRepository transactions;

    public InternalAdminController(TransactionRepository transactions) {
        this.transactions = transactions;
    }

    @GetMapping("/stats")
    public LedgerStats stats() {
        var totals = transactions.findSystemTotals();
        var daily = transactions.findSystemDailyTotals().stream()
                .map(row -> new DailyPoint(row.getDay(), row.getIncome(), row.getExpense()))
                .toList();

        return new LedgerStats(
                totals.getTransactionCount(), totals.getActiveUsers(),
                totals.getIncome(), totals.getExpense(), daily);
    }
}
