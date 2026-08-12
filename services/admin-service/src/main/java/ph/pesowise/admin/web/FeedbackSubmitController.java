package ph.pesowise.admin.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ph.pesowise.admin.api.FeedbackDtos.FeedbackResponse;
import ph.pesowise.admin.api.FeedbackDtos.SubmitRequest;
import ph.pesowise.admin.service.FeedbackService;

import java.util.UUID;

/**
 * Open to any signed-in user — deliberately outside {@code /api/admin/**}, so it needs its own
 * gateway route rather than inheriting one from the admin prefix. See {@link AdminFeedbackController}
 * for the admin-only half of this feature.
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackSubmitController {

    private final FeedbackService feedbackService;

    public FeedbackSubmitController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackResponse submit(
            @RequestHeader(Headers.USER_ID) UUID userId, @Valid @RequestBody SubmitRequest request) {
        return feedbackService.submit(userId, request);
    }
}
