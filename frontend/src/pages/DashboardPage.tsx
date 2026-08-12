import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '@/auth/AuthContext'
import { useBudgetOverview } from '@/api/useBudgets'
import { useRecurringBills } from '@/api/useRecurring'
import { PageHeader } from '@/components/PageHeader'
import { MonthNav } from '@/components/MonthNav'
import { Meter } from '@/components/Meter'
import { Card, CardTitle, StatTile } from '@/components/ui'
import { LazyDailyTrendChart, LazySpendByCategoryChart } from '@/components/LazyCharts'
import {
  useAccounts,
  useBucketBreakdown,
  useDailyTotals,
  useSpendByCategory,
  useSummary,
} from '@/api/useLedger'
import { BUCKET_LABELS } from '@/api/types'
import { formatPeso, toMonthKey, toNumber } from '@/lib/format'

export function DashboardPage() {
  const { user } = useAuth()
  const [month, setMonth] = useState(() => toMonthKey(new Date()))

  // The range for by-category matches the selected month exactly.
  const monthStart = `${month}-01`
  const monthEnd = lastDayOf(month)

  const summary = useSummary(month)
  const byCategory = useSpendByCategory(monthStart, monthEnd)
  const buckets = useBucketBreakdown(month)
  const daily = useDailyTotals(month)
  const accounts = useAccounts()
  const budgets = useBudgetOverview(month)
  const recurring = useRecurringBills()

  const totalBalance = (accounts.data ?? []).reduce((sum, account) => sum + toNumber(account.balance), 0)

  return (
    <>
      <PageHeader
        title={`Kumusta, ${user?.displayName?.split(' ')[0] ?? 'there'}`}
        subtitle="Here's where your pesos went this month."
      />

      {/* One filter row above everything it scopes — never inside a chart card. */}
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <MonthNav month={month} onChange={setMonth} />
        <span className="tnum text-sm text-muted">
          Across all accounts:{' '}
          <span className="font-medium text-ink">{formatPeso(totalBalance)}</span>
        </span>
      </div>

      {/* KPI row — three numbers are stat tiles, not a three-bar chart. */}
      <div className="mb-6 grid gap-4 sm:grid-cols-3">
        <StatTile label="Money in" value={summary.data?.income ?? 0} tone="income" />
        <StatTile label="Money out" value={summary.data?.expense ?? 0} tone="expense" />
        <StatTile label="Net" value={summary.data?.net ?? 0} tone="net" />
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardTitle>Where it went</CardTitle>
          <Loadable loading={byCategory.isLoading}>
            <LazySpendByCategoryChart data={byCategory.data ?? []} />
          </Loadable>
        </Card>

        <Card>
          <CardTitle>Day by day</CardTitle>
          <Loadable loading={daily.isLoading}>
            <LazyDailyTrendChart data={daily.data ?? []} />
          </Loadable>
        </Card>

        {/* Only appears when something is actually due — an empty "upcoming bills" card is noise. */}
        {(recurring.data?.dueNow.length ?? 0) > 0 && (
          <Card className="lg:col-span-2">
            <CardTitle
              action={
                <Link to="/recurring" className="text-xs font-medium text-accent hover:underline">
                  Manage bills
                </Link>
              }
            >
              Bills due now
            </CardTitle>
            <ul className="divide-y divide-line">
              {(recurring.data?.dueNow ?? []).map((bill) => (
                <li key={bill.id} className="flex items-center gap-3 py-2.5">
                  <span className="min-w-0 flex-1 truncate text-sm text-ink">{bill.name}</span>
                  <span className={`shrink-0 text-xs ${bill.daysUntilDue < 0 ? 'text-expense' : 'text-muted'}`}>
                    {bill.daysUntilDue < 0
                      ? `${Math.abs(bill.daysUntilDue)} day(s) late`
                      : 'due today'}
                  </span>
                  <span className="tnum shrink-0 text-sm font-medium text-ink">
                    {formatPeso(bill.amount)}
                  </span>
                </li>
              ))}
            </ul>
          </Card>
        )}

        <Card className="lg:col-span-2">
          <CardTitle
            action={
              <Link to="/budgets" className="text-xs font-medium text-accent hover:underline">
                Manage budgets
              </Link>
            }
          >
            Budget progress
          </CardTitle>
          <Loadable loading={budgets.isLoading}>
            {(budgets.data?.budgeted.length ?? 0) === 0 ? (
              <p className="py-6 text-center text-sm text-muted">
                No budgets set for this month.{' '}
                <Link to="/budgets" className="font-medium text-accent hover:underline">
                  Set one up
                </Link>
                .
              </p>
            ) : (
              <>
                <div className="grid gap-5 sm:grid-cols-2">
                  {/* The worst-standing few — the page is a summary, not the Budgets page. */}
                  {(budgets.data?.budgeted ?? []).slice(0, 4).map((line) => (
                    <Meter
                      key={line.categoryId}
                      label={line.categoryName}
                      actual={line.spent}
                      target={line.limitAmount ?? 0}
                    />
                  ))}
                </div>
                {toNumber(budgets.data?.unbudgetedSpend ?? 0) > 0 && (
                  <p className="mt-4 text-xs text-warning">
                    {formatPeso(budgets.data?.unbudgetedSpend ?? 0)} spent outside any budget.
                  </p>
                )}
              </>
            )}
          </Loadable>
        </Card>

        <Card className="lg:col-span-2">
          <CardTitle
            action={<span className="text-xs text-muted">70% needs · 20% wants · 10% savings</span>}
          >
            The 70-20-10 split
          </CardTitle>
          <Loadable loading={buckets.isLoading}>
            {toNumber(summary.data?.income ?? 0) === 0 ? (
              <p className="py-6 text-center text-sm text-muted">
                Record this month's income to see how your spending compares to the 70-20-10 targets.
              </p>
            ) : (
              <div className="grid gap-5 sm:grid-cols-3">
                {(buckets.data ?? []).map((bucket) => (
                  <Meter
                    key={bucket.bucket}
                    label={BUCKET_LABELS[bucket.bucket]}
                    actual={bucket.actualAmount}
                    target={bucket.targetAmount}
                    caption={`${bucket.targetPercent}% target`}
                  />
                ))}
              </div>
            )}
          </Loadable>
        </Card>
      </div>
    </>
  )
}

/** Holds the previous render at reduced opacity rather than flashing a skeleton. */
function Loadable({ loading, children }: { loading: boolean; children: React.ReactNode }) {
  return <div className={loading ? 'opacity-50 transition-opacity' : 'transition-opacity'}>{children}</div>
}

/** @param month a YYYY-MM key */
function lastDayOf(month: string): string {
  const [year, monthNumber] = month.split('-').map(Number)
  // Day 0 of the next month is the last day of this one, leap years included.
  const date = new Date(year, monthNumber, 0)
  return `${month}-${String(date.getDate()).padStart(2, '0')}`
}
