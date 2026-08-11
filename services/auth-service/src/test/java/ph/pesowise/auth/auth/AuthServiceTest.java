package ph.pesowise.auth.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import ph.pesowise.auth.api.AuthDtos.AuthResponse;
import ph.pesowise.auth.api.AuthDtos.LoginRequest;
import ph.pesowise.auth.api.AuthDtos.RegisterRequest;
import ph.pesowise.auth.config.JwtProperties;
import ph.pesowise.auth.user.User;
import ph.pesowise.auth.user.UserRepository;
import ph.pesowise.auth.web.EmailAlreadyRegisteredException;
import ph.pesowise.auth.web.InvalidCredentialsException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * Exercises AuthService against a mocked repository backed by a map, with a real BCrypt
 * encoder — password handling is the point of this class, so a mocked encoder would test
 * nothing.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository users;

    /** Stands in for the users table, keyed by the stored (normalised) email. */
    private Map<String, User> table;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        table = new HashMap<>();

        lenient().when(users.existsByEmail(anyString()))
                .thenAnswer(call -> table.containsKey(call.getArgument(0, String.class)));
        lenient().when(users.findByEmail(anyString()))
                .thenAnswer(call -> Optional.ofNullable(table.get(call.getArgument(0, String.class))));
        lenient().when(users.saveAndFlush(any(User.class))).thenAnswer(call -> {
            User user = call.getArgument(0, User.class);
            table.put(user.getEmail(), user);
            return user;
        });

        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-secret-that-is-definitely-long-enough-for-hs256");
        jwtProperties.setExpirationMs(3_600_000L);

        // Strength 4 keeps the suite fast; production strength is set in CryptoConfig.
        PasswordEncoder encoder = new BCryptPasswordEncoder(4);
        authService = new AuthService(users, encoder, new JwtIssuer(jwtProperties));
    }

    @Test
    @DisplayName("registration stores the user and returns a token")
    void registersUser() {
        AuthResponse response = authService.register(
                new RegisterRequest("maria@example.com", "sikreto123", "  Maria  "));

        assertThat(response.token()).isNotBlank();
        assertThat(response.expiresInSeconds()).isEqualTo(3600);
        assertThat(response.user().displayName()).isEqualTo("Maria");
        assertThat(table).containsOnlyKeys("maria@example.com");
    }

    @Test
    @DisplayName("email is normalised to lowercase so casing cannot create a duplicate account")
    void normalisesEmail() {
        authService.register(new RegisterRequest("Maria@Example.COM", "sikreto123", "Maria"));

        assertThat(table).containsOnlyKeys("maria@example.com");
        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("MARIA@example.com", "sikreto123", "Impostor")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    @DisplayName("the password is never stored in plain text")
    void hashesPassword() {
        authService.register(new RegisterRequest("juan@example.com", "sikreto123", "Juan"));

        String hash = table.get("juan@example.com").getPasswordHash();
        assertThat(hash).isNotEqualTo("sikreto123").startsWith("$2");
    }

    @Test
    @DisplayName("the JWT subject is the user id, not the email")
    void tokenSubjectIsUserId() {
        AuthResponse response = authService.register(
                new RegisterRequest("juan@example.com", "sikreto123", "Juan"));

        assertThat(response.user().id()).isEqualTo(table.get("juan@example.com").getId().toString());
    }

    @Test
    @DisplayName("login succeeds with the correct password")
    void loginSucceeds() {
        authService.register(new RegisterRequest("juan@example.com", "sikreto123", "Juan"));

        AuthResponse response = authService.login(new LoginRequest("juan@example.com", "sikreto123"));

        assertThat(response.token()).isNotBlank();
        assertThat(response.user().email()).isEqualTo("juan@example.com");
    }

    @Test
    @DisplayName("login is case-insensitive on the email")
    void loginIgnoresEmailCase() {
        authService.register(new RegisterRequest("juan@example.com", "sikreto123", "Juan"));

        assertThat(authService.login(new LoginRequest("JUAN@Example.com", "sikreto123")).token())
                .isNotBlank();
    }

    @Test
    @DisplayName("login with the wrong password is rejected")
    void loginRejectsWrongPassword() {
        authService.register(new RegisterRequest("juan@example.com", "sikreto123", "Juan"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("juan@example.com", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("login for an unknown email gives the same error as a wrong password")
    void loginDoesNotRevealWhetherEmailExists() {
        authService.register(new RegisterRequest("juan@example.com", "sikreto123", "Juan"));

        String unknownEmail = messageFrom(() -> authService.login(new LoginRequest("nobody@example.com", "x")));
        String wrongPassword = messageFrom(() -> authService.login(new LoginRequest("juan@example.com", "x")));

        assertThat(unknownEmail).isEqualTo(wrongPassword);
    }

    private static String messageFrom(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected InvalidCredentialsException");
        } catch (InvalidCredentialsException e) {
            return e.getMessage();
        }
    }
}
