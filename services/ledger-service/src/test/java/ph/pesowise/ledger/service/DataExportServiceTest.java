package ph.pesowise.ledger.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ph.pesowise.ledger.api.LedgerDtos.AccountExport;
import ph.pesowise.ledger.api.LedgerDtos.CategoryExport;
import ph.pesowise.ledger.api.LedgerDtos.LedgerExport;
import ph.pesowise.ledger.api.LedgerDtos.LedgerImportResult;
import ph.pesowise.ledger.api.LedgerDtos.TransactionExport;
import ph.pesowise.ledger.domain.Account;
import ph.pesowise.ledger.domain.Category;
import ph.pesowise.ledger.domain.Enums.AccountType;
import ph.pesowise.ledger.domain.Enums.Kind;
import ph.pesowise.ledger.domain.Enums.SourceType;
import ph.pesowise.ledger.domain.Transaction;
import ph.pesowise.ledger.repo.AccountRepository;
import ph.pesowise.ledger.repo.CategoryRepository;
import ph.pesowise.ledger.repo.TransactionRepository;
import ph.pesowise.ledger.repo.UserBootstrapRepository;
import ph.pesowise.ledger.web.ConflictException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Backs the Settings page's export/import feature.
 *
 * <p>Import always generates fresh ids rather than reusing the file's own — see the javadoc on
 * {@code DataExportService} for why (it's what makes "restore my own backup" and "load someone
 * else's export into a different account" the same code path). These tests check: the export
 * mapping, that reinsertion still respects the FK order using the <em>new</em> ids, that the
 * returned id maps are correct, and that a genuine persistence failure surfaces as a clean 409
 * rather than a raw exception.
 */
@ExtendWith(MockitoExtension.class)
class DataExportServiceTest {

    private static final UUID USER = UUID.fromString("3f1c9e2a-0000-4000-8000-000000000001");

    @Mock
    private AccountRepository accounts;

    @Mock
    private CategoryRepository categories;

    @Mock
    private TransactionRepository transactions;

    @Mock
    private UserBootstrapRepository bootstraps;

    @Mock
    private EntityManager entityManager;

    private DataExportService dataExportService;

    private static Account account() {
        return Account.create(USER, "Cash", AccountType.CASH, BigDecimal.ZERO);
    }

    private static Category category() {
        return Category.create(USER, "Groceries", Kind.EXPENSE, null, "#0f8a6c", false);
    }

    private static Transaction transaction(Account account, Category category) {
        return Transaction.create(
                USER, account.getId(), category, new BigDecimal("250.00"), LocalDate.of(2026, 1, 15),
                "Weekly groceries", SourceType.MANUAL, null);
    }

    @BeforeEach
    void setUp() {
        dataExportService = new DataExportService(accounts, categories, transactions, bootstraps, entityManager);
    }

    @Test
    @DisplayName("export maps every account, category, and transaction field")
    void exportMapsEveryField() {
        Account account = account();
        Category category = category();
        Transaction transaction = transaction(account, category);

        when(accounts.findByUserId(USER)).thenReturn(List.of(account));
        when(categories.findByUserId(USER)).thenReturn(List.of(category));
        when(transactions.findByUserId(USER)).thenReturn(List.of(transaction));

        LedgerExport export = dataExportService.export(USER);

        assertThat(export.accounts()).hasSize(1);
        AccountExport accountExport = export.accounts().get(0);
        assertThat(accountExport.id()).isEqualTo(account.getId());
        assertThat(accountExport.name()).isEqualTo("Cash");
        assertThat(accountExport.type()).isEqualTo(AccountType.CASH);

        assertThat(export.categories()).hasSize(1);
        CategoryExport categoryExport = export.categories().get(0);
        assertThat(categoryExport.id()).isEqualTo(category.getId());
        assertThat(categoryExport.kind()).isEqualTo(Kind.EXPENSE);

        assertThat(export.transactions()).hasSize(1);
        TransactionExport transactionExport = export.transactions().get(0);
        assertThat(transactionExport.id()).isEqualTo(transaction.getId());
        assertThat(transactionExport.accountId()).isEqualTo(account.getId());
        assertThat(transactionExport.categoryId()).isEqualTo(category.getId());
        assertThat(transactionExport.amount()).isEqualByComparingTo("250.00");
    }

    @Test
    @DisplayName("import deletes transactions before accounts/categories, and persists them before transactions")
    void importRespectsForeignKeyOrder() {
        UUID accountId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        Instant now = Instant.now();

        LedgerExport data = new LedgerExport(
                List.of(new AccountExport(accountId, "Cash", AccountType.CASH, BigDecimal.ZERO, false, now)),
                List.of(new CategoryExport(
                        categoryId, "Groceries", Kind.EXPENSE, null, "#0f8a6c", false, false, now)),
                List.of(new TransactionExport(
                        transactionId, accountId, categoryId, Kind.EXPENSE, new BigDecimal("250.00"),
                        LocalDate.of(2026, 1, 15), null, SourceType.MANUAL, null, now)));
        when(bootstraps.existsById(USER)).thenReturn(true);

        dataExportService.importAll(USER, data);

        // transactions has a real FK to accounts and categories with no cascade — deleting it
        // last, or persisting it first, would fail against a real database.
        InOrder deleteOrder = inOrder(transactions, accounts, categories);
        deleteOrder.verify(transactions).deleteByUserId(USER);
        deleteOrder.verify(accounts).deleteByUserId(USER);
        deleteOrder.verify(categories).deleteByUserId(USER);

        ArgumentCaptor<Object> persisted = ArgumentCaptor.forClass(Object.class);
        verify(entityManager, times(3)).persist(persisted.capture());
        List<Object> order = persisted.getAllValues();
        assertThat(order.get(0)).isInstanceOf(Account.class);
        assertThat(order.get(1)).isInstanceOf(Category.class);
        assertThat(order.get(2)).isInstanceOf(Transaction.class);
    }

    @Test
    @DisplayName("import generates fresh ids and remaps the transaction's account/category to match")
    void importGeneratesFreshIdsAndRemapsReferences() {
        UUID oldAccountId = UUID.randomUUID();
        UUID oldCategoryId = UUID.randomUUID();
        UUID oldTransactionId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2025-06-01T00:00:00Z");

        LedgerExport data = new LedgerExport(
                List.of(new AccountExport(
                        oldAccountId, "Cash", AccountType.CASH, BigDecimal.ZERO, false, createdAt)),
                List.of(new CategoryExport(
                        oldCategoryId, "Groceries", Kind.EXPENSE, null, "#0f8a6c", false, false, createdAt)),
                List.of(new TransactionExport(
                        oldTransactionId, oldAccountId, oldCategoryId, Kind.EXPENSE, new BigDecimal("250.00"),
                        LocalDate.of(2026, 1, 15), "note", SourceType.MANUAL, null, createdAt)));
        when(bootstraps.existsById(USER)).thenReturn(true);

        LedgerImportResult result = dataExportService.importAll(USER, data);

        // Fresh ids, not the file's originals.
        UUID newAccountId = result.accountIds().get(oldAccountId);
        UUID newCategoryId = result.categoryIds().get(oldCategoryId);
        UUID newTransactionId = result.transactionIds().get(oldTransactionId);
        assertThat(newAccountId).isNotNull().isNotEqualTo(oldAccountId);
        assertThat(newCategoryId).isNotNull().isNotEqualTo(oldCategoryId);
        assertThat(newTransactionId).isNotNull().isNotEqualTo(oldTransactionId);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(entityManager).persist(captor.capture());
        Transaction restored = captor.getValue();
        assertThat(restored.getId()).isEqualTo(newTransactionId);
        assertThat(restored.getAccountId()).isEqualTo(newAccountId);
        assertThat(restored.getCategoryId()).isEqualTo(newCategoryId);
        assertThat(restored.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("a user without an existing bootstrap marker gets one, so the seed job never re-fires")
    void importClaimsBootstrapIfMissing() {
        when(bootstraps.existsById(USER)).thenReturn(false);

        LedgerImportResult result = dataExportService.importAll(
                USER, new LedgerExport(List.of(), List.of(), List.of()));

        verify(bootstraps).save(ArgumentMatchers.any());
        assertThat(result.summary().accounts()).isZero();
        assertThat(result.summary().categories()).isZero();
        assertThat(result.summary().transactions()).isZero();
    }

    @Test
    @DisplayName("a persistence failure surfaces as a clean conflict rather than a raw exception")
    void importSurfacesAPersistenceFailureAsAConflict() {
        // Simulates a genuine constraint violation during the flush that forces the inserts to
        // actually run — entityManager.flush() is where a deferred INSERT executes.
        doThrow(new PersistenceException("constraint violation")).when(entityManager).flush();

        LedgerExport data = new LedgerExport(
                List.of(new AccountExport(
                        UUID.randomUUID(), "Cash", AccountType.CASH, BigDecimal.ZERO, false, Instant.now())),
                List.of(), List.of());

        assertThatThrownBy(() -> dataExportService.importAll(USER, data))
                .isInstanceOf(ConflictException.class);
    }
}
