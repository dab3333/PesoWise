package ph.pesowise.ledger.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ph.pesowise.ledger.api.LedgerDtos.AccountRequest;
import ph.pesowise.ledger.domain.Enums.AccountType;
import ph.pesowise.ledger.repo.AccountRepository;
import ph.pesowise.ledger.repo.TransactionRepository;
import ph.pesowise.ledger.web.NotFoundException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Every account lookup must be scoped to the caller's own {@code userId}. A plain
 * {@code findById} would let anyone who guesses — or is handed — another user's account UUID
 * read or mutate it. These tests exist so that guard is checked in code, not only asserted in a
 * comment.
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final UUID OWNER = UUID.fromString("11111111-0000-4000-8000-000000000001");
    private static final UUID ATTACKER = UUID.fromString("22222222-0000-4000-8000-000000000002");
    private static final UUID SOMEONE_ELSES_ACCOUNT =
            UUID.fromString("33333333-0000-4000-8000-000000000003");

    @Mock
    private AccountRepository accounts;

    @Mock
    private TransactionRepository transactions;

    @Mock
    private BootstrapService bootstrap;

    private AccountService accountService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        accountService = new AccountService(accounts, transactions, bootstrap);
    }

    private static AccountRequest anyValidRequest() {
        return new AccountRequest("Renamed", AccountType.CASH, BigDecimal.ZERO);
    }

    @Test
    @DisplayName("updating another user's account returns 404, never the record")
    void updateIsScopedToTheOwner() {
        // The repository is asked for (ACCOUNT, ATTACKER) and correctly finds nothing, because the
        // account belongs to OWNER — this is the guard actually being exercised, not bypassed.
        when(accounts.findByIdAndUserId(SOMEONE_ELSES_ACCOUNT, ATTACKER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.update(ATTACKER, SOMEONE_ELSES_ACCOUNT, anyValidRequest()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("deleting another user's account returns 404, never the record")
    void deleteIsScopedToTheOwner() {
        when(accounts.findByIdAndUserId(SOMEONE_ELSES_ACCOUNT, ATTACKER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.delete(ATTACKER, SOMEONE_ELSES_ACCOUNT))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("the owner's own lookup by the same id succeeds")
    void ownerCanReachTheirOwnAccount() {
        var account = ph.pesowise.ledger.domain.Account.create(
                OWNER, "GCash", AccountType.EWALLET, BigDecimal.ZERO);
        when(accounts.findByIdAndUserId(account.getId(), OWNER)).thenReturn(Optional.of(account));

        // No exception — proves the isolation above is about ownership, not a blanket rejection.
        accountService.delete(OWNER, account.getId());
    }
}
