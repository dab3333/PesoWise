package ph.pesowise.planning.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ph.pesowise.planning.domain.DebtInterestAccrual;

import java.util.List;
import java.util.UUID;

public interface DebtInterestAccrualRepository extends JpaRepository<DebtInterestAccrual, UUID> {

    List<DebtInterestAccrual> findByDebtIdOrderByPeriodDesc(UUID debtId);

    /** The full set for data-export — every accrual across all of a user's debts. */
    List<DebtInterestAccrual> findByUserId(UUID userId);
}
