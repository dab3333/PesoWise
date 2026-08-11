package ph.pesowise.planning.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.pesowise.planning.api.GoalDtos.ContributionRequest;
import ph.pesowise.planning.api.GoalDtos.ContributionResponse;
import ph.pesowise.planning.api.GoalDtos.GoalOverview;
import ph.pesowise.planning.api.GoalDtos.GoalRequest;
import ph.pesowise.planning.api.GoalDtos.GoalResponse;
import ph.pesowise.planning.domain.Goal;
import ph.pesowise.planning.domain.GoalContribution;
import ph.pesowise.planning.ledger.LedgerDtos.SourceType;
import ph.pesowise.planning.ledger.LedgerWriter;
import ph.pesowise.planning.repo.GoalContributionRepository;
import ph.pesowise.planning.repo.GoalContributionRepository.GoalSaved;
import ph.pesowise.planning.repo.GoalRepository;
import ph.pesowise.planning.web.NotFoundException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Savings goals, following the same contribution-to-ledger pattern as debt payments — see
 * {@link LedgerWriter} for the shared ordering rule.
 *
 * <p>Nothing here stores a running total: the amount saved is {@code SUM(contributions)}, read in one
 * grouped query. Unlike a debt balance there is no invariant to guard, so a stored total would be
 * duplication that can drift.
 */
