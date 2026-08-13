package ph.pesowise.planning.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ph.pesowise.planning.domain.Goal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

    /** Live goals first, then soonest target date. */
    List<Goal> findByUserIdOrderByArchivedAscTargetDateAscCreatedAtAsc(UUID userId);

    /** Scoped by user id so a guessed UUID cannot reach another user's goal. */
    Optional<Goal> findByIdAndUserId(UUID id, UUID userId);

    /** The full set for data-export, including archived goals. */
    List<Goal> findByUserId(UUID userId);

    /** Full wipe of one user's goals, used by data-import. Cascades goal_contributions. */
    void deleteByUserId(UUID userId);

    long countByArchivedFalse();

    /** Target total across every active goal, every user. Backs the admin overview. */
    @Query("SELECT COALESCE(SUM(g.targetAmount), 0) FROM Goal g WHERE g.archived = false")
    BigDecimal sumActiveTargets();
}
