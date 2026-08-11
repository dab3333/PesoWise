package ph.pesowise.ledger.web;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ph.pesowise.ledger.api.LedgerDtos.BucketBreakdownResponse;
import ph.pesowise.ledger.api.LedgerDtos.CategoryTotalResponse;
import ph.pesowise.ledger.api.LedgerDtos.DailyTotalResponse;
import ph.pesowise.ledger.api.LedgerDtos.SummaryResponse;
import ph.pesowise.ledger.service.ReportService;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Aggregates for the dashboard, and the source planning-service reads to work out how much of a
 * budget has been spent.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** @param month YYYY-MM, defaulting to the current month */
    @GetMapping("/summary")
    public SummaryResponse summary(
            @RequestHeader(Headers.USER_ID) UUID userId,
            @RequestParam(required = false) String month) {
        return reportService.summary(userId, monthOrNow(month));
    }

    /**
     * A date range rather than a month, because planning-service and the dashboard both need
     * arbitrary windows. Defaults to the current month.
     */
    @GetMapping("/by-category")
    public List<CategoryTotalResponse> byCategory(
            @RequestHeader(Headers.USER_ID) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        YearMonth thisMonth = YearMonth.now();
        return reportService.byCategory(
                userId,
                from != null ? from : thisMonth.atDay(1),
                to != null ? to : thisMonth.atEndOfMonth());
    }

    /** The 70-20-10 split: targets from income against what was actually spent. */
    @GetMapping("/by-bucket")
    public List<BucketBreakdownResponse> byBucket(
            @RequestHeader(Headers.USER_ID) UUID userId,
            @RequestParam(required = false) String month) {
        return reportService.byBucket(userId, monthOrNow(month));
    }

    @GetMapping("/daily")
    public List<DailyTotalResponse> daily(
            @RequestHeader(Headers.USER_ID) UUID userId,
            @RequestParam(required = false) String month) {
        return reportService.daily(userId, monthOrNow(month));
    }

    private static String monthOrNow(String month) {
        return month == null || month.isBlank() ? YearMonth.now().toString() : month;
    }
}
