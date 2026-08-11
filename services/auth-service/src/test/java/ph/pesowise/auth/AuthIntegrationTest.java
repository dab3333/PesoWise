package ph.pesowise.auth;

import com.jayway.jsonpath.JsonPath;
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
import ph.pesowise.auth.user.UserRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end over a real Postgres: proves the Flyway migration applies, the entity mapping
 * matches the migrated schema ({@code ddl-auto: validate} fails startup otherwise), and the
 * HTTP contract behaves.
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
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("pesowise.jwt.secret", () -> "integration-test-secret-long-enough-for-hs256");
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
    @DisplayName("POST /api/auth/register returns 201 with a token and persists the user")
    void registerPersistsUser() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("register@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("register@example.com"))
                // The hash must never appear in a response body.
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());

        assertThat(users.existsByEmail("register@example.com")).isTrue();
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
    @DisplayName("login returns a token, and a wrong password returns 401")
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
                .andExpect(jsonPath("$.token").isNotEmpty());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"login@example.com","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/auth/me resolves the user from the gateway-supplied X-User-Id")
    void meResolvesUserFromHeader() throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("me@example.com")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String userId = JsonPath.read(response, "$.user.id");

        mockMvc.perform(get("/api/auth/me").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"));
    }

    @Test
    @DisplayName("GET /api/auth/me returns 404 for a token whose user no longer exists")
    void meReturns404ForUnknownUser() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }
}
