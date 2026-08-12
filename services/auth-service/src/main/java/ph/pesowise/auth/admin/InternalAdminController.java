package ph.pesowise.auth.admin;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ph.pesowise.auth.admin.InternalAdminDtos.UpdateUserRequest;
import ph.pesowise.auth.admin.InternalAdminDtos.UserPage;
import ph.pesowise.auth.admin.InternalAdminDtos.UserStats;
import ph.pesowise.auth.admin.InternalAdminDtos.UserSummary;

import java.util.UUID;

/**
 * The cross-user surface admin-service composes into the admin panel.
 *
 * <p>Mounted at {@code /internal/admin}, not {@code /api/admin} — the gateway has no route for
 * {@code /internal/**}, so these endpoints are unreachable from outside the Compose network.
 * admin-service is the only caller, exactly as planning-service is the only caller of
 * ledger-service's {@code /api} endpoints. There is no per-request authorisation check here: the
 * trust boundary is the network, not a header, which is the same model the rest of this codebase
 * already relies on for inter-service calls.
 */
@RestController
@RequestMapping("/internal/admin")
public class InternalAdminController {

    private final InternalAdminService adminService;

    public InternalAdminController(InternalAdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * No upper bound on {@code size} here, unlike the public-facing admin endpoint that fronts
     * this one: this is only ever called by admin-service, which uses a large page size once to
     * build a CSV export as well as small ones to paginate the UI table.
     */
    @GetMapping("/users")
    public UserPage listUsers(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return adminService.listUsers(q, page, size);
    }

    @PatchMapping("/users/{id}")
    public UserSummary updateUser(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return adminService.updateUser(id, request);
    }

    @GetMapping("/users/stats")
    public UserStats stats() {
        return adminService.stats();
    }
}
