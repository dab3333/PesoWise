package ph.pesowise.ledger.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.pesowise.ledger.api.LedgerDtos.AccountExport;
import ph.pesowise.ledger.api.LedgerDtos.CategoryExport;
import ph.pesowise.ledger.api.LedgerDtos.ImportSummary;
import ph.pesowise.ledger.api.LedgerDtos.LedgerExport;
import ph.pesowise.ledger.api.LedgerDtos.LedgerImportResult;
import ph.pesowise.ledger.api.LedgerDtos.TransactionExport;
import ph.pesowise.ledger.domain.Account;
import ph.pesowise.ledger.domain.Category;
import ph.pesowise.ledger.domain.Transaction;
import ph.pesowise.ledger.domain.UserBootstrap;
import ph.pesowise.ledger.repo.AccountRepository;
import ph.pesowise.ledger.repo.CategoryRepository;
import ph.pesowise.ledger.repo.TransactionRepository;
import ph.pesowise.ledger.repo.UserBootstrapRepository;
import ph.pesowise.ledger.web.ConflictException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Backs the Settings page's "Export data" / "Import data" feature. Import always replaces —
 * never merges — a user's data, so it is a full wipe-then-reinsert rather than an upsert.
 *
 * <p>Import always generates <strong>fresh</strong> ids rather than reusing the ones in the
 * file, deliberately: it means "restore my own backup" and "load someone else's export into a
 * different account" are the exact same code path, with no special-casing needed for whether
 * the file happens to belong to the account importing it. The tradeoff is that every id an
 * imported transaction references (its account, its category) has to be remapped from the
 * file's old id to the new one generated here — {@link #importAll} returns those mappings so
 * planning-service's import (which references ledger ids of its own — {@code category_id},
 * {@code account_id}, and every {@code ledger_txn_id} audit pointer) can remap its own data to
 * match, in the same import.
 */
@Service
public class DataExportService {

    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final UserBootstrapRepository bootstraps;
    private final EntityManager entityManager;

    public DataExportService(
            AccountRepository accounts,
            CategoryRepository categories,
            TransactionRepository transactions,
            UserBootstrapRepository bootstraps,
            EntityManager entityManager) {
        this.accounts = accounts;
        this.categories = categories;
        this.transactions = transactions;
        this.bootstraps = bootstraps;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public LedgerExport export(UUID userId) {
        var accountExports = accounts.findByUserId(userId).stream()
                .map(a -> new AccountExport(
                        a.getId(), a.getName(), a.getType(), a.getOpeningBalance(), a.isArchived(), a.getCreatedAt()))
                .toList();

        var categoryExports = categories.findByUserId(userId).stream()
                .map(c -> new CategoryExport(
                        c.getId(), c.getName(), c.getKind(), c.getBucket(), c.getColor(), c.isSystem(),
                        c.isArchived(), c.getCreatedAt()))
                .toList();

        var transactionExports = transactions.findByUserId(userId).stream()
                .map(t -> new TransactionExport(
                        t.getId(), t.getAccountId(), t.getCategoryId(), t.getKind(), t.getAmount(), t.getTxnDate(),
                        t.getNote(), t.getSourceType(), t.getSourceId(), t.getCreatedAt()))
                .toList();

        return new LedgerExport(accountExports, categoryExports, transactionExports);
    }

    /**
     * Wipes this user's accounts, categories, and transactions, then reinserts the file's
     * contents in their place under fresh ids. One transaction, so a failure partway rolls back
     * to the pre-import state rather than leaving a half-replaced ledger.
     *
     * <p>Reinsertion uses {@link EntityManager#persist} rather than the repositories'
     * {@code saveAll} — with generated ids there's no {@code merge()}-vs-{@code persist()}
     * ambiguity to worry about (every id is guaranteed new), but {@code persist()} keeps INSERT
     * semantics explicit rather than relying on Spring Data's id-nullness heuristic.
     */
    @Transactional
    public LedgerImportResult importAll(UUID userId, LedgerExport data) {
        // transactions carries a real FK to accounts/categories with no ON DELETE CASCADE
        // (V1__init.sql), so it must be cleared first — the reverse of the insert order below.
        transactions.deleteByUserId(userId);
        accounts.deleteByUserId(userId);
        categories.deleteByUserId(userId);

        Map<UUID, UUID> accountIds = new HashMap<>();
        Map<UUID, UUID> categoryIds = new HashMap<>();
        Map<UUID, UUID> transactionIds = new HashMap<>();

        try {
            // Derived deleteByX queries load entities and call EntityManager.remove(), which —
            // like persist() below — is deferred until flush. Hibernate's flush order is always
            // inserts before deletes regardless of call order, so without this flush a
            // re-imported account sharing a name with the one just "deleted" collides with it
            // under ux_accounts_user_name (the delete hasn't hit the database yet when the
            // insert runs).
            entityManager.flush();

            for (AccountExport a : data.accounts()) {
                UUID newId = UUID.randomUUID();
                accountIds.put(a.id(), newId);
                entityManager.persist(Account.restore(
                        newId, userId, a.name(), a.type(), a.openingBalance(), a.archived(), a.createdAt()));
            }

            for (CategoryExport c : data.categories()) {
                UUID newId = UUID.randomUUID();
                categoryIds.put(c.id(), newId);
                entityManager.persist(Category.restore(
                        newId, userId, c.name(), c.kind(), c.bucket(), c.color(), c.system(), c.archived(),
                        c.createdAt()));
            }

            for (TransactionExport t : data.transactions()) {
                UUID newId = UUID.randomUUID();
                transactionIds.put(t.id(), newId);
                entityManager.persist(Transaction.restore(
                        newId, userId, accountIds.get(t.accountId()), categoryIds.get(t.categoryId()), t.kind(),
                        t.amount(), t.txnDate(), t.note(), t.sourceType(), t.sourceId(), t.createdAt()));
            }

            // Forces the inserts to run now, inside this try block, rather than at commit.
            entityManager.flush();
        } catch (PersistenceException e) {
            throw new ConflictException("This file could not be imported. Check that it's a valid export file.");
        }

        // A restore implies the user already has real data — the seed job must never run on
        // top of it, exactly the invariant user_bootstrap exists to guarantee.
        if (!bootstraps.existsById(userId)) {
            bootstraps.save(UserBootstrap.of(userId));
        }

        var summary = new ImportSummary(data.accounts().size(), data.categories().size(), data.transactions().size());
        return new LedgerImportResult(summary, accountIds, categoryIds, transactionIds);
    }
}
