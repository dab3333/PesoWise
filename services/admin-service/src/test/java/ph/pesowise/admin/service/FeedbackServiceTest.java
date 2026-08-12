package ph.pesowise.admin.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import ph.pesowise.admin.api.FeedbackDtos.FeedbackResponse;
import ph.pesowise.admin.api.FeedbackDtos.SubmitRequest;
import ph.pesowise.admin.api.FeedbackDtos.UpdateStatusRequest;
import ph.pesowise.admin.domain.AdminAuditEntry;
import ph.pesowise.admin.domain.Feedback;
import ph.pesowise.admin.repo.AdminAuditRepository;
import ph.pesowise.admin.repo.FeedbackRepository;
import ph.pesowise.admin.web.BadRequestException;
import ph.pesowise.admin.web.NotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock
    private FeedbackRepository feedback;
    @Mock
    private AdminAuditRepository audit;

    private FeedbackService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FeedbackService(feedback, audit);
        lenient().when(feedback.save(any(Feedback.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    @DisplayName("submit stores the denormalised email and name from the request, not a lookup")
    void submitStoresDenormalisedFields() {
        FeedbackResponse response = service.submit(userId, new SubmitRequest(
                "BUG", " user@example.com ", " Maria ", " Charts break ", " The line chart is blank. "));

        // Trimmed, and category/subject/message pass through untouched aside from that.
        assertThat(response.userEmail()).isEqualTo("user@example.com");
        assertThat(response.userName()).isEqualTo("Maria");
        assertThat(response.category()).isEqualTo("BUG");
        assertThat(response.status()).isEqualTo("NEW");
    }

    @Test
    @DisplayName("an unrecognised category is rejected rather than silently stored")
    void rejectsUnknownCategory() {
        assertThatThrownBy(() -> service.submit(userId,
                new SubmitRequest("URGENT", "u@example.com", "Maria", "Subject", "Message")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("updateStatus changes status, sets the note, and records who did it")
    void updateStatusRecordsActor() {
        Feedback existing = Feedback.submit(userId, "u@example.com", "Maria",
                Feedback.Category.BUG, "Subject", "Message");
        UUID id = existing.getId();
        when(feedback.findById(id)).thenReturn(Optional.of(existing));

        FeedbackResponse response = service.updateStatus(
                adminId, id, new UpdateStatusRequest("RESOLVED", "Fixed in the next release."));

        assertThat(response.status()).isEqualTo("RESOLVED");
        assertThat(response.adminNote()).isEqualTo("Fixed in the next release.");
        assertThat(response.resolvedAt()).isNotNull();

        var captor = org.mockito.ArgumentCaptor.forClass(AdminAuditEntry.class);
        verify(audit).save(captor.capture());
        assertThat(captor.getValue().getActorUserId()).isEqualTo(adminId);
        assertThat(captor.getValue().getAction()).isEqualTo("FEEDBACK_STATUS_CHANGED");
        assertThat(captor.getValue().getTargetId()).isEqualTo(id);
    }

    @Test
    @DisplayName("moving away from RESOLVED clears the resolvedAt stamp")
    void reopeningClearsResolvedAt() {
        Feedback existing = Feedback.submit(userId, "u@example.com", "Maria",
                Feedback.Category.BUG, "Subject", "Message");
        existing.changeStatus(Feedback.Status.RESOLVED, "Done");
        UUID id = existing.getId();
        when(feedback.findById(id)).thenReturn(Optional.of(existing));

        FeedbackResponse response = service.updateStatus(
                adminId, id, new UpdateStatusRequest("REVIEWING", null));

        assertThat(response.status()).isEqualTo("REVIEWING");
        assertThat(response.resolvedAt()).isNull();
    }

    @Test
    @DisplayName("updating an unknown feedback id is a 404, not a silent no-op")
    void updateUnknownIdIsNotFound() {
        UUID missing = UUID.randomUUID();
        when(feedback.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(adminId, missing, new UpdateStatusRequest("RESOLVED", null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("an unrecognised status is rejected")
    void rejectsUnknownStatus() {
        Feedback existing = Feedback.submit(userId, "u@example.com", "Maria",
                Feedback.Category.BUG, "Subject", "Message");
        when(feedback.findById(existing.getId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateStatus(
                adminId, existing.getId(), new UpdateStatusRequest("ARCHIVED", null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("counts reflects the three status buckets, not a total")
    void countsBucketsByStatus() {
        when(feedback.countByStatus(Feedback.Status.NEW)).thenReturn(3L);
        when(feedback.countByStatus(Feedback.Status.REVIEWING)).thenReturn(1L);
        when(feedback.countByStatus(Feedback.Status.RESOLVED)).thenReturn(7L);

        var counts = service.counts();

        assertThat(counts.newCount()).isEqualTo(3);
        assertThat(counts.reviewingCount()).isEqualTo(1);
        assertThat(counts.resolvedCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("list with a blank status filters nothing, passing null through to the query")
    void blankStatusMeansNoFilter() {
        when(feedback.search(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.list("  ", 0, 25);

        verify(feedback).search(org.mockito.ArgumentMatchers.isNull(), any(Pageable.class));
    }
}
