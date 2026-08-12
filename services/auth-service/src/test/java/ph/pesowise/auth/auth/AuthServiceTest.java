package ph.pesowise.auth.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import ph.pesowise.auth.api.AuthDtos.AuthResponse;
import ph.pesowise.auth.api.AuthDtos.ForgotPasswordRequest;
import ph.pesowise.auth.api.AuthDtos.LoginRequest;
import ph.pesowise.auth.api.AuthDtos.RegisterRequest;
import ph.pesowise.auth.api.AuthDtos.RegistrationResponse;
import ph.pesowise.auth.api.AuthDtos.ResetPasswordRequest;
import ph.pesowise.auth.config.AdminProperties;
import ph.pesowise.auth.config.JwtProperties;
import ph.pesowise.auth.mail.AccountMailer;
import ph.pesowise.auth.mail.MailProperties;
import ph.pesowise.auth.token.EmailVerificationToken;
import ph.pesowise.auth.token.EmailVerificationTokenRepository;
import ph.pesowise.auth.token.PasswordResetToken;
import ph.pesowise.auth.token.PasswordResetTokenRepository;
import ph.pesowise.auth.token.TokenValues;
import ph.pesowise.auth.user.User;
import ph.pesowise.auth.user.UserRepository;
import ph.pesowise.auth.web.AccountDisabledException;
import ph.pesowise.auth.web.EmailAlreadyRegisteredException;
import ph.pesowise.auth.web.EmailNotVerifiedException;
import ph.pesowise.auth.web.InvalidCredentialsException;
import ph.pesowise.auth.web.InvalidTokenException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Exercises AuthService against mocked repositories backed by collections, with a real BCrypt
 * encoder — password handling is the point of this class, so a mocked encoder would test nothing.
 *
 * <p>Mail delivery is mocked so the raw token can be captured: it is generated inside the service
 * and, by design, is never persisted anywhere, so the argument passed to the mailer is the only
 * place a test can observe it. That is the same position a real user is in.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository users;
    @Mock
    private EmailVerificationTokenRepository verificationTokens;
    @Mock
    private PasswordResetTokenRepository resetTokens;
    @Mock
    private AccountMailer mailer;

    /** Stands in for the users table, keyed by the stored (normalised) email. */
    private Map<String, User> table;
    private List<EmailVerificationToken> verificationStore;
    private List<PasswordResetToken> resetStore;

    private MailProperties mailProperties;
    private AdminProperties adminProperties;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        table = new HashMap<>();
        verificationStore = new ArrayList<>();
        resetStore = new ArrayList<>();

        lenient().when(users.existsByEmail(anyString()))
                .thenAnswer(call -> table.containsKey(call.getArgument(0, String.class)));
        lenient().when(users.findByEmail(anyString()))
                .thenAnswer(call -> Optional.ofNullable(table.get(call.getArgument(0, String.class))));
        lenient().when(users.findById(any(UUID.class))).thenAnswer(call -> {
            UUID id = call.getArgument(0, UUID.class);
            return table.values().stream().filter(u -> u.getId().equals(id)).findFirst();
        });
        lenient().when(users.saveAndFlush(any(User.class))).thenAnswer(call -> {
            User user = call.getArgument(0, User.class);
            table.put(user.getEmail(), user);
            return user;
        });

        lenient().when(verificationTokens.save(any(EmailVerificationToken.class))).thenAnswer(call -> {
            EmailVerificationToken token = call.getArgument(0, EmailVerificationToken.class);
            verificationStore.add(token);
            return token;
        });
        lenient().when(verificationTokens.findByTokenHash(anyString())).thenAnswer(call -> {
            String hash = call.getArgument(0, String.class);
            return verificationStore.stream().filter(t -> t.getTokenHash().equals(hash)).findFirst();
        });
        lenient().when(verificationTokens.findFirstByUserIdOrderByCreatedAtDesc(any(UUID.class)))
                .thenAnswer(call -> {
                    UUID userId = call.getArgument(0, UUID.class);
                    return verificationStore.stream()
                            .filter(t -> t.getUserId().equals(userId))
                            .reduce((first, second) -> second);
                });

        lenient().when(resetTokens.save(any(PasswordResetToken.class))).thenAnswer(call -> {
            PasswordResetToken token = call.getArgument(0, PasswordResetToken.class);
            resetStore.add(token);
            return token;
        });
        lenient().when(resetTokens.findByTokenHash(anyString())).thenAnswer(call -> {
            String hash = call.getArgument(0, String.class);
            return resetStore.stream().filter(t -> t.getTokenHash().equals(hash)).findFirst();
        });
        lenient().when(resetTokens.findFirstByUserIdOrderByCreatedAtDesc(any(UUID.class)))
                .thenAnswer(call -> {
                    UUID userId = call.getArgument(0, UUID.class);
                    return resetStore.stream()
                            .filter(t -> t.getUserId().equals(userId))
                            .reduce((first, second) -> second);
                });
        lenient().when(resetTokens.consumeAllForUser(any(UUID.class), any(Instant.class)))
                .thenAnswer(call -> {
                    UUID userId = call.getArgument(0, UUID.class);
                    int consumed = 0;
                    for (PasswordResetToken token : resetStore) {
                        if (token.getUserId().equals(userId) && token.getUsedAt() == null) {
                            token.redeem();
                            consumed++;
                        }
                    }
                    return consumed;
                });

        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-secret-that-is-definitely-long-enough-for-hs256");
        jwtProperties.setExpirationMs(3_600_000L);

        mailProperties = new MailProperties();
        // On by default in tests: the interesting behaviour is the flow that requires
        // confirmation. The auto-verify shortcut is covered explicitly where it matters.
        mailProperties.setEnabled(true);
        // Cooldown off unless a test sets it, so back-to-back requests are not silently dropped.
        mailProperties.setResendCooldownSeconds(0);

        adminProperties = new AdminProperties();

        // Strength 4 keeps the suite fast; production strength is set in CryptoConfig.
        PasswordEncoder encoder = new BCryptPasswordEncoder(4);
        authService = new AuthService(users, verificationTokens, resetTokens, encoder,
                new JwtIssuer(jwtProperties), mailer, mailProperties, adminProperties);
    }

    /**
     * {@code name} becomes the first name with an empty last name, so {@code getDisplayName()}
     * still comes out as exactly {@code name} (the trailing space collapses under trim) — every
     * existing call site and assertion in this file stays unchanged. The profile fields beyond
     * the name are irrelevant here: this calls the service directly, bypassing the controller's
     * {@code @Valid}, so nothing in this file exercises their validation.
     */
    private static RegisterRequest regReq(String email, String password, String name) {
        return new RegisterRequest(email, password, name, "", 30, User.Gender.UNSPECIFIED,
                User.Occupation.OTHER, "n/a");
    }

    // ---------------------------------------------------------------- registration

    @Test
    @DisplayName("registration stores the user but issues no token until the email is confirmed")
    void registersWithoutSigningIn() {
        RegistrationResponse response = authService.register(
                regReq("maria@example.com", "sikreto123", "  Maria  "));

        assertThat(response.email()).isEqualTo("maria@example.com");
        assertThat(response.verified()).isFalse();
        assertThat(table).containsOnlyKeys("maria@example.com");
        assertThat(table.get("maria@example.com").getDisplayName()).isEqualTo("Maria");
        assertThat(table.get("maria@example.com").isEmailVerified()).isFalse();
        assertThat(verificationStore).hasSize(1);
    }

    @Test
    @DisplayName("with mail delivery off, registration self-verifies so the account stays reachable")
    void selfVerifiesWhenMailIsDisabled() {
        mailProperties.setEnabled(false);

        RegistrationResponse response = authService.register(
                regReq("dev@example.com", "sikreto123", "Dev"));

        assertThat(response.verified()).isTrue();
        assertThat(table.get("dev@example.com").isEmailVerified()).isTrue();
        assertThat(verificationStore).isEmpty();
        verify(mailer, never()).sendVerification(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("email is normalised to lowercase so casing cannot create a duplicate account")
    void normalisesEmail() {
        authService.register(regReq("Maria@Example.COM", "sikreto123", "Maria"));

        assertThat(table).containsOnlyKeys("maria@example.com");
        assertThatThrownBy(() -> authService.register(
                regReq("MARIA@example.com", "sikreto123", "Impostor")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    @DisplayName("the password is never stored in plain text")
    void hashesPassword() {
        authService.register(regReq("juan@example.com", "sikreto123", "Juan"));

        String hash = table.get("juan@example.com").getPasswordHash();
        assertThat(hash).isNotEqualTo("sikreto123").startsWith("$2");
    }

    @Test
    @DisplayName("a configured admin address registers straight into the ADMIN role")
    void promotesConfiguredAdminOnRegistration() {
        adminProperties.setEmails(List.of("Boss@Example.com"));

        authService.register(regReq("boss@example.com", "sikreto123", "Boss"));
        authService.register(regReq("someone@example.com", "sikreto123", "Someone"));

        assertThat(table.get("boss@example.com").getRole()).isEqualTo(User.Role.ADMIN);
        assertThat(table.get("someone@example.com").getRole()).isEqualTo(User.Role.USER);
    }

    // ---------------------------------------------------------------- login

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("succeeds once the address is confirmed")
        void succeedsAfterVerification() {
            registerAndVerify("juan@example.com", "sikreto123");

            AuthResponse response = authService.login(new LoginRequest("juan@example.com", "sikreto123"));

            assertThat(response.token()).isNotBlank();
            assertThat(response.expiresInSeconds()).isEqualTo(3600);
            assertThat(response.user().email()).isEqualTo("juan@example.com");
            assertThat(response.user().role()).isEqualTo("USER");
            assertThat(response.user().emailVerified()).isTrue();
        }

        @Test
        @DisplayName("is refused with the right password while the address is unconfirmed")
        void refusesUnverified() {
            authService.register(regReq("juan@example.com", "sikreto123", "Juan"));

            assertThatThrownBy(() -> authService.login(new LoginRequest("juan@example.com", "sikreto123")))
                    .isInstanceOf(EmailNotVerifiedException.class);
        }

        @Test
        @DisplayName("is refused for a disabled account")
        void refusesDisabled() {
            registerAndVerify("juan@example.com", "sikreto123");
            table.get("juan@example.com").setDisabled(true);

            assertThatThrownBy(() -> authService.login(new LoginRequest("juan@example.com", "sikreto123")))
                    .isInstanceOf(AccountDisabledException.class);
        }

        @Test
        @DisplayName("checks the password before reporting an unverified or disabled account")
        void doesNotLeakStateToAWrongPassword() {
            authService.register(regReq("juan@example.com", "sikreto123", "Juan"));

            // Unverified, but the password is wrong — the answer must be the generic one, or the
            // 403 becomes a way to confirm an address is registered without knowing the password.
            assertThatThrownBy(() -> authService.login(new LoginRequest("juan@example.com", "wrong")))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("is case-insensitive on the email")
        void ignoresEmailCase() {
            registerAndVerify("juan@example.com", "sikreto123");

            assertThat(authService.login(new LoginRequest("JUAN@Example.com", "sikreto123")).token())
                    .isNotBlank();
        }

        @Test
        @DisplayName("for an unknown email gives the same error as a wrong password")
        void doesNotRevealWhetherEmailExists() {
            registerAndVerify("juan@example.com", "sikreto123");

            String unknownEmail = messageFrom(() ->
                    authService.login(new LoginRequest("nobody@example.com", "x")));
            String wrongPassword = messageFrom(() ->
                    authService.login(new LoginRequest("juan@example.com", "x")));

            assertThat(unknownEmail).isEqualTo(wrongPassword);
        }
    }

    // ---------------------------------------------------------------- verification

    @Nested
    @DisplayName("email verification")
    class Verification {

        @Test
        @DisplayName("a valid link confirms the address")
        void confirmsAddress() {
            authService.register(regReq("juan@example.com", "sikreto123", "Juan"));

            authService.verifyEmail(captureVerificationToken());

            assertThat(table.get("juan@example.com").isEmailVerified()).isTrue();
        }

        @Test
        @DisplayName("the same link cannot be used twice")
        void rejectsReplay() {
            authService.register(regReq("juan@example.com", "sikreto123", "Juan"));
            String token = captureVerificationToken();
            authService.verifyEmail(token);

            assertThatThrownBy(() -> authService.verifyEmail(token))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        @DisplayName("an expired link is rejected")
        void rejectsExpired() {
            mailProperties.setVerificationExpiryMinutes(-1);
            authService.register(regReq("juan@example.com", "sikreto123", "Juan"));

            String token = captureVerificationToken();

            assertThatThrownBy(() -> authService.verifyEmail(token))
                    .isInstanceOf(InvalidTokenException.class);
            assertThat(table.get("juan@example.com").isEmailVerified()).isFalse();
        }

        @Test
        @DisplayName("an unknown token is rejected")
        void rejectsUnknown() {
            assertThatThrownBy(() -> authService.verifyEmail("not-a-real-token"))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        @DisplayName("the raw token is never stored — only its hash")
        void storesOnlyTheHash() {
            authService.register(regReq("juan@example.com", "sikreto123", "Juan"));
            String raw = captureVerificationToken();

            assertThat(verificationStore.getFirst().getTokenHash())
                    .isNotEqualTo(raw)
                    .isEqualTo(TokenValues.hash(raw))
                    .hasSize(64);
        }

        @Test
        @DisplayName("resending is silent for an unknown address")
        void resendIsSilentForUnknownAddress() {
            authService.resendVerification("nobody@example.com");

            assertThat(verificationStore).isEmpty();
            verify(mailer, never()).sendVerification(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("resending does nothing once the address is already confirmed")
        void resendIsSilentForVerifiedAccount() {
            registerAndVerify("juan@example.com", "sikreto123");
            int issued = verificationStore.size();

            authService.resendVerification("juan@example.com");

            assertThat(verificationStore).hasSize(issued);
        }

        @Test
        @DisplayName("resending is suppressed inside the cooldown")
        void resendRespectsCooldown() {
            mailProperties.setResendCooldownSeconds(3600);
            authService.register(regReq("juan@example.com", "sikreto123", "Juan"));

            authService.resendVerification("juan@example.com");

            assertThat(verificationStore).hasSize(1);
        }

        @Test
        @DisplayName("resending issues a second working link once the cooldown has passed")
        void resendIssuesANewLink() {
            authService.register(regReq("juan@example.com", "sikreto123", "Juan"));

            authService.resendVerification("juan@example.com");

            assertThat(verificationStore).hasSize(2);
            // Both are live: someone who clicks the older mail should not hit a dead link.
            authService.verifyEmail(captureVerificationToken(0));
            assertThat(table.get("juan@example.com").isEmailVerified()).isTrue();
        }
    }

    // ---------------------------------------------------------------- password reset

    @Nested
    @DisplayName("password reset")
    class Reset {

        @Test
        @DisplayName("a valid link sets the new password and retires the old one")
        void resetsPassword() {
            registerAndVerify("juan@example.com", "sikreto123");
            authService.forgotPassword(new ForgotPasswordRequest("juan@example.com").email());

            authService.resetPassword(new ResetPasswordRequest(captureResetToken(), "bagong-sikreto"));

            assertThat(authService.login(new LoginRequest("juan@example.com", "bagong-sikreto")).token())
                    .isNotBlank();
            assertThatThrownBy(() -> authService.login(new LoginRequest("juan@example.com", "sikreto123")))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("completing a reset also confirms the address")
        void resetVerifiesTheAddress() {
            // Someone who never clicked the confirmation link but can still read the mailbox has
            // proven exactly what verification asks for. Demanding it twice helps nobody.
            authService.register(regReq("juan@example.com", "sikreto123", "Juan"));
            authService.forgotPassword("juan@example.com");

            authService.resetPassword(new ResetPasswordRequest(captureResetToken(), "bagong-sikreto"));

            assertThat(table.get("juan@example.com").isEmailVerified()).isTrue();
        }

        @Test
        @DisplayName("the link cannot be replayed")
        void rejectsReplay() {
            registerAndVerify("juan@example.com", "sikreto123");
            authService.forgotPassword("juan@example.com");
            String token = captureResetToken();
            authService.resetPassword(new ResetPasswordRequest(token, "bagong-sikreto"));

            assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest(token, "iba-naman")))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        @DisplayName("using one link invalidates every other outstanding link for that account")
        void redeemingOneConsumesTheRest() {
            registerAndVerify("juan@example.com", "sikreto123");
            authService.forgotPassword("juan@example.com");
            authService.forgotPassword("juan@example.com");
            assertThat(resetStore).hasSize(2);

            authService.resetPassword(new ResetPasswordRequest(captureResetToken(1), "bagong-sikreto"));

            // The first link must be dead too — otherwise a stale mail is a live key to an
            // account whose password has since been changed.
            assertThatThrownBy(() ->
                    authService.resetPassword(new ResetPasswordRequest(captureResetToken(0), "iba-naman")))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        @DisplayName("an expired link is rejected")
        void rejectsExpired() {
            mailProperties.setResetExpiryMinutes(-1);
            registerAndVerify("juan@example.com", "sikreto123");
            authService.forgotPassword("juan@example.com");

            assertThatThrownBy(() ->
                    authService.resetPassword(new ResetPasswordRequest(captureResetToken(), "bagong-sikreto")))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        @DisplayName("requesting a reset for an unknown address is silent")
        void silentForUnknownAddress() {
            authService.forgotPassword("nobody@example.com");

            assertThat(resetStore).isEmpty();
            verify(mailer, never()).sendPasswordReset(anyString(), anyString(), anyString());
        }
    }

    // ---------------------------------------------------------------- helpers

    private void registerAndVerify(String email, String password) {
        authService.register(regReq(email, password, "Test User"));
        authService.verifyEmail(captureVerificationToken());
    }

    /** The most recent raw verification token handed to the mailer. */
    private String captureVerificationToken() {
        return captureVerificationToken(-1);
    }

    private String captureVerificationToken(int index) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mailer, org.mockito.Mockito.atLeastOnce())
                .sendVerification(anyString(), anyString(), captor.capture());
        List<String> all = captor.getAllValues();
        return index < 0 ? all.getLast() : all.get(index);
    }

    private String captureResetToken() {
        return captureResetToken(-1);
    }

    private String captureResetToken(int index) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mailer, org.mockito.Mockito.atLeastOnce())
                .sendPasswordReset(anyString(), anyString(), captor.capture());
        List<String> all = captor.getAllValues();
        return index < 0 ? all.getLast() : all.get(index);
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
