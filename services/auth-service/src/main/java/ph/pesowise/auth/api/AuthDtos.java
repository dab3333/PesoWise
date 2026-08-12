package ph.pesowise.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
            @NotBlank @Size(max = 60) String firstName,
            @NotBlank @Size(max = 60) String lastName,
            @NotNull @Min(1) @Max(120) Integer age,
            @NotNull User.Gender gender,
            @NotNull User.Occupation occupation,
            // Only meaningful (and required, enforced client-side) alongside OTHER; the backend
            // accepts it blank rather than duplicating that check with a field name Spring's
            // validation would report under the wrong key.
            @Size(max = 100) String occupationOther
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

    public record UserResponse(String id, String email, String displayName, String firstName,
                               String lastName, Integer age, String gender, String occupation,
                               String occupationOther, String role, boolean emailVerified,
                               Instant createdAt) {
        public static UserResponse from(User user) {
            return new UserResponse(
                    user.getId().toString(), user.getEmail(), user.getDisplayName(),
                    user.getFirstName(), user.getLastName(), user.getAge(),
                    user.getGender() != null ? user.getGender().name() : null,
                    user.getOccupation() != null ? user.getOccupation().name() : null,
                    user.getOccupationOther(),
                    user.getRole().name(), user.isEmailVerified(), user.getCreatedAt());
        }
    }
}
