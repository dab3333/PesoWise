package ph.pesowise.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pesowise.jwt")
public class JwtProperties {

    /** HS256 signing key. Must match the gateway's JWT_SECRET or every token is rejected. */
    private String secret;

    /** Token lifetime in milliseconds. Defaults to 24 hours. */
    private long expirationMs = 86_400_000L;

    private String issuer = "pesowise-auth";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
