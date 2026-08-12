import { Suspense, lazy } from 'react'
import type { CategoryTotal, DailyTotal } from '@/api/types'
import type { SignupDay } from '@/api/useAdmin'

/**
 * Recharts is ~400kB of the bundle and only the Dashboard needs it. Splitting it out keeps the
 * initial load small, which matters on the mobile connections this app is aimed at — the login
 * screen should not pay for a charting library.
 */
const SpendByCategoryChart = lazy(() =>
  import('./charts').then((module) => ({ default: module.SpendByCategoryChart })),
)

const DailyTrendChart = lazy(() =>
  import('./charts').then((module) => ({ default: module.DailyTrendChart })),
)

const SignupsChart = lazy(() =>
  import('./charts').then((module) => ({ default: module.SignupsChart })),
)

/** Reserves the chart's height so the card does not jump when the chunk arrives. */
function ChartFallback({ height }: { height: number }) {
  return (
    <div className="grid animate-pulse place-items-center" style={{ height }}>
      <span className="text-xs text-subtle">Loading chart…</span>
    </div>
  )
}

export function LazySpendByCategoryChart({ data }: { data: CategoryTotal[] }) {
  return (
    <Suspense fallback={<ChartFallback height={200} />}>
      <SpendByCategoryChart data={data} />
    </Suspense>
  )
}

export function LazyDailyTrendChart({ data }: { data: DailyTotal[] }) {
  return (
    <Suspense fallback={<ChartFallback height={240} />}>
      <DailyTrendChart data={data} />
    </Suspense>
  )
}

export function LazySignupsChart({ data }: { data: SignupDay[] }) {
  return (
    <Suspense fallback={<ChartFallback height={200} />}>
      <SignupsChart data={data} />
    </Suspense>
  )
}
