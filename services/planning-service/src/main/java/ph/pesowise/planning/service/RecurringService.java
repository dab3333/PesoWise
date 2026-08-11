package ph.pesowise.planning.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.pesowise.planning.api.RecurringDtos.BillOverview;
import ph.pesowise.planning.api.RecurringDtos.BillRequest;
import ph.pesowise.planning.api.RecurringDtos.BillResponse;
import ph.pesowise.planning.api.RecurringDtos.RunResponse;
import ph.pesowise.planning.api.RecurringDtos.RunSummary;
import ph.pesowise.planning.domain.RecurringBill;
import ph.pesowise.planning.service.RecurringOccurrences.Result;
import ph.pesowise.planning.service.RecurringOccurrences.Settlement;
import ph.pesowise.planning.repo.RecurringBillRepository;
import ph.pesowise.planning.repo.RecurringRunRepository;
import ph.pesowise.planning.web.ConflictException;
import ph.pesowise.planning.web.NotFoundException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Recurring bills: the templates, and the pass that acts on them.
 *
 * <p>Each occurrence is settled by {@link RecurringOccurrences} in its own transaction, so one
 * failing bill — an archived category, an unreachable ledger — cannot roll back the bills already
 * handled. The idempotency guards live there too.
 */
@Service
public class RecurringService {

    private static final Logger log = LoggerFactory.getLogger(RecurringService.class);

    /**
     * How many missed occurrences one bill may catch up on in a single pass. A bill left unattended
     * for a year should not silently post twelve months of rent, and a corrupt cursor should not spin
     * forever. When the cap bites it is logged and returned in the summary, never silent.
     */
    static final int MAX_CATCH_UP = 12;

    private final RecurringBillRepository bills;
    private final RecurringRunRepository runs;
    private final RecurringOccurrences occurrences;

    public RecurringService(
            RecurringBillRepository bills, RecurringRunRepository runs, RecurringOccurrences occurrences) {
        this.bills = bills;
        this.runs = runs;
        this.occurrences = occurrences;
    }

    /* ------------------------------------------------------------------ reads */

    @Transactional(readOnly = true)
    public BillOverview list(UUID userId) {
        LocalDate today = LocalDate.now();
        List<BillResponse> all = new ArrayList<>();
        List<BillResponse> dueNow = new ArrayList<>();
        BigDecimal monthlyTotal = BigDecimal.ZERO;

        for (RecurringBill bill : bills.findByUserIdOrderByActiveDescNextRunDateAsc(userId)) {
            BillResponse response = toResponse(bill, today, runs.countByBillId(bill.getId()));
            all.add(response);
            if (response.dueNow()) dueNow.add(response);
            if (bill.isActive()) monthlyTotal = monthlyTotal.add(monthlyEquivalent(bill));
        }

        return new BillOverview(monthlyTotal, dueNow, all);
    }

    @Transactional(readOnly = true)
    public List<RunResponse> history(UUID userId, UUID billId) {
        require(userId, billId);
        return runs.findByBillIdOrderByDueDateDesc(billId).stream().map(RunResponse::from).toList();
    }

    /* ----------------------------------------------------------------- writes */

    @Transactional
    public BillResponse create(UUID userId, BillRequest request) {
        RecurringBill bill = bills.save(RecurringBill.create(
                userId, request.name().trim(), request.categoryId(), request.accountId(),
                request.amount(), request.frequency(), request.nextRunDate(),
                request.autoPost(), trimToNull(request.note())));

        return toResponse(bill, LocalDate.now(), 0);
    }

    @Transactional
    public BillResponse update(UUID userId, UUID billId, BillRequest request) {
        RecurringBill bill = require(userId, billId);

        bill.setName(request.name().trim());
        bill.setCategoryId(request.categoryId());
        bill.setAccountId(request.accountId());
        bill.setAmount(request.amount());
        bill.setAutoPost(request.autoPost());
        bill.setNote(trimToNull(request.note()));
        if (request.active() != null) bill.setActive(request.active());
        // Moving the date also re-anchors a monthly bill's day.
        if (!request.nextRunDate().equals(bill.getNextRunDate())) {
            bill.rescheduleTo(request.nextRunDate());
        }

        return toResponse(bill, LocalDate.now(), (int) runs.countByBillId(billId));
    }

    @Transactional
    public void delete(UUID userId, UUID billId) {
        // ON DELETE CASCADE removes the run history. Ledger transactions stay: the money moved.
        bills.delete(require(userId, billId));
    }

