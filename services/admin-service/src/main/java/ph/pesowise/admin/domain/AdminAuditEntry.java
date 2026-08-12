package ph.pesowise.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One administrative action, kept in its own table rather than folded into a generic activity
 * log. An admin acting on a user's account is a different kind of event from a user acting on
 * their own data — it is the thing a future "who changed this and why" question is asked about —
 * and every service having to reason about that distinction in a shared log would be worse than
 * one small table here.
 */
@Entity
@Table(name = "admin_audit")
public class AdminAuditEntry {

    @Id
    private UUID id;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(nullable = false, length = 40, updatable = false)
    private String action;

    @Column(name = "target_type", length = 20, updatable = false)
    private String targetType;

    @Column(name = "target_id", updatable = false)
    private UUID targetId;

    @Column(length = 500, updatable = false)
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AdminAuditEntry() {
        // for JPA
    }

    public static AdminAuditEntry record(
            UUID actorUserId, String action, String targetType, UUID targetId, String detail) {
        AdminAuditEntry entry = new AdminAuditEntry();
        entry.id = UUID.randomUUID();
        entry.actorUserId = actorUserId;
        entry.action = action;
        entry.targetType = targetType;
        entry.targetId = targetId;
        entry.detail = detail;
        entry.createdAt = Instant.now();
        return entry;
    }

    public UUID getId() {
        return id;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
