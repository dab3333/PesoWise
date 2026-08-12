package ph.pesowise.admin.clients;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Mirrors auth-service's {@code InternalAdminDtos} — the shape of {@code /internal/admin/**}. */
public final class AuthAdminDtos {

    private AuthAdminDtos() {
    }

    public record UserSummary(String id, String email, String displayName, String role,
                              boolean emailVerified, boolean disabled, Instant createdAt) {
    }

    public record UserPage(List<UserSummary> items, int page, int size, long totalItems, int totalPages) {
    }

    public record UpdateUserRequest(String role, Boolean disabled) {
    }

    public record SignupDay(LocalDate date, long count) {
    }

    public record UserStats(long totalUsers, long verifiedUsers, long disabledUsers, long adminUsers,
                            List<SignupDay> signupsLast30Days) {
    }
}
