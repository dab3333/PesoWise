package ph.pesowise.planning.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ph.pesowise.planning.api.DataDtos.ImportSummary;
import ph.pesowise.planning.api.DataDtos.PlanningExport;
import ph.pesowise.planning.api.DataDtos.PlanningImportRequest;
import ph.pesowise.planning.service.DataExportService;

import java.util.UUID;

/**
 * Mapped at {@code /api/data/planning} — the gateway forwards the full path unchanged (no
 * StripPrefix filter), so this must match exactly what its route predicate in the gateway's
 * application.yml lists.
 */
@RestController
@RequestMapping("/api/data/planning")
public class DataExportController {

    private final DataExportService dataExportService;

    public DataExportController(DataExportService dataExportService) {
        this.dataExportService = dataExportService;
    }

    @GetMapping("/export")
    public PlanningExport export(@RequestHeader(Headers.USER_ID) UUID userId) {
        return dataExportService.export(userId);
    }

    @PostMapping("/import")
    public ImportSummary importData(
            @RequestHeader(Headers.USER_ID) UUID userId, @RequestBody PlanningImportRequest request) {
        return dataExportService.importAll(userId, request.data(), request.ledgerIds());
    }
}
