package ph.pesowise.ledger.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ph.pesowise.ledger.domain.Account;
import ph.pesowise.ledger.repo.Projections.AccountBalance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByUserIdAndArchivedFalseOrderByCreatedAtAsc(UUID userId);

    /** Includes archived accounts — needed to label transactions that still reference them. */
    List<Account> findByUserId(UUID userId);

    /**
     * Always look up by id <em>and</em> user id. A plain findById would let anyone who guesses a
     * UUID read or mutate another user's account.
     */
    Optional<Account> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndArchivedFalseAndNameIgnoreCase(UUID userId, String name);

    /**
     * Current balance per account, computed rather than stored: opening balance + income −
     * expense. One grouped query, so listing accounts is a single round trip however many the
     * user has.
     */
    @Query(value = """
            SELECT a.id AS accountId,
                   a.opening_balance
                     + COALESCE(SUM(CASE WHEN t.kind = 'INCOME' THEN t.amount
                                         ELSE -t.amount END), 0) AS balance
            FROM accounts a
            LEFT JOIN transactions t ON t.account_id = a.id AND t.user_id = a.user_id
            WHERE a.user_id = :userId
            GROUP BY a.id, a.opening_balance
            """, nativeQuery = true)
    List<AccountBalance> findBalancesByUserId(@Param("userId") UUID userId);
}
