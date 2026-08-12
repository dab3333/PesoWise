package ph.pesowise.admin.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.pesowise.admin.api.FeedbackDtos.FeedbackCounts;
import ph.pesowise.admin.api.FeedbackDtos.FeedbackPage;
import ph.pesowise.admin.api.FeedbackDtos.FeedbackResponse;
import ph.pesowise.admin.api.FeedbackDtos.SubmitRequest;
import ph.pesowise.admin.api.FeedbackDtos.UpdateStatusRequest;
import ph.pesowise.admin.domain.AdminAuditEntry;
import ph.pesowise.admin.domain.Feedback;
import ph.pesowise.admin.repo.AdminAuditRepository;
import ph.pesowise.admin.repo.FeedbackRepository;
import ph.pesowise.admin.web.BadRequestException;
import ph.pesowise.admin.web.NotFoundException;

import java.util.Locale;
import java.util.UUID;

@Service
public class FeedbackService {

    private final FeedbackRepository feedback;
    private final AdminAuditRepository audit;

    public FeedbackService(FeedbackRepository feedback, AdminAuditRepository audit) {
        this.feedback = feedback;
        this.audit = audit;
    }

    @Transactional
    public FeedbackResponse submit(UUID userId, SubmitRequest request) {
        Feedback.Category category = parseCategory(request.category());
        Feedback saved = feedback.save(Feedback.submit(
                userId, request.userEmail().trim(), request.userName().trim(),
                category, request.subject().trim(), request.message().trim()));
        return FeedbackResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public FeedbackPage list(String status, int page, int size) {
        Feedback.Status parsed = status == null || status.isBlank() ? null : parseStatus(status);
        var result = feedback.search(parsed, PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return new FeedbackPage(
                result.getContent().stream().map(FeedbackResponse::from).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public FeedbackCounts counts() {
        return new FeedbackCounts(
                feedback.countByStatus(Feedback.Status.NEW),
                feedback.countByStatus(Feedback.Status.REVIEWING),
                feedback.countByStatus(Feedback.Status.RESOLVED));
    }

    @Transactional
    public FeedbackResponse updateStatus(UUID actorUserId, UUID id, UpdateStatusRequest request) {
        Feedback entry = feedback.findById(id)
                .orElseThrow(() -> new NotFoundException("That feedback item was not found."));

        Feedback.Status status = parseStatus(request.status());
        entry.changeStatus(status, blankToNull(request.adminNote()));

        audit.save(AdminAuditEntry.record(
                actorUserId, "FEEDBACK_STATUS_CHANGED", "feedback", id, "-> " + status));

        return FeedbackResponse.from(entry);
    }

    private static Feedback.Category parseCategory(String raw) {
        try {
            return Feedback.Category.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Category must be BUG, IDEA, or OTHER.");
        }
    }

    private static Feedback.Status parseStatus(String raw) {
        try {
            return Feedback.Status.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Status must be NEW, REVIEWING, or RESOLVED.");
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
