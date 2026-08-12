package ph.pesowise.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ph.pesowise.auth.user.User;

import java.time.Instant;

/** Request and response payloads for the auth endpoints. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            // 8 is the floor; the frontend nudges toward longer without blocking.
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 100) String displayName
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {
    }

    public record VerifyEmailRequest(@NotBlank String token) {
    }

    public record ResendVerificationRequest(@NotBlank @Email String email) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 72) String password
    ) {
    }

    /** The token plus the user, so the frontend needs only one request to sign in. */
    public record AuthResponse(String token, long expiresInSeconds, UserResponse user) {
    }

    /**
     * What registration returns now that an account is unusable until confirmed.
     *
     * <p>Deliberately not an {@link AuthResponse}: issuing a token here would sign in an account
     * whose address has not been proven, which is the entire thing verification exists to
     * prevent. {@code verified} is true only when mail delivery is switched off, so the frontend
     * can send a developer straight to the sign-in form instead of telling them to check an inbox
     * that will never receive anything.
     */
    public record RegistrationResponse(String email, boolean verified, String message) {
    }

    public record UserResponse(String id, String email, String displayName, String role,
                               boolean emailVerified, Instant createdAt) {
        public static UserResponse from(User user) {
            return new UserResponse(
                    user.getId().toString(), user.getEmail(), user.getDisplayName(),
                    user.getRole().name(), user.isEmailVerified(), user.getCreatedAt());
        }
    }
}
