package ph.pesowise.admin.web;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ph.pesowise.admin.api.FeedbackDtos.FeedbackPage;
import ph.pesowise.admin.api.FeedbackDtos.FeedbackResponse;
import ph.pesowise.admin.api.FeedbackDtos.UpdateStatusRequest;
import ph.pesowise.admin.service.FeedbackService;

import java.util.UUID;

/**
 * Admin-only. Under {@code /api/admin/**}, so the gateway already refuses any caller whose token
 * does not carry the ADMIN role — nothing here re-checks that.
 */
@RestController
@RequestMapping("/api/admin/feedback")
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    public AdminFeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping
    public FeedbackPage list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return feedbackService.list(status, page, Math.min(size, 100));
    }

    @PatchMapping("/{id}")
    public FeedbackResponse updateStatus(
            @RequestHeader(Headers.USER_ID) UUID actorUserId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request) {
        return feedbackService.updateStatus(actorUserId, id, request);
    }
}
