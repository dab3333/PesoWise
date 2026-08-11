package ph.pesowise.ledger.web;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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
import ph.pesowise.ledger.api.LedgerDtos.PageResponse;
import ph.pesowise.ledger.api.LedgerDtos.SourcedTransactionRequest;
import ph.pesowise.ledger.api.LedgerDtos.TransactionRequest;
import ph.pesowise.ledger.api.LedgerDtos.TransactionResponse;
import ph.pesowise.ledger.service.TransactionService;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * Filtered, paged list. Omitted {@code from}/{@code to} default to the current month, which
     * is what the Transactions page opens on — an unbounded default would scan every row.
     */
    @GetMapping
    public PageResponse<TransactionResponse> search(
            @RequestHeader(Headers.USER_ID) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        YearMonth thisMonth = YearMonth.now();
        LocalDate start = from != null ? from : thisMonth.atDay(1);
        LocalDate end = to != null ? to : thisMonth.atEndOfMonth();

        if (start.isAfter(end)) {
            throw new BadRequestException("The start date must not be after the end date.");
        }

        return transactionService.search(userId, start, end, categoryId, accountId, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse create(
            @RequestHeader(Headers.USER_ID) UUID userId, @Valid @RequestBody TransactionRequest request) {
        return transactionService.create(userId, request);
    }

    /**
     * Posts a transaction on behalf of planning-service, recording what created it. Reached
     * through the gateway with the owning user's token, so it needs no separate trust model.
     */
    @PostMapping("/sourced")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse createFromSource(
            @RequestHeader(Headers.USER_ID) UUID userId,
            @Valid @RequestBody SourcedTransactionRequest request) {
        return transactionService.createFromSource(userId, request);
    }

    @PutMapping("/{id}")
    public TransactionResponse update(
            @RequestHeader(Headers.USER_ID) UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody TransactionRequest request) {
        return transactionService.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(Headers.USER_ID) UUID userId, @PathVariable UUID id) {
        transactionService.delete(userId, id);
    }
}
