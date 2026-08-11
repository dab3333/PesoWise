package ph.pesowise.ledger.web;

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
import ph.pesowise.ledger.api.LedgerDtos.AccountRequest;
import ph.pesowise.ledger.api.LedgerDtos.AccountResponse;
import ph.pesowise.ledger.service.AccountService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountResponse> list(@RequestHeader(Headers.USER_ID) UUID userId) {
        return accountService.list(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(
            @RequestHeader(Headers.USER_ID) UUID userId, @Valid @RequestBody AccountRequest request) {
        return accountService.create(userId, request);
    }

    @PutMapping("/{id}")
    public AccountResponse update(
            @RequestHeader(Headers.USER_ID) UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody AccountRequest request) {
        return accountService.update(userId, id, request);
    }

    /** Archives instead of deleting once transactions reference the account. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(Headers.USER_ID) UUID userId, @PathVariable UUID id) {
        accountService.delete(userId, id);
    }
}
