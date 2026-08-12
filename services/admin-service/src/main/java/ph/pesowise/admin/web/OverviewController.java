package ph.pesowise.admin.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ph.pesowise.admin.api.OverviewDtos.OverviewResponse;
import ph.pesowise.admin.service.OverviewService;

@RestController
@RequestMapping("/api/admin/overview")
public class OverviewController {

    private final OverviewService overviewService;

    public OverviewController(OverviewService overviewService) {
        this.overviewService = overviewService;
    }

    @GetMapping
    public OverviewResponse overview() {
        return overviewService.fetch();
    }
}
