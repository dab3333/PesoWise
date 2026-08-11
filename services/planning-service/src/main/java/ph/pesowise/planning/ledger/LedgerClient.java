package ph.pesowise.planning.ledger;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The only route into ledger-service.
 *
 * <p>Calls go direct over the Compose network rather than back out through the gateway — a second
 * hop would add latency and a needless dependency on the gateway being healthy. Because the
 * gateway is bypassed, {@code X-User-Id} is passed explicitly on every call, and it must always be
 * the id from the inbound request. Passing anything else would let one user's budget read another
 * user's spending.
 */
@FeignClient(name = "ledger", url = "${pesowise.ledger.url}")
public interface LedgerClient {

    @GetMapping("/api/categories")
    List<LedgerDtos.Category> categories(@RequestHeader("X-User-Id") UUID userId);

    @GetMapping("/api/accounts")
    List<LedgerDtos.Account> accounts(@RequestHeader("X-User-Id") UUID userId);

    /** Totals per category over a window. Includes categories with no activity, at zero. */
    @GetMapping("/api/reports/by-category")
    List<LedgerDtos.CategoryTotal> spendByCategory(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam("from") LocalDate from,
            @RequestParam("to") LocalDate to);

    @GetMapping("/api/reports/summary")
    LedgerDtos.Summary summary(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam("month") String month);

    /**
     * Records a transaction that this service is responsible for — a debt payment, goal
     * contribution, or posted recurring bill. Money is only ever written in one place.
     */
    @PostMapping("/api/transactions/sourced")
    LedgerDtos.Transaction createSourcedTransaction(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody LedgerDtos.SourcedTransactionRequest request);

    /**
     * Removes a transaction this service created, when the originating payment or contribution is
     * deleted. Without this, reversing a payment would leave the cash movement in the ledger and
     * the two records would disagree.
     */
    @DeleteMapping("/api/transactions/{id}")
    void deleteTransaction(@RequestHeader("X-User-Id") UUID userId, @PathVariable("id") UUID id);
}