@Service
public class GoalService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final GoalRepository goals;
    private final GoalContributionRepository contributions;
    private final LedgerWriter ledger;

    public GoalService(
            GoalRepository goals, GoalContributionRepository contributions, LedgerWriter ledger) {
        this.goals = goals;
        this.contributions = contributions;
        this.ledger = ledger;
    }

    @Transactional(readOnly = true)
    public GoalOverview list(UUID userId) {
        List<Goal> found = goals.findByUserIdOrderByArchivedAscTargetDateAscCreatedAtAsc(userId);

        // One grouped query for every goal's total and count, rather than two queries per row.
        Map<UUID, GoalSaved> totals = new HashMap<>();
        for (GoalSaved row : contributions.findSavedTotalsByUserId(userId)) {
            totals.put(row.getGoalId(), row);
        }

        BigDecimal totalTarget = BigDecimal.ZERO;
        BigDecimal totalSaved = BigDecimal.ZERO;
        int active = 0;
        int achieved = 0;
        List<GoalResponse> responses = new ArrayList<>(found.size());

        for (Goal goal : found) {
            GoalSaved row = totals.get(goal.getId());
            GoalResponse response = toResponse(
                    goal,
                    row == null ? BigDecimal.ZERO : row.getTotal(),
                    row == null ? 0 : row.getContributionCount());
            responses.add(response);

            // Archived goals are excluded from the headline totals; they are history, not plans.
            if (!goal.isArchived()) {
                totalTarget = totalTarget.add(response.targetAmount());
                totalSaved = totalSaved.add(response.savedAmount());
                if (response.achieved()) achieved++;
                else active++;
            }
        }

        return new GoalOverview(totalTarget, totalSaved, active, achieved, responses);
    }

    @Transactional
    public GoalResponse create(UUID userId, GoalRequest request) {
        Goal goal = goals.save(Goal.create(
                userId, request.name().trim(), request.targetAmount(),
                request.targetDate(), trimToNull(request.note())));

        return toResponse(goal, BigDecimal.ZERO, 0);
    }

    /**
     * The target amount is editable, unlike a debt's principal. Revising what you are saving for is
     * normal and invalidates nothing — every contribution stays exactly as recorded.
     */
    @Transactional
    public GoalResponse update(UUID userId, UUID goalId, GoalRequest request) {
        Goal goal = require(userId, goalId);

        goal.setName(request.name().trim());
        goal.setTargetAmount(request.targetAmount());
        goal.setTargetDate(request.targetDate());
        goal.setNote(trimToNull(request.note()));
        if (request.archived() != null) goal.setArchived(request.archived());

        GoalSaved row = savedFor(userId, goalId);
        return toResponse(
                goal,
                row == null ? BigDecimal.ZERO : row.getTotal(),
                row == null ? 0 : row.getContributionCount());
    }

    /**
     * Deletes the goal and its contribution records. As with debts, the ledger transactions those
     * contributions created are kept: the money really did move.
     */
    @Transactional
    public void delete(UUID userId, UUID goalId) {
        goals.delete(require(userId, goalId));
    }

    @Transactional(readOnly = true)
    public List<ContributionResponse> contributions(UUID userId, UUID goalId) {
        require(userId, goalId);
        return contributions.findByGoalIdOrderByContributedOnDescCreatedAtDesc(goalId).stream()
                .map(ContributionResponse::from)
                .toList();
    }

    /**
     * Records a contribution: stores it here and writes the cash movement to the ledger, so money
     * put into savings shows up in spending reports and against the 70-20-10 savings bucket.
     *
     * <p>Deliberately <em>not</em> capped at the remaining amount. Over-saving is a good outcome, so
     * rejecting it — as an overpayment on a debt is rejected — would be wrong here.
     */
    @Transactional
    public ContributionResponse contribute(UUID userId, UUID goalId, ContributionRequest request) {
        Goal goal = require(userId, goalId);

        UUID ledgerTxnId = ledger.post(
                userId, SourceType.GOAL_CONTRIBUTION, goal.getId(),
                request.accountId(), request.categoryId(), request.amount(), request.contributedOn(),
                noteFor(goal, request));

        GoalContribution contribution = contributions.save(GoalContribution.create(
                userId, goal.getId(), request.amount(), request.contributedOn(),
                trimToNull(request.note()), ledgerTxnId));

        return ContributionResponse.from(contribution);
    }

    /**
     * Undo: removes the contribution and the ledger transaction it created. Leaving the transaction
     * behind would make the two records disagree.
     */
    @Transactional
    public void deleteContribution(UUID userId, UUID goalId, UUID contributionId) {
        require(userId, goalId);

        GoalContribution contribution = contributions.findByIdAndUserId(contributionId, userId)
                .filter(found -> found.getGoalId().equals(goalId))
                .orElseThrow(() -> new NotFoundException("Contribution"));

        ledger.remove(userId, contribution.getLedgerTxnId());
        contributions.delete(contribution);
    }

    private Goal require(UUID userId, UUID goalId) {
        return goals.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new NotFoundException("Goal"));
    }

    /** Single-goal total for the update response, where there is only one row to look up. */
    private GoalSaved savedFor(UUID userId, UUID goalId) {
        return contributions.findSavedTotalsByUserId(userId).stream()
                .filter(row -> row.getGoalId().equals(goalId))
                .findFirst()
                .orElse(null);
    }

    /** Gives the ledger transaction a note that explains itself in the transaction list. */
    private static String noteFor(Goal goal, ContributionRequest request) {
        String own = trimToNull(request.note());
        String label = "Saved toward " + goal.getName();
        return own == null ? label : label + " — " + own;
    }

    static GoalResponse toResponse(Goal goal, BigDecimal saved, int contributionCount) {
        BigDecimal target = goal.getTargetAmount();
        boolean achieved = saved.compareTo(target) >= 0;

        // Never negative: over-saving is not a shortfall, and "₱0 to go" is the useful reading.
        BigDecimal remaining = achieved ? BigDecimal.ZERO : target.subtract(saved);

        BigDecimal percent = target.signum() == 0
                ? BigDecimal.ZERO
                : saved.multiply(HUNDRED).divide(target, 1, RoundingMode.HALF_UP);

        LocalDate today = LocalDate.now();
        Long daysUntilTarget = goal.getTargetDate() == null
                ? null
                : ChronoUnit.DAYS.between(today, goal.getTargetDate());

        return new GoalResponse(
                goal.getId(), goal.getName(), target, saved, remaining, percent,
                goal.getTargetDate(), daysUntilTarget,
                monthlyNeeded(remaining, goal.getTargetDate(), today, achieved),
                achieved,
                // An achieved goal is never behind schedule, however long ago the date was.
                daysUntilTarget != null && daysUntilTarget < 0 && !achieved,
                goal.isArchived(), goal.getNote(), contributionCount);
    }

    /**
     * What must be set aside each remaining month to land on the target date — the number that turns
     * a goal into a plan.
     *
     * <p>Counts calendar months inclusive of the current one, so a target at the end of this month
     * asks for the whole remaining amount rather than dividing by zero. Once the date has passed
     * there is no schedule left to spread over, so this returns the full shortfall.
     */
    private static BigDecimal monthlyNeeded(
            BigDecimal remaining, LocalDate targetDate, LocalDate today, boolean achieved) {
        if (targetDate == null || achieved) return null;

        long months = ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(targetDate)) + 1;
        if (months <= 1) return remaining;

        return remaining.divide(BigDecimal.valueOf(months), 2, RoundingMode.CEILING);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
