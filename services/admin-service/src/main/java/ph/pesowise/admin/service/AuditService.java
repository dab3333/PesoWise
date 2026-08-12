package ph.pesowise.admin.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.pesowise.admin.api.AuditDtos.AuditEntryResponse;
import ph.pesowise.admin.api.AuditDtos.AuditPage;
import ph.pesowise.admin.repo.AdminAuditRepository;

@Service
public class AuditService {

    private final AdminAuditRepository audit;

    public AuditService(AdminAuditRepository audit) {
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public AuditPage list(int page, int size) {
        var result = audit.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        return new AuditPage(
                result.getContent().stream().map(AuditEntryResponse::from).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }
}
