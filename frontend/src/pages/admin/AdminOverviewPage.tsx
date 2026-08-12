import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { PageHeader } from '@/components/PageHeader'
import { Card, CardTitle, CountTile, StatTile } from '@/components/ui'
import { LazyDailyTrendChart, LazySignupsChart } from '@/components/LazyCharts'
import { useOverview } from '@/api/useAdmin'
import type { Section } from '@/api/useAdmin'

export function AdminOverviewPage() {
  const overview = useOverview()
  const data = overview.data

  return (
    <>
      <PageHeader title="Admin overview" subtitle="Across every account, not just yours." />

      <div className="grid gap-6">
        <SectionCard title="Users" section={data?.users} loading={overview.isLoading}>
          {(stats) => (
            <>
              {/* Four tiles is one more than every other stat grid in the app (which top out at
                  sm:grid-cols-3) — fine on real desktop widths, but the 15rem sidebar makes a
                  tablet's usable width closer to a phone's, so the jump to 4 columns waits for
                  lg rather than sm. */}
              <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
                <CountTile label="Total" value={stats.totalUsers} />
                <CountTile label="Verified" value={stats.verifiedUsers} />
                <CountTile label="Disabled" value={stats.disabledUsers} />
                <CountTile label="Admins" value={stats.adminUsers} />
              </div>
              <div className="mt-6">
                <p className="mb-3 text-sm font-medium text-ink">Signups, last 30 days</p>
                <LazySignupsChart data={stats.signupsLast30Days} />
              </div>
            </>
          )}
        </SectionCard>

        <SectionCard title="Ledger activity" section={data?.ledger} loading={overview.isLoading}>
          {(stats) => (
            <>
              <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
                <CountTile label="Transactions" value={stats.transactionCount} />
                <CountTile label="Active users" value={stats.activeUsers} />
                <StatTile label="Total income" value={stats.totalIncome} tone="income" />
                <StatTile label="Total expense" value={stats.totalExpense} tone="expense" />
              </div>
              <div className="mt-6">
                <p className="mb-3 text-sm font-medium text-ink">Income vs. expense, last 30 days</p>
                <LazyDailyTrendChart data={stats.dailyLast30Days} />
              </div>
            </>
          )}
        </SectionCard>

        <SectionCard title="Planning" section={data?.planning} loading={overview.isLoading}>
          {(stats) => (
            <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
              <CountTile label="Budget lines this month" value={stats.budgetLinesThisMonth} />
              <CountTile label="Active debts" value={stats.activeDebts} />
              <CountTile label="Active goals" value={stats.activeGoals} />
              <CountTile label="Active recurring bills" value={stats.activeRecurringBills} />
              <StatTile label="Owed by users" value={stats.totalOwedByUsers} tone="expense" />
              <StatTile label="Owed to users" value={stats.totalOwedToUsers} tone="income" />
              <StatTile label="Goal targets" value={stats.totalGoalTargets} />
              <StatTile label="Goal saved" value={stats.totalGoalSaved} tone="income" />
            </div>
          )}
        </SectionCard>

        <Card>
          <CardTitle
            action={
              <Link to="/admin/feedback" className="text-xs font-medium text-accent hover:underline">
                View all
              </Link>
            }
          >
            Feedback
          </CardTitle>
          {data ? (
            <div className="grid gap-4 sm:grid-cols-3">
              <CountTile label="New" value={data.feedback.newCount} />
              <CountTile label="Reviewing" value={data.feedback.reviewingCount} />
              <CountTile label="Resolved" value={data.feedback.resolvedCount} />
            </div>
          ) : (
            <p className="py-4 text-sm text-muted">Loading…</p>
          )}
        </Card>
      </div>
    </>
  )
}

/**
 * Renders a panel's data, or a plain explanation when that panel's service was unreachable —
 * the whole point of the fan-out degrading per-panel on the backend is that this page has
 * something honest to show for the panels that are still fine, rather than failing outright.
 */
function SectionCard<T>({
  title,
  section,
  loading,
  children,
}: {
  title: string
  section: Section<T> | undefined
  loading: boolean
  children: (data: T) => ReactNode
}) {
  return (
    <Card>
      <CardTitle>{title}</CardTitle>
      {loading ? (
        <p className="py-4 text-sm text-muted">Loading…</p>
      ) : !section?.available || section.data === null ? (
        <p className="py-4 text-sm text-warning">
          {section?.error ?? 'This section is unavailable right now.'}
        </p>
      ) : (
        children(section.data)
      )}
    </Card>
  )
}
