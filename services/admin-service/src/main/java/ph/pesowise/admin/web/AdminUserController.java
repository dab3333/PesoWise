package ph.pesowise.admin.web;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ph.pesowise.admin.clients.AuthAdminDtos.UpdateUserRequest;
import ph.pesowise.admin.clients.AuthAdminDtos.UserPage;
import ph.pesowise.admin.clients.AuthAdminDtos.UserSummary;
import ph.pesowise.admin.service.AdminUserService;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public UserPage list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return adminUserService.list(q, page, Math.min(size, 100));
    }

    @PatchMapping("/{id}")
    public UserSummary update(
            @RequestHeader(Headers.USER_ID) UUID actorUserId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        return adminUserService.update(actorUserId, id, request);
    }
}
