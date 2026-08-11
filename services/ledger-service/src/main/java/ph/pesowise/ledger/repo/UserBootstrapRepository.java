package ph.pesowise.ledger.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ph.pesowise.ledger.domain.UserBootstrap;

import java.util.UUID;

public interface UserBootstrapRepository extends JpaRepository<UserBootstrap, UUID> {
}
