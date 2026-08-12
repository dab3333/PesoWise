package ph.pesowise.admin.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import ph.pesowise.admin.clients.AuthAdminDtos.UpdateUserRequest;
import ph.pesowise.admin.clients.AuthAdminDtos.UserPage;
import ph.pesowise.admin.clients.AuthAdminDtos.UserStats;
import ph.pesowise.admin.clients.AuthAdminDtos.UserSummary;

import java.util.UUID;

/**
 * The only route into auth-service's cross-user surface.
 *
 * <p>Calls go direct over the Compose network rather than back out through the gateway — the
 * same choice planning-service already made for ledger-service, for the same reason: a second
 * hop through the gateway would add latency and a needless dependency on it staying healthy.
 * These specific paths are unreachable any other way, since the gateway has no route for
 * {@code /internal/**} at all.
 */
@FeignClient(name = "auth-admin", url = "${pesowise.services.auth-url}")
public interface AuthAdminClient {

    @GetMapping("/internal/admin/users")
    UserPage users(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam("page") int page,
            @RequestParam("size") int size);

    @PatchMapping("/internal/admin/users/{id}")
    UserSummary updateUser(@PathVariable("id") UUID id, @RequestBody UpdateUserRequest request);

    @GetMapping("/internal/admin/users/stats")
    UserStats stats();
}
