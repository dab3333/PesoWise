package ph.pesowise.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ph.pesowise.auth.user.User;
import ph.pesowise.auth.user.UserRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end over a real Postgres: proves both Flyway migrations apply, the entity mapping
 * matches the migrated schema ({@code ddl-auto: validate} fails startup otherwise), and the
 * HTTP contract behaves.
 *
 * <p>Mail delivery is left off, which is the default. Registration therefore self-verifies, so
 * these tests exercise the sign-up and sign-in contract without needing an SMTP server. The
 * confirmation and reset flows themselves are covered by {@code AuthServiceTest}, where the
 * emailed token can be observed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
class AuthIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("pesowise.jwt.secret", () -> "integration-test-secret-long-enough-for-hs256");
        registry.add("pesowise.mail.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    private static String registerBody(String email) {
        return """
                {"email":"%s","password":"sikreto123","displayName":"Test User"}
                """.formatted(email);
    }

    @Test
    @DisplayName("POST /api/auth/register persists the user and returns no token")
    void registerPersistsUserWithoutIssuingAToken() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("register@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("register@example.com"))
                // Delivery is off, so the account self-verifies and can sign in immediately.
                .andExpect(jsonPath("$.verified").value(true))
                // The whole point of the change: registration must not hand out a session.
                .andExpect(jsonPath("$.token").doesNotExist());

        User stored = users.findByEmail("register@example.com").orElseThrow();
        assertThat(stored.getRole()).isEqualTo(User.Role.USER);
        assertThat(stored.isDisabled()).isFalse();
    }

    @Test
    @DisplayName("registering the same email twice returns 409")
    void duplicateEmailIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("dupe@example.com")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("dupe@example.com")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("an invalid email and a short password both fail validation with field errors")
    void validationRejectsBadInput() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"short","displayName":"X"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    @DisplayName("login returns a token carrying the role, and a wrong password returns 401")
    void loginRoundTrip() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("login@example.com")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"login@example.com","password":"sikreto123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.emailVerified").value(true))
                // The hash must never appear in a response body.
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"login@example.com","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("login is refused with 403 EMAIL_NOT_VERIFIED while the address is unconfirmed")
    void unverifiedLoginIsForbidden() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("unverified@example.com")))
                .andExpect(status().isCreated());

        // Undo the auto-verification that mail-disabled mode applied, to reach the state a real
        // deployment starts every account in.
        User user = users.findByEmail("unverified@example.com").orElseThrow();
        users.save(unverify(user));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"unverified@example.com","password":"sikreto123"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    @DisplayName("login is refused with 403 ACCOUNT_DISABLED for a disabled account")
    void disabledLoginIsForbidden() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("disabled@example.com")))
                .andExpect(status().isCreated());

        User user = users.findByEmail("disabled@example.com").orElseThrow();
        user.setDisabled(true);
        users.save(user);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"disabled@example.com","password":"sikreto123"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    @DisplayName("a bad verification token returns 400 rather than revealing why it failed")
    void badVerificationTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"nonsense"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("forgot-password answers 204 for an unknown address, so it cannot enumerate users")
    void forgotPasswordIsSilentForUnknownAddress() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nobody@example.com"}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET /api/auth/me resolves the user from the gateway-supplied X-User-Id")
    void meResolvesUserFromHeader() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("me@example.com")))
                .andExpect(status().isCreated());

        UUID userId = users.findByEmail("me@example.com").orElseThrow().getId();

        mockMvc.perform(get("/api/auth/me").header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    @DisplayName("GET /api/auth/me returns 404 for a token whose user no longer exists")
    void meReturns404ForUnknownUser() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    /**
     * The entity exposes no way to un-verify — verification is deliberately one-way — so this
     * reaches for the field directly rather than widening the domain model for a test.
     */
    private static User unverify(User user) {
        try {
            var field = User.class.getDeclaredField("emailVerified");
            field.setAccessible(true);
            field.setBoolean(user, false);
            return user;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("User.emailVerified moved or was renamed", e);
        }
    }
}
