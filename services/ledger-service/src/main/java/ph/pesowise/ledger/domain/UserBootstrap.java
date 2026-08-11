package ph.pesowise.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Records that a user's starter accounts and categories have been created. Without this marker,
 * deleting every category would silently re-seed the defaults on the next request.
 */
@Entity
@Table(name = "user_bootstrap")
public class UserBootstrap {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "bootstrapped_at", nullable = false)
    private Instant bootstrappedAt;

    protected UserBootstrap() {
        // for JPA
    }

    public static UserBootstrap of(UUID userId) {
        UserBootstrap marker = new UserBootstrap();
        marker.userId = userId;
        marker.bootstrappedAt = Instant.now();
        return marker;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getBootstrappedAt() {
        return bootstrappedAt;
    }
}
