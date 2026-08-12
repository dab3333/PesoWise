package ph.pesowise.planning.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ph.pesowise.planning.domain.Budget;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    List<Budget> findByUserIdAndPeriodMonth(UUID userId, LocalDate periodMonth);

    /** The upsert lookup: a category has at most one budget per month. */
    Optional<Budget> findByUserIdAndCategoryIdAndPeriodMonth(
            UUID userId, UUID categoryId, LocalDate periodMonth);

    /** Scoped by user id so a guessed UUID cannot reach another user's budget. */
    Optional<Budget> findByIdAndUserId(UUID id, UUID userId);

    /** Powers "copy last month's budget" and tells the UI whether a prior month exists. */
    boolean existsByUserIdAndPeriodMonth(UUID userId, LocalDate periodMonth);

    /** Every user's budget lines for the given month. Backs the admin overview. */
    long countByPeriodMonth(LocalDate periodMonth);
}
