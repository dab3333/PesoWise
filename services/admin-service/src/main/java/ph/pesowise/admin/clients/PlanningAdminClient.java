package ph.pesowise.admin.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import ph.pesowise.admin.clients.PlanningAdminDtos.PlanningStats;

/** The only route into planning-service's cross-user surface. See {@link AuthAdminClient}. */
@FeignClient(name = "planning-admin", url = "${pesowise.services.planning-url}")
public interface PlanningAdminClient {

    @GetMapping("/internal/admin/stats")
    PlanningStats stats();
}
