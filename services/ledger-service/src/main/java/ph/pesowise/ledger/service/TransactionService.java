package ph.pesowise.ledger.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.pesowise.ledger.api.LedgerDtos.PageResponse;
import ph.pesowise.ledger.api.LedgerDtos.SourcedTransactionRequest;
import ph.pesowise.ledger.api.LedgerDtos.TransactionRequest;
import ph.pesowise.ledger.api.LedgerDtos.TransactionResponse;
import ph.pesowise.ledger.domain.Account;
import ph.pesowise.ledger.domain.Category;
import ph.pesowise.ledger.domain.Enums.SourceType;
import ph.pesowise.ledger.domain.Transaction;
import ph.pesowise.ledger.repo.AccountRepository;
import ph.pesowise.ledger.repo.TransactionRepository;
import ph.pesowise.ledger.web.ConflictException;
import ph.pesowise.ledger.web.NotFoundException;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
public class TransactionService {

    /** Caps a hostile or accidental page size; the UI asks for 25. */
    private static final int MAX_PAGE_SIZE = 200;

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final AccountService accountService;
    private final CategoryService categoryService;

    public TransactionService(
            TransactionRepository transactions,
            AccountRepository accounts,
            AccountService accountService,
            CategoryService categoryService) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.accountService = accountService;
        this.categoryService = categoryService;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> search(
            UUID userId,
            LocalDate from,
            LocalDate to,
            UUID categoryId,
            UUID accountId,
            int page,
            int size) {

        // Newest first, with id as a tiebreaker so paging is stable when many rows share a date.
        Sort sort = Sort.by(Sort.Direction.DESC, "txnDate").and(Sort.by(Sort.Direction.DESC, "id"));
        Page<Transaction> found = transactions.search(
                userId, from, to, categoryId, accountId,
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), sort));

        // Two lookup maps for the whole page rather than a join per row.
        Map<UUID, Account> accountsById = accountService.byId(userId);
        Map<UUID, Category> categoriesById = categoryService.byId(userId);

        return new PageResponse<>(
                found.getContent().stream()
                        .map(txn -> TransactionResponse.from(
                                txn, accountsById.get(txn.getAccountId()), categoriesById.get(txn.getCategoryId())))
                        .toList(),
                found.getNumber(), found.getSize(), found.getTotalElements(), found.getTotalPages());
    }

    @Transactional
    public TransactionResponse create(UUID userId, TransactionRequest request) {
        return save(userId, request.accountId(), request.categoryId(), request.amount(),
                request.txnDate(), request.note(), SourceType.MANUAL, null);
    }

    /**
     * Used by planning-service for debt payments, goal contributions and recurring bills.
     *
     * <p>A recurring bill may be posted at most once per date, enforced by a unique index. Hitting it
     * means the occurrence was already recorded — a retry after a partial failure — so it is reported
     * as a 409 rather than a 500, letting the caller treat it as "already done" instead of an error.
     */
    @Transactional
    public TransactionResponse createFromSource(UUID userId, SourcedTransactionRequest request) {
        try {
            return save(userId, request.accountId(), request.categoryId(), request.amount(),
                    request.txnDate(), request.note(), request.sourceType(), request.sourceId());
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(
                    "That recurring bill has already been recorded for %s.".formatted(request.txnDate()));
        }
    }

    private TransactionResponse save(
            UUID userId,
            UUID accountId,
            UUID categoryId,
            java.math.BigDecimal amount,
            LocalDate txnDate,
            String note,
            SourceType sourceType,
            UUID sourceId) {

        // Both lookups are user-scoped, so a transaction can never point at someone else's
        // account or category.
        Account account = requireAccount(userId, accountId);
        Category category = categoryService.require(userId, categoryId);

        Transaction saved = transactions.save(Transaction.create(
                userId, account.getId(), category, amount, txnDate, trimToNull(note), sourceType, sourceId));

        return TransactionResponse.from(saved, account, category);
    }

    @Transactional
    public TransactionResponse update(UUID userId, UUID transactionId, TransactionRequest request) {
        Transaction transaction = transactions.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new NotFoundException("Transaction"));

        Account account = requireAccount(userId, request.accountId());
        Category category = categoryService.require(userId, request.categoryId());

        transaction.setAccountId(account.getId());
        // Re-derives kind from the new category, so the two can never disagree.
        transaction.moveTo(category);
        transaction.setAmount(request.amount());
        transaction.setTxnDate(request.txnDate());
        transaction.setNote(trimToNull(request.note()));

        return TransactionResponse.from(transaction, account, category);
    }

    @Transactional
    public void delete(UUID userId, UUID transactionId) {
        Transaction transaction = transactions.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new NotFoundException("Transaction"));
        transactions.delete(transaction);
    }

    private Account requireAccount(UUID userId, UUID accountId) {
        return accounts.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new NotFoundException("Account"));
    }

    /** Keeps whitespace-only notes out of the database. */
    private static String trimToNull(String note) {
        if (note == null) return null;
        String trimmed = note.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
