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

    /** The token plus the user, so the frontend needs only one request to sign in. */
    public record AuthResponse(String token, long expiresInSeconds, UserResponse user) {
    }

    public record UserResponse(String id, String email, String displayName, Instant createdAt) {
        public static UserResponse from(User user) {
            return new UserResponse(
                    user.getId().toString(), user.getEmail(), user.getDisplayName(), user.getCreatedAt());
        }
    }
}