    /**
     * Records the current occurrence on the user's say-so — the path for bills whose amount varies,
     * where {@code autoPost} is off.
     */
    public RunResponse postNow(UUID userId, UUID billId) {
        RecurringBill bill = require(userId, billId);
        LocalDate today = LocalDate.now();

        if (!bill.isDueOn(today)) {
            throw new ConflictException(
                    "\"%s\" is not due until %s.".formatted(bill.getName(), bill.getNextRunDate()));
        }

        Result result = occurrences.settleDue(billId, today);
        return switch (result.settlement()) {
            case POSTED -> RunResponse.from(result.run());
            case ALREADY_RECORDED -> throw new ConflictException(
                    "That occurrence has already been recorded.");
            case NOT_DUE -> throw new ConflictException("That bill is no longer due.");
        };
    }

    /** Marks the current occurrence dealt with without posting anything. */
    public RunResponse skipNow(UUID userId, UUID billId) {
        require(userId, billId);

        Result result = occurrences.skipDue(billId, LocalDate.now());
        return switch (result.settlement()) {
            case POSTED -> RunResponse.from(result.run());
            case ALREADY_RECORDED -> throw new ConflictException(
                    "That occurrence has already been recorded.");
            case NOT_DUE -> throw new ConflictException("That bill is not due yet.");
        };
    }

    /* -------------------------------------------------------------- scheduler */

    /**
     * One pass over every user's due bills.
     *
     * <p>Deliberately not transactional: each occurrence commits on its own inside
     * {@link RecurringOccurrences}, so a failure part-way through keeps everything already done.
     */
    public RunSummary runDueBills() {
        LocalDate today = LocalDate.now();
        List<RecurringBill> due = bills.findByActiveTrueAndNextRunDateLessThanEqual(today);

        int posted = 0;
        int flagged = 0;
        int alreadyRecorded = 0;
        List<String> notes = new ArrayList<>();

        for (RecurringBill bill : due) {
            if (!bill.isAutoPost()) {
                // Left where it is, so the UI keeps showing it as due until the user acts.
                flagged++;
                continue;
            }

            // Missed occurrences are posted, not dropped: rent that was due really was due, and
            // quietly skipping it would understate the month.
            int settled = 0;
            try {
                for (int attempt = 0; attempt < MAX_CATCH_UP; attempt++) {
                    Result result = occurrences.settleDue(bill.getId(), today);
                    if (result.settlement() == Settlement.NOT_DUE) break;

                    if (result.settlement() == Settlement.POSTED) posted++;
                    else alreadyRecorded++;
                    settled++;
                }
            } catch (RuntimeException e) {
                // Contained on purpose. One bill pointing at an archived category, or a transient
                // ledger failure, must not stop the bills after it in the list — each occurrence has
                // already committed on its own, and this one is safe to retry on the next pass.
                String note = "%s: could not be posted (%s). It will be retried."
                        .formatted(bill.getName(), e.getClass().getSimpleName());
                log.error("Recurring bill {} failed during the pass", bill.getId(), e);
                notes.add(note);
                continue;
            }

            if (settled == MAX_CATCH_UP && stillDue(bill.getId(), today)) {
                String note = "%s: stopped after %d occurrences, more are still outstanding."
                        .formatted(bill.getName(), MAX_CATCH_UP);
                log.warn(note);
                notes.add(note);
            }
        }

        if (posted > 0 || flagged > 0 || alreadyRecorded > 0) {
            log.info("Recurring pass: {} posted, {} awaiting confirmation, {} already recorded",
                    posted, flagged, alreadyRecorded);
        }
        return new RunSummary(posted, flagged, alreadyRecorded, notes);
    }

    private boolean stillDue(UUID billId, LocalDate today) {
        return bills.findById(billId).filter(bill -> bill.isDueOn(today)).isPresent();
    }

    /* ------------------------------------------------------------------ utils */

    private RecurringBill require(UUID userId, UUID billId) {
        return bills.findByIdAndUserId(billId, userId)
                .orElseThrow(() -> new NotFoundException("Recurring bill"));
    }

    /**
     * What a bill costs per month, so weekly and yearly ones can be summed alongside monthly ones.
     * 52 weeks over 12 months, not 4 per month — the latter overstates weekly bills by about 8%.
     */
    private static BigDecimal monthlyEquivalent(RecurringBill bill) {
        return switch (bill.getFrequency()) {
            case MONTHLY -> bill.getAmount();
            case WEEKLY -> bill.getAmount()
                    .multiply(BigDecimal.valueOf(52))
                    .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            case YEARLY -> bill.getAmount().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        };
    }

    private static BillResponse toResponse(RecurringBill bill, LocalDate today, long postedCount) {
        return new BillResponse(
                bill.getId(), bill.getName(), bill.getCategoryId(), bill.getAccountId(),
                bill.getAmount(), bill.getFrequency(), bill.getDayOfPeriod(), bill.getNextRunDate(),
                ChronoUnit.DAYS.between(today, bill.getNextRunDate()),
                bill.isDueOn(today), bill.isAutoPost(), bill.isActive(),
                bill.getNote(), (int) postedCount);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
