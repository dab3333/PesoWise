package ph.pesowise.auth.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ph.pesowise.auth.api.AuthDtos.AuthResponse;
import ph.pesowise.auth.api.AuthDtos.ForgotPasswordRequest;
import ph.pesowise.auth.api.AuthDtos.LoginRequest;
import ph.pesowise.auth.api.AuthDtos.RegisterRequest;
import ph.pesowise.auth.api.AuthDtos.RegistrationResponse;
import ph.pesowise.auth.api.AuthDtos.ResendVerificationRequest;
import ph.pesowise.auth.api.AuthDtos.ResetPasswordRequest;
import ph.pesowise.auth.api.AuthDtos.UserResponse;
import ph.pesowise.auth.api.AuthDtos.VerifyEmailRequest;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Injected by the gateway after it verifies the token. Never trust a client's copy. */
    static final String USER_ID_HEADER = "X-User-Id";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Returns no token — the account is unusable until the emailed link is followed. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
    }

    // The three endpoints below always return 204, whatever the outcome. Each takes an email
    // address from an unauthenticated caller, so any observable difference between "that account
    // exists" and "it does not" would turn them into a user-enumeration tool.
    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.email());
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
    }

    @GetMapping("/me")
    public UserResponse me(@RequestHeader(USER_ID_HEADER) UUID userId) {
        return authService.currentUser(userId);
    }
}
