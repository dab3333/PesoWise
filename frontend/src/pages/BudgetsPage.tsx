import { useState } from 'react'
import type { FormEvent } from 'react'
import { PageHeader } from '@/components/PageHeader'
import { MonthNav } from '@/components/MonthNav'
import { Meter } from '@/components/Meter'
import { ConfirmDialog, Modal } from '@/components/Modal'
import { MoneyInput } from '@/components/form'
import { Alert, Button, Card, CardTitle, EmptyState, QueryError } from '@/components/ui'
import {
  useBudgetOverview,
  useCopyPreviousMonth,
  useDeleteBudget,
  useSaveBudget,
  useSaveBudgets,
  useSuggestion,
  type BudgetLine,
  type Suggestion,
} from '@/api/useBudgets'
import { BUCKET_LABELS, type Bucket } from '@/api/types'
import { ApiError } from '@/lib/api'
import { formatMonth, formatPeso, toMonthKey, toNumber } from '@/lib/format'

/** 70-20-10 order, matching the suggester and every other bucket-ordered view in the app. */
const BUCKET_ORDER: Bucket[] = ['NEEDS', 'WANTS', 'SAVINGS']

/** Groups a bucket-tagged list into 70-20-10 order, dropping any bucket with nothing in it. */
function groupByBucket<T extends { bucket: Bucket | null }>(lines: T[]): Array<[Bucket, T[]]> {
  return BUCKET_ORDER.map((bucket): [Bucket, T[]] => [bucket, lines.filter((line) => line.bucket === bucket)])
    .filter(([, group]) => group.length > 0)
}

