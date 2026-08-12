package ph.pesowise.admin.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.pesowise.admin.clients.AuthAdminClient;
import ph.pesowise.admin.clients.AuthAdminDtos.UpdateUserRequest;
import ph.pesowise.admin.clients.AuthAdminDtos.UserPage;
import ph.pesowise.admin.clients.AuthAdminDtos.UserSummary;
import ph.pesowise.admin.domain.AdminAuditEntry;
import ph.pesowise.admin.repo.AdminAuditRepository;

import java.util.UUID;

/**
 * A thin proxy over auth-service's user data, plus the one thing that belongs here instead:
 * recording that an admin made the change. auth-service owns the user; this service owns the
 * fact that someone acted on it.
 */
@Service
public class AdminUserService {

    private final AuthAdminClient authClient;
    private final AdminAuditRepository audit;

    public AdminUserService(AuthAdminClient authClient, AdminAuditRepository audit) {
        this.authClient = authClient;
        this.audit = audit;
    }

    public UserPage list(String q, int page, int size) {
        return authClient.users(q, page, size);
    }

    @Transactional
    public UserSummary update(UUID actorUserId, UUID targetUserId, UpdateUserRequest request) {
        UserSummary updated = authClient.updateUser(targetUserId, request);

        String action = request.role() != null ? "USER_ROLE_CHANGED"
                : Boolean.TRUE.equals(request.disabled()) ? "USER_DISABLED" : "USER_ENABLED";
        String detail = "role=%s, disabled=%s".formatted(request.role(), request.disabled());
        audit.save(AdminAuditEntry.record(actorUserId, action, "user", targetUserId, detail));

        return updated;
    }
}
