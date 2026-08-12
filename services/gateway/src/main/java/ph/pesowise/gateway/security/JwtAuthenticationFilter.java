package ph.pesowise.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Validates the bearer token on every non-public request and forwards the token subject
 * to downstream services as {@code X-User-Id}.
 *
 * <p>Downstream services trust {@code X-User-Id} and {@code X-User-Role} unconditionally, so this
 * filter is the only thing standing between a client and impersonation. Two rules keep that safe:
 * <ol>
 *   <li>Any client-supplied copy of either header is stripped before routing — always, including
 *       on public paths and preflights — so they can only ever originate here.</li>
 *   <li>The services are never published on host ports in production; the gateway is the only
 *       reachable entrypoint.</li>
 * </ol>
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";

    /**
     * The caller's role, for services that want to re-check rather than assume the gateway got
     * it right. Authorisation is decided here; this header is defence in depth, not the gate.
     */
    public static final String USER_ROLE_HEADER = "X-User-Role";

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_CLAIM = "role";
    private static final String ADMIN_ROLE = "ADMIN";

    private final GatewayAuthProperties properties;
    private final SecretKey signingKey;

    public JwtAuthenticationFilter(GatewayAuthProperties properties) {
        this.properties = properties;

        String secret = properties.getJwt().getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set and at least 32 bytes long for HS256 signing");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        String path = request.getURI().getPath();

        // Never let a caller inject its own identity or authority.
        ServerHttpRequest sanitized = request.mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_ROLE_HEADER);
                })
                .build();

        // CORS preflight carries no Authorization header by design.
        if (HttpMethod.OPTIONS.equals(request.getMethod()) || isPublic(path)) {
            return chain.filter(exchange.mutate().request(sanitized).build());
        }

        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, "missing bearer token");
        }

        String userId;
        String role;
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(header.substring(BEARER_PREFIX.length()).trim())
                    .getPayload();
            userId = claims.getSubject();
            role = claims.get(ROLE_CLAIM, String.class);
        } catch (JwtException | IllegalArgumentException e) {
            // Covers bad signature, malformed token, and expiry.
            return unauthorized(exchange, e.getMessage());
        }

        if (userId == null || userId.isBlank()) {
            return unauthorized(exchange, "token has no subject");
        }

        // Tokens issued before the role claim existed have none. Treating that as USER rather
        // than rejecting keeps sessions alive across the upgrade; it can never grant admin.
        String effectiveRole = (role == null || role.isBlank()) ? "USER" : role;

        if (requiresAdmin(path) && !ADMIN_ROLE.equals(effectiveRole)) {
            // 403, not 401: the token is valid and the caller is authenticated. Answering 401
            // would tell the frontend to bounce them to the sign-in page, which cannot help.
            return forbidden(exchange, userId);
        }

        ServerHttpRequest authenticated = sanitized.mutate()
                .header(USER_ID_HEADER, userId)
                .header(USER_ROLE_HEADER, effectiveRole)
                .build();
        return chain.filter(exchange.mutate().request(authenticated).build());
    }

    /**
     * Exact match, deliberately not a prefix match.
     *
     * <p>A prefix match would make {@code /api/auth/login} open up anything that merely starts
     * with it, and this list now sits alongside sensitive siblings under {@code /api/auth/}.
     * Exact matching is only sufficient because no public endpoint takes a path variable — the
     * verification and reset tokens travel in the request body for exactly this reason.
     */
    private boolean isPublic(String path) {
        return properties.getAuth().getPublicPaths().contains(path);
    }

    /** Prefix match, which fails closed: a new endpoint under an admin prefix is covered. */
    private boolean requiresAdmin(String path) {
        return properties.getAuth().getAdminPaths().stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String reason) {
        // Logged at debug only: the reason can describe token internals.
        log.debug("Rejecting {} {}: {}",
                exchange.getRequest().getMethod(), exchange.getRequest().getURI().getPath(), reason);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String userId) {
        // At INFO: a non-admin reaching an admin path is worth noticing, unlike a routine
        // expired token.
        log.info("Denying {} {} to non-admin {}",
                exchange.getRequest().getMethod(), exchange.getRequest().getURI().getPath(), userId);
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        // Ahead of the routing filter so rejected requests never reach a downstream service.
        return -100;
    }
}
