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
 * <p>Downstream services trust {@code X-User-Id} unconditionally, so this filter is the only
 * thing standing between a client and impersonation. Two rules keep that safe:
 * <ol>
 *   <li>Any client-supplied {@code X-User-Id} is stripped before routing — always, including on
 *       public paths — so the header can only ever originate here.</li>
 *   <li>The services are never published on host ports in production; the gateway is the only
 *       reachable entrypoint.</li>
 * </ol>
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

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

        // Never let a caller inject its own identity header.
        ServerHttpRequest sanitized = request.mutate()
                .headers(headers -> headers.remove(USER_ID_HEADER))
                .build();

        // CORS preflight carries no Authorization header by design.
        if (HttpMethod.OPTIONS.equals(request.getMethod()) || isPublic(request.getURI().getPath())) {
            return chain.filter(exchange.mutate().request(sanitized).build());
        }

        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return reject(exchange, "missing bearer token");
        }

        String userId;
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(header.substring(BEARER_PREFIX.length()).trim())
                    .getPayload();
            userId = claims.getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            // Covers bad signature, malformed token, and expiry.
            return reject(exchange, e.getMessage());
        }

        if (userId == null || userId.isBlank()) {
            return reject(exchange, "token has no subject");
        }

        ServerHttpRequest authenticated = sanitized.mutate()
                .header(USER_ID_HEADER, userId)
                .build();
        return chain.filter(exchange.mutate().request(authenticated).build());
    }

    private boolean isPublic(String path) {
        return properties.getAuth().getPublicPaths().stream().anyMatch(path::startsWith);
    }

    private Mono<Void> reject(ServerWebExchange exchange, String reason) {
        // Logged at debug only: the reason can describe token internals.
        log.debug("Rejecting {} {}: {}",
                exchange.getRequest().getMethod(), exchange.getRequest().getURI().getPath(), reason);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        // Ahead of the routing filter so rejected requests never reach a downstream service.
        return -100;
    }
}
