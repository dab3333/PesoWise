package ph.pesowise.planning.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ph.pesowise.planning.domain.DebtInterestAccrual;

import java.util.List;
import java.util.UUID;

public interface DebtInterestAccrualRepository extends JpaRepository<DebtInterestAccrual, UUID> {

    List<DebtInterestAccrual> findByDebtIdOrderByPeriodDesc(UUID debtId);
}
