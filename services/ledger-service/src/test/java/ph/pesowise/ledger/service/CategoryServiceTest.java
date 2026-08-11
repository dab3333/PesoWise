package ph.pesowise.ledger.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ph.pesowise.ledger.domain.Category;
import ph.pesowise.ledger.domain.Enums.Kind;
import ph.pesowise.ledger.repo.CategoryRepository;
import ph.pesowise.ledger.repo.TransactionRepository;
import ph.pesowise.ledger.web.NotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * {@link CategoryService#require} is the lookup {@link TransactionService} calls on every write —
 * if it were not user-scoped, a transaction could be filed under another user's category.
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final UUID OWNER = UUID.fromString("11111111-0000-4000-8000-000000000001");
    private static final UUID ATTACKER = UUID.fromString("22222222-0000-4000-8000-000000000002");
    private static final UUID SOMEONE_ELSES_CATEGORY =
            UUID.fromString("33333333-0000-4000-8000-000000000003");

    @Mock
    private CategoryRepository categories;

    @Mock
    private TransactionRepository transactions;

    @Mock
    private BootstrapService bootstrap;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categories, transactions, bootstrap);
    }

    @Test
    @DisplayName("resolving another user's category returns 404, never the record")
    void requireIsScopedToTheOwner() {
        when(categories.findByIdAndUserId(SOMEONE_ELSES_CATEGORY, ATTACKER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.require(ATTACKER, SOMEONE_ELSES_CATEGORY))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("deleting another user's category returns 404, never the record")
    void deleteIsScopedToTheOwner() {
        when(categories.findByIdAndUserId(SOMEONE_ELSES_CATEGORY, ATTACKER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.delete(ATTACKER, SOMEONE_ELSES_CATEGORY))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("the owner's own lookup by the same id succeeds")
    void ownerCanReachTheirOwnCategory() {
        Category category = Category.create(OWNER, "Groceries", Kind.EXPENSE, null, "#0f8a6c", false);
        when(categories.findByIdAndUserId(category.getId(), OWNER)).thenReturn(Optional.of(category));

        // No exception thrown — proves the guard above blocks by ownership, not universally.
        categoryService.require(OWNER, category.getId());
    }
}
