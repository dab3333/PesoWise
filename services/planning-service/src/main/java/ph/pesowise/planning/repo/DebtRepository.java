package ph.pesowise.planning.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ph.pesowise.planning.domain.Debt;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DebtRepository extends JpaRepository<Debt, UUID> {

    /** Active first, then by due date — what is urgent leads the list. */
    List<Debt> findByUserIdOrderByStatusAscDueDateAscCreatedAtAsc(UUID userId);

    /** Scoped by user id so a guessed UUID cannot reach another user's debt. */
    Optional<Debt> findByIdAndUserId(UUID id, UUID userId);

    long countByStatus(Debt.Status status);

    /**
     * Outstanding balance across every user's active debts, split by direction — the system-wide
     * equivalent of the per-user totals in {@code DebtOverview}. Not user-scoped, on purpose: this
     * backs the admin overview.
     */
    @Query("""
            SELECT COALESCE(SUM(d.balance), 0) FROM Debt d
            WHERE d.status = ph.pesowise.planning.domain.Debt.Status.ACTIVE
              AND d.direction = ph.pesowise.planning.domain.Debt.Direction.OWED_BY_ME
            """)
    BigDecimal sumActiveBalanceOwedByUsers();

    @Query("""
            SELECT COALESCE(SUM(d.balance), 0) FROM Debt d
            WHERE d.status = ph.pesowise.planning.domain.Debt.Status.ACTIVE
              AND d.direction = ph.pesowise.planning.domain.Debt.Direction.OWED_TO_ME
            """)
    BigDecimal sumActiveBalanceOwedToUsers();
}
