package ph.pesowise.admin.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ph.pesowise.admin.domain.AdminAuditEntry;

import java.util.UUID;

public interface AdminAuditRepository extends JpaRepository<AdminAuditEntry, UUID> {

    Page<AdminAuditEntry> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
