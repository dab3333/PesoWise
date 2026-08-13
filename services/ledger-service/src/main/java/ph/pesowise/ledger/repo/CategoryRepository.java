package ph.pesowise.ledger.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ph.pesowise.ledger.domain.Category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByUserIdAndArchivedFalseOrderByKindAscNameAsc(UUID userId);

    /** Includes archived categories — needed to label transactions that still reference them. */
    List<Category> findByUserId(UUID userId);

    /** Scoped by user id so a guessed UUID cannot reach another user's category. */
    Optional<Category> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndArchivedFalseAndNameIgnoreCase(UUID userId, String name);

    boolean existsByUserId(UUID userId);

    /** Full wipe of one user's categories, used by data-import to reset before reinserting. */
    void deleteByUserId(UUID userId);
}
