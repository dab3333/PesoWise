package ph.pesowise.planning.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ph.pesowise.planning.domain.Debt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DebtRepository extends JpaRepository<Debt, UUID> {

    /** Active first, then by due date — what is urgent leads the list. */
    List<Debt> findByUserIdOrderByStatusAscDueDateAscCreatedAtAsc(UUID userId);

    /** Scoped by user id so a guessed UUID cannot reach another user's debt. */
    Optional<Debt> findByIdAndUserId(UUID id, UUID userId);
}
