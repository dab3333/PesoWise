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
import ph.pesowise.planning.api.GoalDtos.ContributionRequest;
import ph.pesowise.planning.api.GoalDtos.ContributionResponse;
import ph.pesowise.planning.api.GoalDtos.GoalOverview;
import ph.pesowise.planning.api.GoalDtos.GoalRequest;
import ph.pesowise.planning.api.GoalDtos.GoalResponse;
import ph.pesowise.planning.service.GoalService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @GetMapping
    public GoalOverview list(@RequestHeader(Headers.USER_ID) UUID userId) {
        return goalService.list(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse create(
            @RequestHeader(Headers.USER_ID) UUID userId, @Valid @RequestBody GoalRequest request) {
        return goalService.create(userId, request);
    }

    /** The target amount is editable here, unlike a debt's principal. */
    @PutMapping("/{id}")
    public GoalResponse update(
            @RequestHeader(Headers.USER_ID) UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody GoalRequest request) {
        return goalService.update(userId, id, request);
    }

    /** Removes the goal and its contribution records. The ledger transactions are kept. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(Headers.USER_ID) UUID userId, @PathVariable UUID id) {
        goalService.delete(userId, id);
    }

    @GetMapping("/{id}/contributions")
    public List<ContributionResponse> contributions(
            @RequestHeader(Headers.USER_ID) UUID userId, @PathVariable UUID id) {
        return goalService.contributions(userId, id);
    }

    /** Records the contribution and writes the matching transaction to the ledger. */
    @PostMapping("/{id}/contributions")
    @ResponseStatus(HttpStatus.CREATED)
    public ContributionResponse contribute(
            @RequestHeader(Headers.USER_ID) UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody ContributionRequest request) {
        return goalService.contribute(userId, id, request);
    }

    /** Undo: removes the contribution and the ledger transaction it created. */
    @DeleteMapping("/{id}/contributions/{contributionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteContribution(
            @RequestHeader(Headers.USER_ID) UUID userId,
            @PathVariable UUID id,
            @PathVariable UUID contributionId) {
        goalService.deleteContribution(userId, id, contributionId);
    }
}
