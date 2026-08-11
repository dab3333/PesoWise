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
import ph.pesowise.planning.api.DebtDtos.DebtOverview;
import ph.pesowise.planning.api.DebtDtos.DebtRequest;
import ph.pesowise.planning.api.DebtDtos.DebtResponse;
import ph.pesowise.planning.api.DebtDtos.DebtUpdateRequest;
import ph.pesowise.planning.api.DebtDtos.PaymentRequest;
import ph.pesowise.planning.api.DebtDtos.PaymentResponse;
import ph.pesowise.planning.service.DebtService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/debts")
public class DebtController {

    private final DebtService debtService;

    public DebtController(DebtService debtService) {
        this.debtService = debtService;
    }

    @GetMapping
    public DebtOverview list(@RequestHeader(Headers.USER_ID) UUID userId) {
        return debtService.list(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DebtResponse create(
            @RequestHeader(Headers.USER_ID) UUID userId, @Valid @RequestBody DebtRequest request) {
        return debtService.create(userId, request);
    }

    /** Descriptive fields only — principal and direction are fixed once payments exist. */
    @PutMapping("/{id}")
    public DebtResponse update(
            @RequestHeader(Headers.USER_ID) UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody DebtUpdateRequest request) {
        return debtService.update(userId, id, request);
    }

    /** Removes the debt and its payment records. The ledger transactions are kept. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(Headers.USER_ID) UUID userId, @PathVariable UUID id) {
        debtService.delete(userId, id);
    }

    @GetMapping("/{id}/payments")
    public List<PaymentResponse> payments(
            @RequestHeader(Headers.USER_ID) UUID userId, @PathVariable UUID id) {
        return debtService.payments(userId, id);
    }

    /** Reduces the balance and writes the matching transaction to the ledger. */
    @PostMapping("/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse recordPayment(
            @RequestHeader(Headers.USER_ID) UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody PaymentRequest request) {
        return debtService.recordPayment(userId, id, request);
    }

    /** Restores the balance and removes the ledger transaction the payment created. */
    @DeleteMapping("/{id}/payments/{paymentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePayment(
            @RequestHeader(Headers.USER_ID) UUID userId,
            @PathVariable UUID id,
            @PathVariable UUID paymentId) {
        debtService.deletePayment(userId, id, paymentId);
    }
}
