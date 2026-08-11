package ph.pesowise.ledger.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.pesowise.ledger.api.LedgerDtos.AccountRequest;
import ph.pesowise.ledger.api.LedgerDtos.AccountResponse;
import ph.pesowise.ledger.domain.Account;
import ph.pesowise.ledger.repo.AccountRepository;
import ph.pesowise.ledger.repo.Projections.AccountBalance;
import ph.pesowise.ledger.repo.TransactionRepository;
import ph.pesowise.ledger.web.ConflictException;
import ph.pesowise.ledger.web.NotFoundException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final BootstrapService bootstrap;

    public AccountService(
            AccountRepository accounts, TransactionRepository transactions, BootstrapService bootstrap) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.bootstrap = bootstrap;
    }

    @Transactional
    public List<AccountResponse> list(UUID userId) {
        bootstrap.ensureSeeded(userId);

        // One grouped query for all balances, then a map join — not a query per account.
        Map<UUID, BigDecimal> balances = accounts.findBalancesByUserId(userId).stream()
                .collect(Collectors.toMap(AccountBalance::getAccountId, AccountBalance::getBalance));

        return accounts.findByUserIdAndArchivedFalseOrderByCreatedAtAsc(userId).stream()
                .map(account -> toResponse(account, balances))
                .toList();
    }

    @Transactional
    public AccountResponse create(UUID userId, AccountRequest request) {
        String name = request.name().trim();
        requireNameAvailable(userId, name);

        Account account = accounts.save(
                Account.create(userId, name, request.type(), request.openingBalance()));

        // A brand new account has no transactions, so its balance is its opening balance.
        return new AccountResponse(
                account.getId(), account.getName(), account.getType(),
                account.getOpeningBalance(), account.getOpeningBalance());
    }

    @Transactional
    public AccountResponse update(UUID userId, UUID accountId, AccountRequest request) {
        Account account = require(userId, accountId);
        String name = request.name().trim();

        if (!name.equalsIgnoreCase(account.getName())) requireNameAvailable(userId, name);

        account.setName(name);
        account.setType(request.type());
        account.setOpeningBalance(request.openingBalance());

        return toResponse(account, balancesFor(userId));
    }

    /**
     * Archives rather than deletes once transactions reference the account — removing it would
     * orphan them and silently change every historical report.
     */
    @Transactional
    public void delete(UUID userId, UUID accountId) {
        Account account = require(userId, accountId);

        if (transactions.existsByAccountId(accountId)) {
            account.archive();
        } else {
            accounts.delete(account);
        }
    }

    private Account require(UUID userId, UUID accountId) {
        return accounts.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new NotFoundException("Account"));
    }

    private void requireNameAvailable(UUID userId, String name) {
        if (accounts.existsByUserIdAndArchivedFalseAndNameIgnoreCase(userId, name)) {
            throw new ConflictException("You already have an account called \"%s\".".formatted(name));
        }
    }

    private Map<UUID, BigDecimal> balancesFor(UUID userId) {
        return accounts.findBalancesByUserId(userId).stream()
                .collect(Collectors.toMap(AccountBalance::getAccountId, AccountBalance::getBalance));
    }

    private static AccountResponse toResponse(Account account, Map<UUID, BigDecimal> balances) {
        return new AccountResponse(
                account.getId(), account.getName(), account.getType(), account.getOpeningBalance(),
                balances.getOrDefault(account.getId(), account.getOpeningBalance()));
    }

    /**
     * Used by TransactionService to label rows without re-querying per transaction. Includes
     * archived accounts, since transactions keep referencing them.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Account> byId(UUID userId) {
        return accounts.findByUserId(userId).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
    }
}
