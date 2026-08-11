package ph.pesowise.planning.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ph.pesowise.planning.domain.DebtPayment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DebtPaymentRepository extends JpaRepository<DebtPayment, UUID> {

    List<DebtPayment> findByDebtIdOrderByPaidOnDescCreatedAtDesc(UUID debtId);

    Optional<DebtPayment> findByIdAndUserId(UUID id, UUID userId);

    long countByDebtId(UUID debtId);
}
