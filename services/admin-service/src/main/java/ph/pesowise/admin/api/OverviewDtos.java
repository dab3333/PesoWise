package ph.pesowise.admin.api;

import ph.pesowise.admin.clients.AuthAdminDtos.UserStats;
import ph.pesowise.admin.clients.LedgerAdminDtos.LedgerStats;
import ph.pesowise.admin.clients.PlanningAdminDtos.PlanningStats;

public final class OverviewDtos {

    private OverviewDtos() {
    }

    /**
     * One panel of the overview. {@code available=false} means that service's call failed or
     * timed out — {@code data} is then null and {@code error} explains why, so the admin page can
     * render every other panel instead of failing the whole request over one down dependency.
     */
    public record Section<T>(boolean available, T data, String error) {
        public static <T> Section<T> ok(T data) {
            return new Section<>(true, data, null);
        }

        public static <T> Section<T> unavailable(String error) {
            return new Section<>(false, null, error);
        }
    }

    public record OverviewResponse(
            Section<UserStats> users,
            Section<LedgerStats> ledger,
            Section<PlanningStats> planning,
            FeedbackDtos.FeedbackCounts feedback) {
    }
}
