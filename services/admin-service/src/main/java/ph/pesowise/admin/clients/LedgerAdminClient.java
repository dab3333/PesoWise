package ph.pesowise.admin.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import ph.pesowise.admin.clients.LedgerAdminDtos.LedgerStats;

/** The only route into ledger-service's cross-user surface. See {@link AuthAdminClient}. */
@FeignClient(name = "ledger-admin", url = "${pesowise.services.ledger-url}")
public interface LedgerAdminClient {

    @GetMapping("/internal/admin/stats")
    LedgerStats stats();
}
