package ph.pesowise.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-that-is-definitely-long-enough-for-hs256";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    private static final String USER_ID = "3f1c9e2a-0000-4000-8000-000000000001";

    private JwtAuthenticationFilter filter;

    /** Captures the request the filter forwards, so we can assert on the injected headers. */
    private AtomicReference<ServerHttpRequest> forwarded;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(propertiesWith(SECRET));
        forwarded = new AtomicReference<>();
        chain = exchange -> {
            forwarded.set(exchange.getRequest());
            return Mono.empty();
        };
    }

    private static GatewayAuthProperties propertiesWith(String secret) {
        GatewayAuthProperties properties = new GatewayAuthProperties();
        properties.getJwt().setSecret(secret);
        properties.getAuth().setPublicPaths(List.of("/api/auth/login", "/actuator/health"));
        properties.getAuth().setAdminPaths(List.of("/api/admin/"));
        return properties;
    }

    /** A token with no role claim — what every token issued before this release looks like. */
    private static String tokenFor(String subject, Instant expiry) {
        return tokenFor(subject, expiry, null);
    }

    private static String tokenFor(String subject, Instant expiry, String role) {
        var builder = Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(Instant.now().minusSeconds(60)))
                .expiration(Date.from(expiry));
        if (role != null) {
            builder.claim("role", role);
        }
        return builder.signWith(KEY).compact();
    }

    private ServerWebExchange run(MockServerHttpRequest request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        filter.filter(exchange, chain).block();
        return exchange;
    }

    @Test
    @DisplayName("a valid token is accepted and the subject is forwarded as X-User-Id")
    void forwardsUserIdForValidToken() {
        run(MockServerHttpRequest.get("/api/transactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(USER_ID, Instant.now().plusSeconds(600)))
                .build());

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getHeaders().getFirst(JwtAuthenticationFilter.USER_ID_HEADER))
                .isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("a request with no Authorization header is rejected with 401")
    void rejectsMissingToken() {
        ServerWebExchange exchange = run(MockServerHttpRequest.get("/api/transactions").build());

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded.get()).isNull();
    }

    @Test
    @DisplayName("a token signed with a different key is rejected with 401")
    void rejectsForgedSignature() {
        String forged = Jwts.builder()
                .subject(USER_ID)
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor("a-completely-different-key-of-sufficient-length!!".getBytes(StandardCharsets.UTF_8)))
                .compact();

        ServerWebExchange exchange = run(MockServerHttpRequest.get("/api/transactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + forged)
                .build());

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded.get()).isNull();
    }

    @Test
    @DisplayName("an expired token is rejected with 401")
    void rejectsExpiredToken() {
        ServerWebExchange exchange = run(MockServerHttpRequest.get("/api/transactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(USER_ID, Instant.now().minusSeconds(30)))
                .build());

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded.get()).isNull();
    }

    @Test
    @DisplayName("a client-supplied X-User-Id is stripped and replaced by the token subject")
    void stripsSpoofedUserIdHeader() {
        run(MockServerHttpRequest.get("/api/transactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(USER_ID, Instant.now().plusSeconds(600)))
                .header(JwtAuthenticationFilter.USER_ID_HEADER, "attacker-supplied-id")
                .build());

        assertThat(forwarded.get().getHeaders().get(JwtAuthenticationFilter.USER_ID_HEADER))
                .containsExactly(USER_ID);
    }

    @Test
    @DisplayName("a client-supplied X-User-Id is stripped on public paths too")
    void stripsSpoofedUserIdHeaderOnPublicPaths() {
        run(MockServerHttpRequest.post("/api/auth/login")
                .header(JwtAuthenticationFilter.USER_ID_HEADER, "attacker-supplied-id")
                .build());

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getHeaders().get(JwtAuthenticationFilter.USER_ID_HEADER)).isNull();
    }

    @Test
    @DisplayName("public paths pass through without a token")
    void allowsPublicPaths() {
        ServerWebExchange exchange = run(MockServerHttpRequest.post("/api/auth/login").build());

        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(forwarded.get()).isNotNull();
    }

    @Test
    @DisplayName("CORS preflight is allowed through without a token")
    void allowsPreflight() {
        run(MockServerHttpRequest.options("/api/transactions").build());

        assertThat(forwarded.get()).isNotNull();
    }

    @Test
    @DisplayName("startup fails fast when JWT_SECRET is too short to sign HS256")
    void rejectsWeakSecretAtStartup() {
        assertThatThrownBy(() -> new JwtAuthenticationFilter(propertiesWith("too-short")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    // ------------------------------------------------------------------ roles

    @Test
    @DisplayName("the role claim is forwarded as X-User-Role")
    void forwardsRole() {
        run(MockServerHttpRequest.get("/api/transactions")
                .header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + tokenFor(USER_ID, Instant.now().plusSeconds(600), "ADMIN"))
                .build());

        assertThat(forwarded.get().getHeaders().getFirst(JwtAuthenticationFilter.USER_ROLE_HEADER))
                .isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("a token with no role claim is treated as USER, not rejected")
    void defaultsMissingRoleToUser() {
        // Tokens issued before the role claim existed are still valid for their 24h lifetime.
        // They must keep working, and must never be read as anything but the least privilege.
        run(MockServerHttpRequest.get("/api/transactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(USER_ID, Instant.now().plusSeconds(600)))
                .build());

        assertThat(forwarded.get().getHeaders().getFirst(JwtAuthenticationFilter.USER_ROLE_HEADER))
                .isEqualTo("USER");
    }

    @Test
    @DisplayName("an admin token reaches an admin path")
    void allowsAdminOnAdminPath() {
        ServerWebExchange exchange = run(MockServerHttpRequest.get("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + tokenFor(USER_ID, Instant.now().plusSeconds(600), "ADMIN"))
                .build());

        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(forwarded.get()).isNotNull();
    }

    @Test
    @DisplayName("a non-admin on an admin path is refused with 403, not 401")
    void refusesNonAdminOnAdminPath() {
        // 401 would send the frontend to the sign-in page, which cannot fix anything: the token
        // is perfectly valid, it simply does not carry the authority.
        ServerWebExchange exchange = run(MockServerHttpRequest.get("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + tokenFor(USER_ID, Instant.now().plusSeconds(600), "USER"))
                .build());

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forwarded.get()).isNull();
    }

    @Test
    @DisplayName("a spoofed X-User-Role header cannot grant admin access")
    void spoofedRoleHeaderDoesNotGrantAdmin() {
        // The whole authorisation model rests on this: the header is trusted downstream, so it
        // has to be impossible to supply.
        ServerWebExchange exchange = run(MockServerHttpRequest.get("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + tokenFor(USER_ID, Instant.now().plusSeconds(600), "USER"))
                .header(JwtAuthenticationFilter.USER_ROLE_HEADER, "ADMIN")
                .build());

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forwarded.get()).isNull();
    }

    @Test
    @DisplayName("a client-supplied X-User-Role is stripped on public paths too")
    void stripsSpoofedRoleHeaderOnPublicPaths() {
        run(MockServerHttpRequest.post("/api/auth/login")
                .header(JwtAuthenticationFilter.USER_ROLE_HEADER, "ADMIN")
                .build());

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getHeaders().get(JwtAuthenticationFilter.USER_ROLE_HEADER)).isNull();
    }

    @Test
    @DisplayName("an admin path still requires a token at all")
    void adminPathStillRequiresAToken() {
        ServerWebExchange exchange = run(MockServerHttpRequest.get("/api/admin/users").build());

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded.get()).isNull();
    }

    // ------------------------------------------------------------------ public path matching

    @Test
    @DisplayName("public paths are matched exactly, so a longer path sharing the prefix is not public")
    void publicPathsAreNotPrefixMatched() {
        // Under the old startsWith matching, "/api/auth/login-as-anyone" would have been public.
        ServerWebExchange exchange = run(MockServerHttpRequest.post("/api/auth/loginXYZ").build());

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded.get()).isNull();
    }
}
