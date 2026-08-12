package ph.pesowise.planning.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ph.pesowise.planning.api.RecurringDtos.BillOverview;
import ph.pesowise.planning.api.RecurringDtos.BillRequest;
import ph.pesowise.planning.api.RecurringDtos.BillResponse;
import ph.pesowise.planning.api.RecurringDtos.RunResponse;
import ph.pesowise.planning.api.RecurringDtos.RunSummary;
import ph.pesowise.planning.service.RecurringService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/recurring")
public class RecurringController {

    private final RecurringService recurringService;

    public RecurringController(RecurringService recurringService) {
        this.recurringService = recurringService;
    }

    @GetMapping
    public BillOverview list(@RequestHeader(Headers.USER_ID) UUID userId) {
        return recurringService.list(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BillResponse create(
            @RequestHeader(Headers.USER_ID) UUID userId, @Valid @RequestBody BillRequest request) {
        return recurringService.create(userId, request);
    }

    @PutMapping("/{id}")
    public BillResponse update(
            @RequestHeader(Headers.USER_ID) UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody BillRequest request) {
        return recurringService.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(Headers.USER_ID) UUID userId, @PathVariable UUID id) {
        recurringService.delete(userId, id);
    }

    @GetMapping("/{id}/runs")
    public List<RunResponse> history(
            @RequestHeader(Headers.USER_ID) UUID userId, @PathVariable UUID id) {
        return recurringService.history(userId, id);
    }

    /** Confirms a due bill — the path for bills whose amount varies, where auto-post is off. */
    @PostMapping("/{id}/post")
    @ResponseStatus(HttpStatus.CREATED)
    public RunResponse postNow(
            @RequestHeader(Headers.USER_ID) UUID userId, @PathVariable UUID id) {
        return recurringService.postNow(userId, id);
    }

    /** Marks the current occurrence dealt with without recording anything. */
    @PostMapping("/{id}/skip")
    @ResponseStatus(HttpStatus.CREATED)
    public RunResponse skipNow(
            @RequestHeader(Headers.USER_ID) UUID userId, @PathVariable UUID id) {
        return recurringService.skipNow(userId, id);
    }

    /**
     * Runs the daily pass immediately, instead of waiting until after midnight.
     *
     * <p><b>Administrators only.</b> The pass operates on <em>every</em> user's due bills — the
     * scheduler has no notion of a current user — so this posts real transactions to other
     * people's ledgers. Idempotency makes it safe to call twice; it does not make it safe to
     * expose, which is what the original version of this endpoint got wrong: it accepted the
     * caller's id and then ignored it, leaving a system-wide operation open to any signed-in
     * account.
     *
     * <p>It sits outside {@code /api/admin/**}, so the gateway's prefix rule does not cover it
     * and the check has to happen here.
     */
    @PostMapping("/run")
    public RunSummary runNow(@RequestHeader(Headers.USER_ID) UUID userId,
                             @RequestHeader(name = Headers.USER_ROLE, required = false) String role) {
        if (!Headers.ADMIN_ROLE.equals(role)) {
            throw new ForbiddenException("Running the recurring pass is an administrator action.");
        }
        return recurringService.runDueBills();
    }
}
