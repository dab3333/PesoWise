package ph.pesowise.ledger.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ph.pesowise.ledger.domain.Transaction;
import ph.pesowise.ledger.repo.Projections.BucketTotal;
import ph.pesowise.ledger.repo.Projections.CategoryTotal;
import ph.pesowise.ledger.repo.Projections.DailyTotal;
import ph.pesowise.ledger.repo.Projections.PeriodTotals;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByCategoryId(UUID categoryId);

    boolean existsByAccountId(UUID accountId);

    /**
     * The filtered list. Null filters are ignored, which keeps one query serving every
     * combination the Transactions page offers instead of a Specification tree.
     */
    @Query("""
            SELECT t FROM Transaction t
            WHERE t.userId = :userId
              AND t.txnDate >= :from
              AND t.txnDate <= :to
              AND (:categoryId IS NULL OR t.categoryId = :categoryId)
              AND (:accountId IS NULL OR t.accountId = :accountId)
            """)
    Page<Transaction> search(
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("categoryId") UUID categoryId,
            @Param("accountId") UUID accountId,
            Pageable pageable);

    /* ------------------------------------------------------------ aggregates
       All summing happens in Postgres. Pulling rows into Java to add them up would mean
       transferring a year of transactions to render one number. */

    @Query(value = """
            SELECT COALESCE(SUM(CASE WHEN kind = 'INCOME'  THEN amount END), 0) AS income,
                   COALESCE(SUM(CASE WHEN kind = 'EXPENSE' THEN amount END), 0) AS expense
            FROM transactions
            WHERE user_id = :userId AND txn_date BETWEEN :from AND :to
            """, nativeQuery = true)
    PeriodTotals findTotals(
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * Includes categories with no activity in the period (LEFT JOIN from categories), so the
     * budgets page can show an untouched category as ₱0 spent rather than omitting it.
     */
    @Query(value = """
            SELECT c.id             AS categoryId,
                   c.name           AS categoryName,
                   c.color          AS color,
                   c.kind           AS kind,
                   c.bucket         AS bucket,
                   COALESCE(SUM(t.amount), 0) AS total
            FROM categories c
            LEFT JOIN transactions t
                   ON t.category_id = c.id
                  AND t.user_id = c.user_id
                  AND t.txn_date BETWEEN :from AND :to
            WHERE c.user_id = :userId AND NOT c.archived
            GROUP BY c.id, c.name, c.color, c.kind, c.bucket
            ORDER BY total DESC, c.name ASC
            """, nativeQuery = true)
    List<CategoryTotal> findTotalsByCategory(
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** Expense only — 70-20-10 divides spending, and income categories carry no bucket. */
    @Query(value = """
            SELECT c.bucket AS bucket, COALESCE(SUM(t.amount), 0) AS total
            FROM transactions t
            JOIN categories c ON c.id = t.category_id
            WHERE t.user_id = :userId
              AND t.kind = 'EXPENSE'
              AND t.txn_date BETWEEN :from AND :to
            GROUP BY c.bucket
            """, nativeQuery = true)
    List<BucketTotal> findExpenseTotalsByBucket(
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** Only days with activity are returned; the caller fills the gaps with zeroes. */
    @Query(value = """
            SELECT txn_date AS day,
                   COALESCE(SUM(CASE WHEN kind = 'INCOME'  THEN amount END), 0) AS income,
                   COALESCE(SUM(CASE WHEN kind = 'EXPENSE' THEN amount END), 0) AS expense
            FROM transactions
            WHERE user_id = :userId AND txn_date BETWEEN :from AND :to
            GROUP BY txn_date
            ORDER BY txn_date
            """, nativeQuery = true)
    List<DailyTotal> findDailyTotals(
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
