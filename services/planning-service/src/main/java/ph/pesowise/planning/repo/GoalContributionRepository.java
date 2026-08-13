package ph.pesowise.planning.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ph.pesowise.planning.domain.GoalContribution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoalContributionRepository extends JpaRepository<GoalContribution, UUID> {

    List<GoalContribution> findByGoalIdOrderByContributedOnDescCreatedAtDesc(UUID goalId);

    Optional<GoalContribution> findByIdAndUserId(UUID id, UUID userId);

    /** The full set for data-export — every contribution across all of a user's goals. */
    List<GoalContribution> findByUserId(UUID userId);

    /**
     * Saved total and contribution count for every one of a user's goals, in one grouped query. The
     * alternative — a SUM and a COUNT per goal — is two queries per row on a page that lists them
     * all.
     */
    @Query(value = """
            SELECT goal_id                   AS goalId,
                   COALESCE(SUM(amount), 0)  AS total,
                   COUNT(*)                  AS contributionCount
            FROM goal_contributions
            WHERE user_id = :userId
            GROUP BY goal_id
            """, nativeQuery = true)
    List<GoalSaved> findSavedTotalsByUserId(@Param("userId") UUID userId);

    /** Projection for the grouped saved-total query. */
    interface GoalSaved {
        UUID getGoalId();

        java.math.BigDecimal getTotal();

        int getContributionCount();
    }

    /** Every user's saved total, in one number. Backs the admin overview, not user-scoped. */
    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM GoalContribution c")
    java.math.BigDecimal sumAllContributions();
}
