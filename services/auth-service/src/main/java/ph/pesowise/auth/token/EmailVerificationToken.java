package ph.pesowise.auth.token;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Proves a registrant can read the address they signed up with. */
@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationToken extends OneTimeToken {

    protected EmailVerificationToken() {
        // for JPA
    }

    private EmailVerificationToken(UUID userId, String tokenHash, Instant expiresAt) {
        super(userId, tokenHash, expiresAt);
    }

    public static EmailVerificationToken issue(UUID userId, String tokenHash, Instant expiresAt) {
        return new EmailVerificationToken(userId, tokenHash, expiresAt);
    }
}
