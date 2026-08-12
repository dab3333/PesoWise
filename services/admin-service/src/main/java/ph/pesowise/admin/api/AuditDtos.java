package ph.pesowise.admin.api;

import ph.pesowise.admin.domain.AdminAuditEntry;

import java.time.Instant;
import java.util.List;

public final class AuditDtos {

    private AuditDtos() {
    }

    public record AuditEntryResponse(String id, String actorUserId, String action,
                                     String targetType, String targetId, String detail,
                                     Instant createdAt) {
        public static AuditEntryResponse from(AdminAuditEntry e) {
            return new AuditEntryResponse(
                    e.getId().toString(), e.getActorUserId().toString(), e.getAction(),
                    e.getTargetType(), e.getTargetId() == null ? null : e.getTargetId().toString(),
                    e.getDetail(), e.getCreatedAt());
        }
    }

    public record AuditPage(List<AuditEntryResponse> items, int page, int size,
                            long totalItems, int totalPages) {
    }
}
