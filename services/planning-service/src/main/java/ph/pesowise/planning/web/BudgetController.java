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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ph.pesowise.planning.api.BudgetDtos.BudgetOverview;
import ph.pesowise.planning.api.BudgetDtos.BudgetRequest;
import ph.pesowise.planning.api.BudgetDtos.BulkBudgetRequest;
import ph.pesowise.planning.api.BudgetDtos.SuggestionRequest;
import ph.pesowise.planning.api.BudgetDtos.SuggestionResponse;
import ph.pesowise.planning.service.BudgetService;
import ph.pesowise.planning.service.BudgetSuggester;
import ph.pesowise.planning.service.Months;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/budgets")
public class BudgetController {

    private final BudgetService budgetService;
    private final BudgetSuggester suggester;

    public BudgetController(BudgetService budgetService, BudgetSuggester suggester) {
        this.budgetService = budgetService;
        this.suggester = suggester;
    }

    /** @param month YYYY-MM, defaulting to the current month */
    @GetMapping
    public BudgetOverview overview(
            @RequestHeader(Headers.USER_ID) UUID userId,
            @RequestParam(required = false) String month) {
        return budgetService.overview(userId, Months.parse(month));
    }

    /** Upsert: one intention — "set the budget for this category this month". */
    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void upsert(
            @RequestHeader(Headers.USER_ID) UUID userId,
            @RequestParam(required = false) String month,
            @Valid @RequestBody BudgetRequest request) {
        budgetService.upsert(userId, Months.parse(month), request);
    }

    /** Saves many limits in one transaction, so applying a suggestion is all-or-nothing. */
    @PutMapping("/bulk")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void upsertAll(
            @RequestHeader(Headers.USER_ID) UUID userId,
            @RequestParam(required = false) String month,
            @Valid @RequestBody BulkBudgetRequest request) {
        budgetService.upsertAll(userId, Months.parse(month), request.budgets());
    }

    @DeleteMapping("/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @RequestHeader(Headers.USER_ID) UUID userId,
            @PathVariable UUID categoryId,
            @RequestParam(required = false) String month) {
        budgetService.delete(userId, Months.parse(month), categoryId);
    }

    /**
     * A preview only — nothing is saved. The client shows the proposed limits, lets the user adjust
     * them, then applies the result via the bulk endpoint.
     *
     * <p>POST rather than GET because the expected income goes in a body, and because a future
     * version may take per-bucket overrides.
     */
    @PostMapping("/suggestion")
    public SuggestionResponse suggestion(
            @RequestHeader(Headers.USER_ID) UUID userId,
            @RequestParam(required = false) String month,
            @Valid @RequestBody(required = false) SuggestionRequest request) {
        return suggester.suggest(
                userId, Months.parse(month), request == null ? null : request.expectedIncome());
    }

    /** Copies every limit from the previous month. */
    @PostMapping("/copy-previous")
    public Map<String, Integer> copyPrevious(
            @RequestHeader(Headers.USER_ID) UUID userId,
            @RequestParam(required = false) String month) {
        return Map.of("copied", budgetService.copyFromPreviousMonth(userId, Months.parse(month)));
    }
}
