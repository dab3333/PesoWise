package ph.pesowise.ledger.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.pesowise.ledger.domain.Account;
import ph.pesowise.ledger.domain.Category;
import ph.pesowise.ledger.domain.Enums.AccountType;
import ph.pesowise.ledger.domain.Enums.Bucket;
import ph.pesowise.ledger.domain.Enums.Kind;
import ph.pesowise.ledger.domain.UserBootstrap;
import ph.pesowise.ledger.repo.AccountRepository;
import ph.pesowise.ledger.repo.CategoryRepository;
import ph.pesowise.ledger.repo.UserBootstrapRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Gives a new user a usable starting point: one Cash account and a set of Philippine-flavoured
 * categories, each pre-tagged with its 70-20-10 bucket so the budget suggester and the bucket
 * report work from the very first transaction.
 *
 * <p>Seeding is lazy — ledger-service never sees a registration event, so the first request that
 * needs categories triggers it. The {@code user_bootstrap} marker makes it happen exactly once,
 * so a user who deliberately deletes every category does not get them back.
 */
@Service
public class BootstrapService {

    private static final Logger log = LoggerFactory.getLogger(BootstrapService.class);

    /** Colours come from the DESIGN.md chart ramp so charts stay consistent app-wide. */
    private record Seed(String name, Kind kind, Bucket bucket, String color) {
    }

    private static final List<Seed> DEFAULT_CATEGORIES = List.of(
            // Income
            new Seed("Salary", Kind.INCOME, null, "#0f8a6c"),
            new Seed("Business", Kind.INCOME, null, "#65a30d"),
            new Seed("Other Income", Kind.INCOME, null, "#0891b2"),

            // Needs — 70%
            new Seed("Groceries", Kind.EXPENSE, Bucket.NEEDS, "#0f8a6c"),
            new Seed("Rent", Kind.EXPENSE, Bucket.NEEDS, "#2563eb"),
            new Seed("Utilities", Kind.EXPENSE, Bucket.NEEDS, "#d97706"),
            new Seed("Transportation", Kind.EXPENSE, Bucket.NEEDS, "#7c3aed"),
            new Seed("Load & Internet", Kind.EXPENSE, Bucket.NEEDS, "#0891b2"),
            new Seed("Health", Kind.EXPENSE, Bucket.NEEDS, "#db2777"),
            new Seed("Education", Kind.EXPENSE, Bucket.NEEDS, "#65a30d"),

            // Wants — 20%
            new Seed("Dining Out", Kind.EXPENSE, Bucket.WANTS, "#d97706"),
            new Seed("Shopping", Kind.EXPENSE, Bucket.WANTS, "#db2777"),
            new Seed("Entertainment", Kind.EXPENSE, Bucket.WANTS, "#7c3aed"),
            new Seed("Subscriptions", Kind.EXPENSE, Bucket.WANTS, "#2563eb"),

            // Savings — 10%
            new Seed("Savings", Kind.EXPENSE, Bucket.SAVINGS, "#0f8a6c"),
            new Seed("Debt Payment", Kind.EXPENSE, Bucket.SAVINGS, "#64748b")
    );

    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final UserBootstrapRepository bootstraps;

    public BootstrapService(
            AccountRepository accounts, CategoryRepository categories, UserBootstrapRepository bootstraps) {
        this.accounts = accounts;
        this.categories = categories;
        this.bootstraps = bootstraps;
    }

    /** Called at the top of every read that expects accounts or categories to exist. */
    @Transactional
    public void ensureSeeded(UUID userId) {
        if (bootstraps.existsById(userId)) return;

        try {
            // Claim the marker first. Two concurrent first requests race here, and the primary
            // key makes the loser fail before it can duplicate the seed data.
            bootstraps.saveAndFlush(UserBootstrap.of(userId));
        } catch (DataIntegrityViolationException e) {
            log.debug("Bootstrap already claimed for user {}", userId);
            return;
        }

        accounts.save(Account.create(userId, "Cash", AccountType.CASH, BigDecimal.ZERO));
        categories.saveAll(DEFAULT_CATEGORIES.stream()
                .map(seed -> Category.create(userId, seed.name(), seed.kind(), seed.bucket(), seed.color(), true))
                .toList());

        log.info("Seeded {} categories and a Cash account for user {}", DEFAULT_CATEGORIES.size(), userId);
    }
}
