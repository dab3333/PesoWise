package ph.pesowise.planning.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ph.pesowise.planning.domain.RecurringBill;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Top-level on purpose. Spring Data only detects repository interfaces declared at the top level —
 * nesting these inside a holder class compiles and unit-tests perfectly well, then fails at startup
 * with "No qualifying bean of type BillRepository". Learned the hard way.
 */
public interface RecurringBillRepository extends JpaRepository<RecurringBill, UUID> {

    List<RecurringBill> findByUserIdOrderByActiveDescNextRunDateAsc(UUID userId);

    Optional<RecurringBill> findByIdAndUserId(UUID id, UUID userId);

    /**
     * The scheduler's only query — active bills whose next occurrence has arrived, across all users.
     * Deliberately not user-scoped: the scheduler runs for everyone.
     */
    List<RecurringBill> findByActiveTrueAndNextRunDateLessThanEqual(LocalDate date);

    /** Every user's active bills. Backs the admin overview. */
    long countByActiveTrue();

    /** The full set for data-export, including inactive bills. */
    List<RecurringBill> findByUserId(UUID userId);

    /** Full wipe of one user's recurring bills, used by data-import. Cascades recurring_runs. */
    void deleteByUserId(UUID userId);
}
