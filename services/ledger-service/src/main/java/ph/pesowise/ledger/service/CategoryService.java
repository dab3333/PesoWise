package ph.pesowise.ledger.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.pesowise.ledger.api.LedgerDtos.CategoryRequest;
import ph.pesowise.ledger.api.LedgerDtos.CategoryResponse;
import ph.pesowise.ledger.domain.Category;
import ph.pesowise.ledger.domain.Enums.Bucket;
import ph.pesowise.ledger.domain.Enums.Kind;
import ph.pesowise.ledger.repo.CategoryRepository;
import ph.pesowise.ledger.repo.TransactionRepository;
import ph.pesowise.ledger.web.BadRequestException;
import ph.pesowise.ledger.web.ConflictException;
import ph.pesowise.ledger.web.NotFoundException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final BootstrapService bootstrap;

    public CategoryService(
            CategoryRepository categories, TransactionRepository transactions, BootstrapService bootstrap) {
        this.categories = categories;
        this.transactions = transactions;
        this.bootstrap = bootstrap;
    }

    @Transactional
    public List<CategoryResponse> list(UUID userId) {
        bootstrap.ensureSeeded(userId);
        return categories.findByUserIdAndArchivedFalseOrderByKindAscNameAsc(userId).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional
    public CategoryResponse create(UUID userId, CategoryRequest request) {
        String name = request.name().trim();
        requireNameAvailable(userId, name);
        requireBucketForExpense(request);

        Category category = categories.save(Category.create(
                userId, name, request.kind(), request.bucket(), request.color().toLowerCase(), false));
        return CategoryResponse.from(category);
    }

    /**
     * Name, bucket and colour are editable; {@code kind} is not. Flipping a category from expense
     * to income would silently rewrite the direction of every transaction already filed under it.
     */
    @Transactional
    public CategoryResponse update(UUID userId, UUID categoryId, CategoryRequest request) {
        Category category = require(userId, categoryId);
        String name = request.name().trim();

        if (!name.equalsIgnoreCase(category.getName())) requireNameAvailable(userId, name);
        if (request.kind() != category.getKind()) {
            throw new BadRequestException(
                    "A category's type cannot be changed. Create a new category instead.");
        }
        requireBucketForExpense(request);

        category.setName(name);
        category.setBucket(request.bucket());
        category.setColor(request.color().toLowerCase());

        return CategoryResponse.from(category);
    }

    @Transactional
    public void delete(UUID userId, UUID categoryId) {
        Category category = require(userId, categoryId);

        // Seeded categories stay: reports and the budget suggester assume the bucket set is whole.
        if (category.isSystem()) {
            throw new ConflictException(
                    "\"%s\" is a built-in category and cannot be deleted.".formatted(category.getName()));
        }

        // Archive rather than delete once referenced, so historical reports stay intact.
        if (transactions.existsByCategoryId(categoryId)) {
            category.archive();
        } else {
            categories.delete(category);
        }
    }

    /** Resolves a category for a transaction write, scoped to the owner. */
    @Transactional(readOnly = true)
    public Category require(UUID userId, UUID categoryId) {
        return categories.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new NotFoundException("Category"));
    }

    /** Includes archived categories, since transactions keep referencing them. */
    @Transactional(readOnly = true)
    public Map<UUID, Category> byId(UUID userId) {
        return categories.findByUserId(userId).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
    }

    private void requireNameAvailable(UUID userId, String name) {
        if (categories.existsByUserIdAndArchivedFalseAndNameIgnoreCase(userId, name)) {
            throw new ConflictException("You already have a category called \"%s\".".formatted(name));
        }
    }

    /** Mirrors the DB CHECK so the client gets a field-level message instead of a 500. */
    private static void requireBucketForExpense(CategoryRequest request) {
        if (request.kind() == Kind.EXPENSE && request.bucket() == null) {
            throw new BadRequestException(
                    "Choose whether this is a need, a want, or savings.");
        }
    }

    /** Exposed for the bucket report's target maths. */
    public static int targetPercent(Bucket bucket) {
        return switch (bucket) {
            case NEEDS -> 70;
            case WANTS -> 20;
            case SAVINGS -> 10;
        };
    }
}
