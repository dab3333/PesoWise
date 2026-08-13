package ph.pesowise.planning.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ph.pesowise.planning.domain.RecurringRun;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RecurringRunRepository extends JpaRepository<RecurringRun, UUID> {

    List<RecurringRun> findByBillIdOrderByDueDateDesc(UUID billId);

    /** Lets a caller check whether an occurrence has already been dealt with. */
    boolean existsByBillIdAndDueDate(UUID billId, LocalDate dueDate);

    long countByBillId(UUID billId);

    /** The full set for data-export — every run across all of a user's recurring bills. */
    List<RecurringRun> findByUserId(UUID userId);
}
