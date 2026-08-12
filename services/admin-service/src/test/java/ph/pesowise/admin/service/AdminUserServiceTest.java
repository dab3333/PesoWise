package ph.pesowise.admin.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ph.pesowise.admin.clients.AuthAdminClient;
import ph.pesowise.admin.clients.AuthAdminDtos.UpdateUserRequest;
import ph.pesowise.admin.clients.AuthAdminDtos.UserSummary;
import ph.pesowise.admin.domain.AdminAuditEntry;
import ph.pesowise.admin.repo.AdminAuditRepository;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserServiceTest {

    private final AuthAdminClient authClient = org.mockito.Mockito.mock(AuthAdminClient.class);
    private final AdminAuditRepository audit = org.mockito.Mockito.mock(AdminAuditRepository.class);
    private final AdminUserService service = new AdminUserService(authClient, audit);

    private final UUID actorId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();

    @Test
    @DisplayName("a role change is recorded as USER_ROLE_CHANGED, naming the actor and the target")
    void recordsRoleChange() {
        UserSummary updated = new UserSummary(
                targetId.toString(), "u@example.com", "Maria", "ADMIN", true, false, Instant.now());
        when(authClient.updateUser(eq(targetId), any())).thenReturn(updated);

        UserSummary result = service.update(actorId, targetId, new UpdateUserRequest("ADMIN", null));

        assertThat(result.role()).isEqualTo("ADMIN");

        ArgumentCaptor<AdminAuditEntry> captor = ArgumentCaptor.forClass(AdminAuditEntry.class);
        verify(audit).save(captor.capture());
        assertThat(captor.getValue().getActorUserId()).isEqualTo(actorId);
        assertThat(captor.getValue().getAction()).isEqualTo("USER_ROLE_CHANGED");
        assertThat(captor.getValue().getTargetId()).isEqualTo(targetId);
        assertThat(captor.getValue().getTargetType()).isEqualTo("user");
    }

    @Test
    @DisplayName("disabling an account is recorded as USER_DISABLED")
    void recordsDisable() {
        when(authClient.updateUser(eq(targetId), any())).thenReturn(new UserSummary(
                targetId.toString(), "u@example.com", "Maria", "USER", true, true, Instant.now()));

        service.update(actorId, targetId, new UpdateUserRequest(null, true));

        ArgumentCaptor<AdminAuditEntry> captor = ArgumentCaptor.forClass(AdminAuditEntry.class);
        verify(audit).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("USER_DISABLED");
    }

    @Test
    @DisplayName("re-enabling an account is recorded as USER_ENABLED, not as a disable")
    void recordsEnable() {
        when(authClient.updateUser(eq(targetId), any())).thenReturn(new UserSummary(
                targetId.toString(), "u@example.com", "Maria", "USER", true, false, Instant.now()));

        service.update(actorId, targetId, new UpdateUserRequest(null, false));

        ArgumentCaptor<AdminAuditEntry> captor = ArgumentCaptor.forClass(AdminAuditEntry.class);
        verify(audit).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("USER_ENABLED");
    }

    @Test
    @DisplayName("list is a pass-through to auth-service with no audit entry — reading is not an action")
    void listWritesNoAuditEntry() {
        service.list("maria", 0, 25);

        verify(authClient).users("maria", 0, 25);
        org.mockito.Mockito.verifyNoInteractions(audit);
    }
}
