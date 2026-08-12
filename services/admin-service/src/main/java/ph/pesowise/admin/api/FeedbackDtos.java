package ph.pesowise.admin.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ph.pesowise.admin.domain.Feedback;

import java.time.Instant;

public final class FeedbackDtos {

    private FeedbackDtos() {
    }

    public record SubmitRequest(
            @NotBlank String category,
            // Denormalised from the client rather than looked up — see the migration comment on
            // feedback.user_email for why that is safe: user_id, the trustworthy part, comes
            // from the gateway-verified header, not from this request body.
            @NotBlank @Size(max = 320) String userEmail,
            @NotBlank @Size(max = 100) String userName,
            @NotBlank @Size(max = 150) String subject,
            @NotBlank @Size(max = 4000) String message) {
    }

    public record UpdateStatusRequest(@NotBlank String status, @Size(max = 4000) String adminNote) {
    }

    public record FeedbackResponse(
            String id, String userId, String userEmail, String userName, String category,
            String subject, String message, String status, String adminNote,
            Instant createdAt, Instant resolvedAt) {
        public static FeedbackResponse from(Feedback f) {
            return new FeedbackResponse(
                    f.getId().toString(), f.getUserId().toString(), f.getUserEmail(), f.getUserName(),
                    f.getCategory().name(), f.getSubject(), f.getMessage(), f.getStatus().name(),
                    f.getAdminNote(), f.getCreatedAt(), f.getResolvedAt());
        }
    }

    public record FeedbackPage(java.util.List<FeedbackResponse> items, int page, int size,
                               long totalItems, int totalPages) {
    }

    public record FeedbackCounts(long newCount, long reviewingCount, long resolvedCount) {
    }
}
