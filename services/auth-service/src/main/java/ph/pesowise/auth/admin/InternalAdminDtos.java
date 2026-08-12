package ph.pesowise.auth.admin;

import jakarta.validation.constraints.AssertTrue;
import ph.pesowise.auth.user.User;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Payloads for the internal, gateway-unreachable admin surface — see {@link InternalAdminController}. */
public final class InternalAdminDtos {

    private InternalAdminDtos() {
    }

    public record UserSummary(String id, String email, String displayName, String role,
                              boolean emailVerified, boolean disabled, Instant createdAt) {
        public static UserSummary from(User user) {
            return new UserSummary(
                    user.getId().toString(), user.getEmail(), user.getDisplayName(),
                    user.getRole().name(), user.isEmailVerified(), user.isDisabled(), user.getCreatedAt());
        }
    }

    public record UserPage(List<UserSummary> items, int page, int size, long totalItems, int totalPages) {
    }

    /**
     * Both fields optional so a caller can flip one without re-sending the other. At least one
     * must be present, or the request has nothing to do.
     */
    public record UpdateUserRequest(String role, Boolean disabled) {
        @AssertTrue(message = "Provide a role, a disabled flag, or both.")
        public boolean hasSomethingToChange() {
            return role != null || disabled != null;
        }
    }

    public record SignupDay(LocalDate date, long count) {
    }

    public record UserStats(long totalUsers, long verifiedUsers, long disabledUsers, long adminUsers,
                            List<SignupDay> signupsLast30Days) {
    }
}
