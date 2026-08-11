package ph.pesowise.planning.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ph.pesowise.planning.domain.Goal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

    /** Live goals first, then soonest target date. */
    List<Goal> findByUserIdOrderByArchivedAscTargetDateAscCreatedAtAsc(UUID userId);

    /** Scoped by user id so a guessed UUID cannot reach another user's goal. */
    Optional<Goal> findByIdAndUserId(UUID id, UUID userId);
}
