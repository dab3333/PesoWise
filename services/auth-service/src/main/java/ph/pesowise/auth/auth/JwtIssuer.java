package ph.pesowise.auth.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import ph.pesowise.auth.config.JwtProperties;
import ph.pesowise.auth.user.User;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Signs HS256 access tokens. The gateway is the only verifier, and it reads the subject —
 * so the subject must always be the user id, never the email.
 */
@Component
public class JwtIssuer {

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtIssuer(JwtProperties properties) {
        this.properties = properties;

        String secret = properties.getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set and at least 32 bytes long for HS256 signing");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issue(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(properties.getExpirationMs());

        return Jwts.builder()
                .subject(user.getId().toString())
                .issuer(properties.getIssuer())
                // Informational only — downstream services must key off the subject.
                .claim("email", user.getEmail())
                .claim("name", user.getDisplayName())
                // Load-bearing, unlike the two above: the gateway reads this to decide whether a
                // request may reach /api/admin/**. It is signed, so a client cannot alter it —
                // but it is also a snapshot. A user demoted mid-session keeps admin access until
                // their token expires, which is the price of stateless auth and the reason token
                // lifetime is 24h rather than longer.
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public long expiresInSeconds() {
        return properties.getExpirationMs() / 1000;
    }
}
