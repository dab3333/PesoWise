package ph.pesowise.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Gateway auth configuration, bound from the {@code pesowise.*} keys in application.yml.
 */
@ConfigurationProperties(prefix = "pesowise")
public class GatewayAuthProperties {

    private final Jwt jwt = new Jwt();
    private final Auth auth = new Auth();

    public Jwt getJwt() {
        return jwt;
    }

    public Auth getAuth() {
        return auth;
    }

    public static class Jwt {
        /** HS256 signing key, shared with auth-service. Injected from the JWT_SECRET env var. */
        private String secret;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }

    public static class Auth {
        /**
         * Request paths that bypass JWT validation. Matched exactly, not by prefix — see
         * {@code JwtAuthenticationFilter.isPublic}.
         */
        private List<String> publicPaths = new ArrayList<>();

        /**
         * Path prefixes that additionally require the ADMIN role. Prefix matching is correct
         * here: it fails closed, so a new admin endpoint is protected the moment it is added.
         */
        private List<String> adminPaths = new ArrayList<>();

        public List<String> getPublicPaths() {
            return publicPaths;
        }

        public void setPublicPaths(List<String> publicPaths) {
            this.publicPaths = publicPaths;
        }

        public List<String> getAdminPaths() {
            return adminPaths;
        }

        public void setAdminPaths(List<String> adminPaths) {
            this.adminPaths = adminPaths;
        }
    }
}