export function BudgetsPage() {
  const [month, setMonth] = useState(() => toMonthKey(new Date()))
  const [editing, setEditing] = useState<BudgetLine | null>(null)
  const [removing, setRemoving] = useState<BudgetLine | null>(null)
  const [suggesting, setSuggesting] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)

  const overview = useBudgetOverview(month)
  const deleteBudget = useDeleteBudget(month)
  const copyPrevious = useCopyPreviousMonth(month)

  const data = overview.data
  const hasBudgets = (data?.budgeted.length ?? 0) > 0

  return (
    <>
      <PageHeader
        title="Budgets"
        subtitle="What you meant to spend, against what you actually did."
        action={
          <Button onClick={() => setSuggesting(true)}>
            {hasBudgets ? 'Rebuild with 70-20-10' : 'Suggest a budget'}
          </Button>
        }
      />

      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <MonthNav
          month={month}
          onChange={(next) => {
            setMonth(next)
            setNotice(null)
          }}
        />
        {!hasBudgets && (
          <Button
            variant="secondary"
            loading={copyPrevious.isPending}
            onClick={() =>
              copyPrevious.mutate(undefined, {
                onSuccess: (result) => setNotice(`Copied ${result.copied} budgets forward.`),
                onError: (caught) =>
                  setNotice(
                    caught instanceof ApiError && caught.status === 404
                      ? 'There is no budget from last month to copy.'
                      : 'Could not copy last month’s budget.',
                  ),
              })
            }
          >
            Copy last month
          </Button>
        )}
      </div>

      {notice && (
        <div className="mb-4">
          <Alert>{notice}</Alert>
        </div>
      )}

      {/* Totals row: three stat tiles, not a chart of three numbers. */}
      {hasBudgets && data && (
        <div className="mb-6 grid gap-4 sm:grid-cols-3">
          <Tile label="Budgeted" value={data.totalLimit} />
          <Tile label="Spent" value={data.totalSpent} />
          <Tile
            label="Left to spend"
            value={data.totalRemaining}
            tone={toNumber(data.totalRemaining) < 0 ? 'bad' : 'good'}
          />
        </div>
      )}

      <div className={overview.isFetching ? 'grid gap-6 opacity-50 transition-opacity' : 'grid gap-6'}>
        <Card>
          <CardTitle
            action={
              data && toNumber(data.income) > 0 ? (
                <span className="text-xs text-muted">
                  Income this month {formatPeso(data.income)}
                </span>
              ) : undefined
            }
          >
            This month’s limits
          </CardTitle>

          {overview.isError ? (
            <QueryError error={overview.error} />
          ) : !hasBudgets && !overview.isLoading ? (
            <EmptyState
              message={`No budgets set for ${formatMonth(month)} yet.`}
              action={<Button onClick={() => setSuggesting(true)}>Suggest a budget</Button>}
            />
          ) : (
            <div className="flex flex-col gap-5">
              {groupByBucket(data?.budgeted ?? []).map(([bucket, lines]) => (
                <div key={bucket}>
                  <p className="mb-3 text-xs font-medium uppercase tracking-wide text-muted">
                    {BUCKET_LABELS[bucket]}
                  </p>
                  <div className="grid gap-5 sm:grid-cols-2">
                    {lines.map((line) => (
                      <div key={line.categoryId} className="group">
                        <Meter
                          label={line.categoryName}
                          actual={line.spent}
                          target={line.limitAmount ?? 0}
                          caption={
                            line.remaining !== null && toNumber(line.remaining) >= 0
                              ? `${formatPeso(line.remaining)} left`
                              : `${formatPeso(Math.abs(toNumber(line.remaining ?? 0)))} over`
                          }
                        />
                        {/* Hover-reveal only makes sense where hover exists — on touch there is
                            no hover event, so opacity-0 with no override left these clickable but
                            invisible. Visible by default below sm; hover-gated only at desktop
                            widths. */}
                        <div className="mt-1 flex gap-1 opacity-100 transition-opacity sm:opacity-0 sm:group-hover:opacity-100 sm:focus-within:opacity-100">
                          <Button
                            variant="ghost"
                            className="px-2 py-1 text-xs"
                            onClick={() => setEditing(line)}
                          >
                            Change limit
                          </Button>
                          <Button
                            variant="ghost"
                            className="px-2 py-1 text-xs"
                            onClick={() => setRemoving(line)}
                          >
                            Remove
                          </Button>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>

        {/* The number that quietly breaks a budget if it is not shown. */}
        {(data?.unbudgeted.length ?? 0) > 0 && (
          <Card>
            <CardTitle
              action={
                <span className="tnum text-sm font-medium text-warning">
                  {formatPeso(data?.unbudgetedSpend ?? 0)}
                </span>
              }
            >
              Spent outside any budget
            </CardTitle>
            <ul className="divide-y divide-line">
              {(data?.unbudgeted ?? []).map((line) => (
                <li key={line.categoryId} className="flex items-center gap-3 py-2.5">
                  <span
                    aria-hidden
                    className="size-2.5 shrink-0 rounded-full"
                    style={{ background: line.color }}
                  />
                  <span className="min-w-0 flex-1 truncate text-sm text-ink">
                    {line.categoryName}
                    {line.bucket && (
                      <span className="ml-2 text-xs text-muted">{BUCKET_LABELS[line.bucket]}</span>
                    )}
                  </span>
                  <span className="tnum shrink-0 text-sm font-medium text-ink">
                    {formatPeso(line.spent)}
                  </span>
                  <Button
                    variant="ghost"
                    className="shrink-0 px-2 py-1 text-xs"
                    onClick={() =>
                      setEditing({ ...line, limitAmount: line.spent })
                    }
                  >
                    Set a limit
                  </Button>
                </li>
              ))}
            </ul>
          </Card>
        )}
      </div>

      {editing && (
        <LimitDialog month={month} line={editing} onClose={() => setEditing(null)} />
      )}

      {suggesting && (
        <SuggestionDialog month={month} onClose={() => setSuggesting(false)} />
      )}

      <ConfirmDialog
        open={removing !== null}
        onClose={() => setRemoving(null)}
        onConfirm={() =>
          removing &&
          deleteBudget.mutate(removing.categoryId, { onSuccess: () => setRemoving(null) })
        }
        loading={deleteBudget.isPending}
        confirmLabel="Remove"
        title="Remove this limit?"
        message={`The budget for "${removing?.categoryName}" will be removed. Your transactions are not affected — the spending will simply move to "Spent outside any budget".`}
      />
    </>
  )
}

function Tile({
  label,
  value,
  tone,
}: {
  label: string
  value: import('@/api/types').Money
  tone?: 'good' | 'bad'
}) {
  const color = tone === 'bad' ? 'text-expense' : tone === 'good' ? 'text-income' : 'text-ink'
  return (
    <div className="rounded-xl border border-line bg-surface p-5">
      <p className="text-xs font-medium uppercase tracking-wide text-muted">{label}</p>
      <p className={`mt-2 text-2xl font-semibold ${color}`}>{formatPeso(value)}</p>
    </div>
  )
}

function LimitDialog({
  month,
  line,
  onClose,
}: {
  month: string
  line: BudgetLine
  onClose: () => void
}) {
  const save = useSaveBudget(month)
  const [amount, setAmount] = useState(() =>
    line.limitAmount === null ? '' : String(line.limitAmount),
  )
  const [error, setError] = useState<string | null>(null)

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    save.mutate(
      { categoryId: line.categoryId, limitAmount: amount },
      {
        onSuccess: onClose,
        onError: (caught) =>
          setError(
            caught instanceof ApiError
              ? (caught.fieldErrors.limitAmount ?? caught.message)
              : 'Could not save. Check your connection and try again.',
          ),
      },
    )
  }

  return (
    <Modal open onClose={onClose} title={`Budget for ${line.categoryName}`}>
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        {error && <Alert>{error}</Alert>}
        <p className="text-sm text-muted">
          Already spent {formatPeso(line.spent)} this month.
        </p>
        <MoneyInput
          label="Monthly limit"
          value={amount}
          onChange={setAmount}
          required
        />
        <div className="mt-1 flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            Save limit
          </Button>
        </div>
      </form>
    </Modal>
  )
}

/**
 * Two steps: ask for the expected income, then show the proposed limits for review. The
 * suggestion is never applied silently — the user sees every line and can back out.
 */
function SuggestionDialog({ month, onClose }: { month: string; onClose: () => void }) {
  const suggest = useSuggestion(month)
  const saveAll = useSaveBudgets(month)

  const [income, setIncome] = useState('')
  const [preview, setPreview] = useState<Suggestion | null>(null)
  const [error, setError] = useState<string | null>(null)

  function requestSuggestion(event: FormEvent) {
    event.preventDefault()
    setError(null)
    suggest.mutate(income || undefined, {
      onSuccess: setPreview,
      onError: (caught) =>
        setError(
          caught instanceof ApiError
            ? (caught.fieldErrors.expectedIncome ?? caught.message)
            : 'Could not build a suggestion. Check your connection and try again.',
        ),
    })
  }

  function apply() {
    if (!preview) return
    setError(null)
    saveAll.mutate(
      preview.lines.map((line) => ({
        categoryId: line.categoryId,
        limitAmount: String(line.limitAmount),
      })),
      {
        onSuccess: onClose,
        onError: (caught) =>
          setError(caught instanceof ApiError ? caught.message : 'Could not save the budget.'),
      },
    )
  }

  return (
    <Modal open onClose={onClose} title="Suggest a budget">
      {preview === null ? (
        <form onSubmit={requestSuggestion} className="flex flex-col gap-4" noValidate>
          {error && <Alert>{error}</Alert>}
          <p className="text-sm text-muted">
            The 70-20-10 method puts 70% of your income toward needs, 20% toward wants, and 10%
            toward savings. Each bucket is then split across your categories in proportion to what
            you actually spent over the last three months.
          </p>
          <MoneyInput
            label="Expected monthly income"
            value={income}
            onChange={setIncome}
          />
          <p className="-mt-2 text-xs text-muted">
            Leave this blank to use last month’s actual income.
          </p>
          <div className="mt-1 flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" loading={suggest.isPending}>
              Build suggestion
            </Button>
          </div>
        </form>
      ) : (
        <div className="flex flex-col gap-4">
          {error && <Alert>{error}</Alert>}

          <p className="text-sm text-muted">
            Based on {formatPeso(preview.expectedIncome)}
            {preview.incomeWasEstimated && ' (last month’s income)'} for{' '}
            {formatMonth(preview.month)}.
          </p>

          <div className="grid grid-cols-3 gap-2">
            {preview.buckets.map((bucket) => (
              <div key={bucket.bucket} className="rounded-lg border border-line px-3 py-2">
                <p className="text-xs text-muted">
                  {BUCKET_LABELS[bucket.bucket]} · {bucket.targetPercent}%
                </p>
                <p className="tnum text-sm font-medium text-ink">{formatPeso(bucket.amount)}</p>
              </div>
            ))}
          </div>

          <ul className="max-h-64 divide-y divide-line overflow-auto rounded-lg border border-line">
            {groupByBucket(preview.lines).map(([bucket, lines]) => (
              <li key={bucket}>
                <p className="bg-surface-muted px-3 py-1.5 text-xs font-medium uppercase tracking-wide text-muted">
                  {BUCKET_LABELS[bucket]}
                </p>
                <ul className="divide-y divide-line">
                  {lines.map((line) => (
                    <li key={line.categoryId} className="flex items-center gap-2 px-3 py-2 text-sm">
                      <span
                        aria-hidden
                        className="size-2.5 shrink-0 rounded-full"
                        style={{ background: line.color }}
                      />
                      <span className="min-w-0 flex-1 truncate text-ink">{line.categoryName}</span>
                      {/* Says why the number is what it is, so the suggestion is not a black box. */}
                      {!line.fromHistory && (
                        <span className="shrink-0 text-xs text-subtle">even split</span>
                      )}
                      <span className="tnum shrink-0 font-medium text-ink">
                        {formatPeso(line.limitAmount)}
                      </span>
                    </li>
                  ))}
                </ul>
              </li>
            ))}
          </ul>

          <p className="text-xs text-muted">
            Applying this replaces the limits for these categories. You can adjust any of them
            afterwards.
          </p>

          <div className="flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={() => setPreview(null)}>
              Back
            </Button>
            <Button onClick={apply} loading={saveAll.isPending}>
              Apply {preview.lines.length} limits
            </Button>
          </div>
        </div>
      )}
    </Modal>
  )
}
