package ph.pesowise.auth.token;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Grants one password change without knowing the current password. Shorter-lived than a
 * verification token because the consequence of a leaked one is immediate account takeover.
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken extends OneTimeToken {

    protected PasswordResetToken() {
        // for JPA
    }

    private PasswordResetToken(UUID userId, String tokenHash, Instant expiresAt) {
        super(userId, tokenHash, expiresAt);
    }

    public static PasswordResetToken issue(UUID userId, String tokenHash, Instant expiresAt) {
        return new PasswordResetToken(userId, tokenHash, expiresAt);
    }
}
