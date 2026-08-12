package ph.pesowise.auth.token;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import java.time.Instant;
import java.util.UUID;

/**
 * Shared shape for the single-use links this service mails out.
 *
 * <p>Email verification and password reset have different lifetimes and different consequences,
 * so they get separate tables — a bug that redeemed one as the other would be an account
 * takeover. What they share is the redemption rule, which lives here so both enforce it
 * identically: a token is usable only while unexpired and unspent, and redeeming stamps
 * {@code usedAt} rather than deleting the row, so a replayed link is refused rather than
 * looking like a token that never existed.
 *
 * <p>{@code tokenHash} is a SHA-256 of the value that was mailed. The raw token is never stored.
 */
@MappedSuperclass
public abstract class OneTimeToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OneTimeToken() {
        // for JPA
    }

    protected OneTimeToken(UUID userId, String tokenHash, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public boolean isRedeemable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void redeem() {
        this.usedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
