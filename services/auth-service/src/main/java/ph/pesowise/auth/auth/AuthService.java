package ph.pesowise.auth.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.pesowise.auth.api.AuthDtos.AuthResponse;
import ph.pesowise.auth.api.AuthDtos.LoginRequest;
import ph.pesowise.auth.api.AuthDtos.RegisterRequest;
import ph.pesowise.auth.api.AuthDtos.RegistrationResponse;
import ph.pesowise.auth.api.AuthDtos.ResetPasswordRequest;
import ph.pesowise.auth.api.AuthDtos.UserResponse;
import ph.pesowise.auth.config.AdminProperties;
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
import ph.pesowise.auth.web.UserNotFoundException;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final EmailVerificationTokenRepository verificationTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;
    private final AccountMailer mailer;
    private final MailProperties mailProperties;
    private final AdminProperties adminProperties;

    public AuthService(UserRepository users,
                       EmailVerificationTokenRepository verificationTokens,
                       PasswordResetTokenRepository resetTokens,
                       PasswordEncoder passwordEncoder,
                       JwtIssuer jwtIssuer,
                       AccountMailer mailer,
                       MailProperties mailProperties,
                       AdminProperties adminProperties) {
        this.users = users;
        this.verificationTokens = verificationTokens;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
        this.mailer = mailer;
        this.mailProperties = mailProperties;
        this.adminProperties = adminProperties;
    }

    /**
     * Creates the account and mails a confirmation link. Returns no token: an address that has
     * not been proven reachable does not get a session.
     */
    @Transactional
    public RegistrationResponse register(RegisterRequest request) {
        String email = normalise(request.email());

        // Checked up front for a clean 409, but the unique index below is the real guard —
        // two concurrent registrations would both pass this check.
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        // By default this follows mail delivery: with no way to send a link, requiring one would
        // make the account permanently unreachable. It can be switched on independently so the
        // flow can be walked through against logged links, with no SMTP account.
        boolean autoVerify = !mailProperties.isVerificationRequired();
        User.Role role = adminProperties.normalisedEmails().contains(email) ? User.Role.ADMIN : User.Role.USER;

        User user = User.create(
                email, passwordEncoder.encode(request.password()),
                request.firstName().trim(), request.lastName().trim(),
                request.age(), request.gender(), request.occupation(),
                request.occupation() == User.Occupation.OTHER
                        ? trimToNull(request.occupationOther()) : null,
                role, autoVerify);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyRegisteredException();
        }

        if (role == User.Role.ADMIN) {
            log.info("Registered {} as ADMIN (listed in pesowise.admin.emails)", email);
        }

        if (autoVerify) {
            return new RegistrationResponse(email, true,
                    "Account created. Mail delivery is off, so you can sign in right away.");
        }

        issueVerification(user);
        return new RegistrationResponse(email, false,
                "Account created. Check your email for a confirmation link.");
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(normalise(request.email()))
                .orElse(null);

        // Hash a throwaway value when the user is absent so a missing account and a wrong
        // password take comparable time — otherwise response latency reveals which emails
        // are registered.
        if (user == null) {
            passwordEncoder.encode(request.password());
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        // Both checks come after the password, so neither leaks anything to someone who cannot
        // already sign in.
        if (user.isDisabled()) {
            throw new AccountDisabledException();
        }
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }

        return tokenResponse(user);
    }

    /** Redeems a confirmation link. */
    @Transactional
    public void verifyEmail(String rawToken) {
        EmailVerificationToken token = verificationTokens.findByTokenHash(TokenValues.hash(rawToken))
                .filter(candidate -> candidate.isRedeemable(Instant.now()))
                .orElseThrow(InvalidTokenException::verification);

        User user = users.findById(token.getUserId())
                .orElseThrow(InvalidTokenException::verification);

        token.redeem();
        user.markEmailVerified();
        log.info("Verified {}", user.getEmail());
    }

    /**
     * Sends another confirmation link.
     *
     * <p>Silent in every branch — unknown address, already verified, still inside the cooldown —
     * because the response is visible to anyone and must not become an oracle for which addresses
     * are registered.
     */
    @Transactional
    public void resendVerification(String rawEmail) {
        Optional<User> found = users.findByEmail(normalise(rawEmail));
        if (found.isEmpty()) {
            return;
        }

        User user = found.get();
        if (user.isEmailVerified() || user.isDisabled()) {
            return;
        }

        Instant cooldownStart = Instant.now().minusSeconds(mailProperties.getResendCooldownSeconds());
        boolean tooSoon = verificationTokens.findFirstByUserIdOrderByCreatedAtDesc(user.getId())
                .map(latest -> latest.getCreatedAt().isAfter(cooldownStart))
                .orElse(false);
        if (tooSoon) {
            log.debug("Suppressed a verification resend for {} — inside the cooldown", user.getEmail());
            return;
        }

        issueVerification(user);
    }

    /**
     * Starts a password reset.
     *
     * <p>Returns normally whether or not the address exists. "No account with that email" is the
     * single most useful sentence you can give someone enumerating a user list, and the honest
     * version costs a real user nothing: if no mail arrives, they mistyped it.
     */
    @Transactional
    public void forgotPassword(String rawEmail) {
        Optional<User> found = users.findByEmail(normalise(rawEmail));
        if (found.isEmpty()) {
            log.debug("Password reset requested for an address with no account");
            return;
        }

        User user = found.get();
        if (user.isDisabled()) {
            return;
        }

        Instant cooldownStart = Instant.now().minusSeconds(mailProperties.getResendCooldownSeconds());
        boolean tooSoon = resetTokens.findFirstByUserIdOrderByCreatedAtDesc(user.getId())
                .map(latest -> latest.getCreatedAt().isAfter(cooldownStart))
                .orElse(false);
        if (tooSoon) {
            return;
        }

        String raw = TokenValues.mint();
        resetTokens.save(PasswordResetToken.issue(
                user.getId(),
                TokenValues.hash(raw),
                Instant.now().plus(Duration.ofMinutes(mailProperties.getResetExpiryMinutes()))));

        mailer.sendPasswordReset(user.getEmail(), user.getDisplayName(), raw);
    }

    /** Redeems a reset link and sets the new password. */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = resetTokens.findByTokenHash(TokenValues.hash(request.token()))
                .filter(candidate -> candidate.isRedeemable(Instant.now()))
                .orElseThrow(InvalidTokenException::reset);

        User user = users.findById(token.getUserId())
                .orElseThrow(InvalidTokenException::reset);
        if (user.isDisabled()) {
            throw new AccountDisabledException();
        }

        user.changePassword(passwordEncoder.encode(request.password()));

        // Spends every outstanding link, not just this one. Someone who requested three resets
        // must not leave two working keys behind for whoever else can read that mailbox.
        resetTokens.consumeAllForUser(user.getId(), Instant.now());

        // Completing a reset proves the address is reachable, which is exactly what verification
        // asks for. Refusing to sign them in now would be asking for the same proof twice.
        user.markEmailVerified();

        log.info("Password reset for {}", user.getEmail());
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(UUID userId) {
        return users.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(UserNotFoundException::new);
    }

    private void issueVerification(User user) {
        String raw = TokenValues.mint();
        verificationTokens.save(EmailVerificationToken.issue(
                user.getId(),
                TokenValues.hash(raw),
                Instant.now().plus(Duration.ofMinutes(mailProperties.getVerificationExpiryMinutes()))));

        mailer.sendVerification(user.getEmail(), user.getDisplayName(), raw);
    }

    private AuthResponse tokenResponse(User user) {
        return new AuthResponse(jwtIssuer.issue(user), jwtIssuer.expiresInSeconds(), UserResponse.from(user));
    }

    /** Emails are stored lowercased so the unique index actually prevents duplicates. */
    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
