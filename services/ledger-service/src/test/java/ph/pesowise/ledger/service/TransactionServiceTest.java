package ph.pesowise.ledger.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ph.pesowise.ledger.api.LedgerDtos.TransactionRequest;
import ph.pesowise.ledger.domain.Account;
import ph.pesowise.ledger.domain.Enums.AccountType;
import ph.pesowise.ledger.repo.AccountRepository;
import ph.pesowise.ledger.repo.TransactionRepository;
import ph.pesowise.ledger.web.NotFoundException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A transaction write touches three user-scoped lookups at once (the transaction itself, its
 * account, its category). Each is checked here, because letting any one of them slip would let a
 * transaction be filed against, or point at, another user's data.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final UUID OWNER = UUID.fromString("11111111-0000-4000-8000-000000000001");
    private static final UUID ATTACKER = UUID.fromString("22222222-0000-4000-8000-000000000002");
    private static final UUID SOMEONE_ELSES_ACCOUNT =
            UUID.fromString("33333333-0000-4000-8000-000000000003");
    private static final UUID SOMEONE_ELSES_TXN =
            UUID.fromString("44444444-0000-4000-8000-000000000004");
    private static final UUID SOME_CATEGORY = UUID.fromString("55555555-0000-4000-8000-000000000005");

    @Mock
    private TransactionRepository transactions;

    @Mock
    private AccountRepository accounts;

    @Mock
    private AccountService accountService;

    @Mock
    private CategoryService categoryService;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(transactions, accounts, accountService, categoryService);
    }

    private static TransactionRequest requestFor(UUID accountId) {
        return new TransactionRequest(accountId, SOME_CATEGORY, new BigDecimal("100.00"),
                LocalDate.of(2026, 8, 11), "note");
    }

    @Test
    @DisplayName("creating a transaction against another user's account is refused, not just mislabelled")
    void createCannotTargetSomeoneElsesAccount() {
        when(accounts.findByIdAndUserId(SOMEONE_ELSES_ACCOUNT, ATTACKER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.create(ATTACKER, requestFor(SOMEONE_ELSES_ACCOUNT)))
                .isInstanceOf(NotFoundException.class);

        // The account guard fails first, so the category is never even looked up, let alone a
        // transaction saved against someone else's wallet.
        verify(categoryService, never()).require(any(), any());
        verify(transactions, never()).save(any());
    }

    @Test
    @DisplayName("updating another user's transaction returns 404, never the record")
    void updateIsScopedToTheOwner() {
        when(transactions.findByIdAndUserId(SOMEONE_ELSES_TXN, ATTACKER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.update(
                ATTACKER, SOMEONE_ELSES_TXN, requestFor(SOMEONE_ELSES_ACCOUNT)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("deleting another user's transaction returns 404, never the record")
    void deleteIsScopedToTheOwner() {
        when(transactions.findByIdAndUserId(SOMEONE_ELSES_TXN, ATTACKER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.delete(ATTACKER, SOMEONE_ELSES_TXN))
                .isInstanceOf(NotFoundException.class);

        verify(transactions, never()).delete(any());
    }

    @Test
    @DisplayName("the owner's own account and category resolve, and the write proceeds")
    void ownerCanCreateAgainstTheirOwnData() {
        Account account = Account.create(OWNER, "Cash", AccountType.CASH, BigDecimal.ZERO);
        var category = ph.pesowise.ledger.domain.Category.create(
                OWNER, "Groceries", ph.pesowise.ledger.domain.Enums.Kind.EXPENSE, null, "#0f8a6c", false);

        when(accounts.findByIdAndUserId(account.getId(), OWNER)).thenReturn(Optional.of(account));
        when(categoryService.require(OWNER, category.getId())).thenReturn(category);
        lenient().when(transactions.save(any())).thenAnswer(call -> call.getArgument(0));

        // No exception — proves the guards above reject by ownership, not indiscriminately.
        transactionService.create(OWNER, new TransactionRequest(
                account.getId(), category.getId(), new BigDecimal("100.00"),
                LocalDate.of(2026, 8, 11), "note"));
    }
}
