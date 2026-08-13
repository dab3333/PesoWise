import { useState } from 'react'
import type { FormEvent } from 'react'
import { PageHeader } from '@/components/PageHeader'
import { ConfirmDialog, Modal } from '@/components/Modal'
import { DateField, MoneyInput, Select } from '@/components/form'
import { Alert, Button, Card, CardTitle, EmptyState, Field, QueryError } from '@/components/ui'
import { useAccounts, useCategories } from '@/api/useLedger'
import {
  useContribute,
  useContributions,
  useDeleteContribution,
  useDeleteGoal,
  useGoals,
  useSaveGoal,
  type Goal,
  type GoalInput,
} from '@/api/useGoals'
import type { Money } from '@/api/types'
import { ApiError } from '@/lib/api'
import { formatDate, formatPeso, toDateKey, toNumber } from '@/lib/format'

export function GoalsPage() {
  const goals = useGoals()
  const deleteGoal = useDeleteGoal()

  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<Goal | null>(null)
  const [contributing, setContributing] = useState<Goal | null>(null)
  const [viewing, setViewing] = useState<Goal | null>(null)
  const [removing, setRemoving] = useState<Goal | null>(null)

  const data = goals.data
  const live = (data?.goals ?? []).filter((goal) => !goal.archived)
  const archived = (data?.goals ?? []).filter((goal) => goal.archived)

  return (
    <>
      <PageHeader
        title="Goals"
        subtitle="What you're saving for, and whether you're on track."
        action={<Button onClick={() => setCreating(true)}>Add a goal</Button>}
      />

      {live.length > 0 && (
        <div className="mb-6 grid gap-4 sm:grid-cols-3">
          <Tile label="Saved so far" value={data?.totalSaved ?? 0} tone="good" />
          <Tile label="Total targets" value={data?.totalTarget ?? 0} />
          <div className="rounded-xl border border-line bg-surface p-5">
            <p className="text-xs font-medium uppercase tracking-wide text-muted">Goals</p>
            <p className="mt-2 text-2xl font-semibold text-ink">
              {data?.achievedCount ?? 0}
              <span className="text-base font-normal text-muted">
                {' '}
                of {(data?.activeCount ?? 0) + (data?.achievedCount ?? 0)} reached
              </span>
            </p>
          </div>
        </div>
      )}

      <div className={goals.isFetching ? 'grid gap-6 opacity-50 transition-opacity' : 'grid gap-6'}>
        <Card>
          <CardTitle>In progress</CardTitle>
          {goals.isError ? (
            <QueryError error={goals.error} />
          ) : live.length === 0 && !goals.isLoading ? (
            <EmptyState
              message="No goals yet. Saving for something specific makes it far likelier to happen."
              action={<Button onClick={() => setCreating(true)}>Add your first goal</Button>}
            />
          ) : (
            <ul className="divide-y divide-line">
              {live.map((goal) => (
                <GoalRow
                  key={goal.id}
                  goal={goal}
                  onContribute={() => setContributing(goal)}
                  onEdit={() => setEditing(goal)}
                  onHistory={() => setViewing(goal)}
                  onRemove={() => setRemoving(goal)}
                />
              ))}
            </ul>
          )}
        </Card>

        {archived.length > 0 && (
          <Card>
            <CardTitle>Archived</CardTitle>
            <ul className="divide-y divide-line">
              {archived.map((goal) => (
                <GoalRow
                  key={goal.id}
                  goal={goal}
                  onEdit={() => setEditing(goal)}
                  onHistory={() => setViewing(goal)}
                  onRemove={() => setRemoving(goal)}
                />
              ))}
            </ul>
          </Card>
        )}
      </div>

      {(creating || editing) && (
        <GoalDialog
          goal={editing}
          onClose={() => {
            setCreating(false)
            setEditing(null)
          }}
        />
      )}

      {contributing && (
        <ContributionDialog goal={contributing} onClose={() => setContributing(null)} />
      )}
      {viewing && <HistoryDialog goal={viewing} onClose={() => setViewing(null)} />}

      <ConfirmDialog
        open={removing !== null}
        onClose={() => setRemoving(null)}
        onConfirm={() =>
          removing && deleteGoal.mutate(removing.id, { onSuccess: () => setRemoving(null) })
        }
        loading={deleteGoal.isPending}
        confirmLabel="Delete"
        title="Delete this goal?"
        message={`"${removing?.name}" and its ${removing?.contributionCount ?? 0} contribution record(s) will be deleted. The transactions those contributions created stay in your ledger, because the money really did move. To keep the history instead, archive the goal.`}
      />
    </>
  )
}

function Tile({ label, value, tone }: { label: string; value: Money; tone?: 'good' }) {
  return (
    <div className="rounded-xl border border-line bg-surface p-5">
      <p className="text-xs font-medium uppercase tracking-wide text-muted">{label}</p>
      <p className={`mt-2 text-2xl font-semibold ${tone === 'good' ? 'text-income' : 'text-ink'}`}>
        {formatPeso(value)}
      </p>
    </div>
  )
}

function GoalRow({
  goal,
  onContribute,
  onEdit,
  onHistory,
  onRemove,
}: {
  goal: Goal
  onContribute?: () => void
  onEdit: () => void
  onHistory: () => void
  onRemove: () => void
}) {
  const percent = Math.min(toNumber(goal.percentComplete), 100)

  return (
    <li className="py-4">
      <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
        <div className="min-w-0">
          <p className="truncate text-sm font-medium text-ink">
            {goal.name}
            {goal.achieved && <span className="ml-2 text-xs font-normal text-income">Reached</span>}
          </p>
          <p className="mt-0.5 text-xs text-muted">
            {formatPeso(goal.savedAmount)} of {formatPeso(goal.targetAmount)}
            {goal.note && <span> · {goal.note}</span>}
          </p>
        </div>
        <p className="tnum shrink-0 text-sm font-medium text-ink">
          {goal.achieved ? (
            <span className="text-income">Complete</span>
          ) : (
            <>{formatPeso(goal.remaining)} to go</>
          )}
        </p>
      </div>

      <div className="mt-2 h-2 overflow-hidden rounded-full bg-surface-muted">
        <div
          style={{ width: `${percent}%` }}
          className={`h-full rounded-full ${goal.achieved ? 'bg-income' : 'bg-accent'}`}
        />
      </div>

      <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1">
        {/* The number that turns a goal into a plan. */}
        {goal.monthlyNeeded !== null && !goal.achieved && (
          <span className={`text-xs ${goal.behindSchedule ? 'font-medium text-expense' : 'text-muted'}`}>
            {goal.behindSchedule
              ? `Target date passed — ${formatPeso(goal.remaining)} still to go`
              : `Set aside ${formatPeso(goal.monthlyNeeded)}/month to hit ${formatDate(goal.targetDate!)}`}
          </span>
        )}
        {goal.targetDate && goal.achieved && (
          <span className="text-xs text-muted">Target was {formatDate(goal.targetDate)}</span>
        )}

        {/* flex-wrap: buttons drop to a second row as a whole rather than a label wrapping
            mid-word at a phone width. */}
        <span className="ml-auto flex flex-wrap gap-1">
          {onContribute && (
            <Button onClick={onContribute} className="px-3 py-1 text-xs">
              Add savings
            </Button>
          )}
          <Button variant="ghost" onClick={onHistory} className="px-2 py-1 text-xs">
            History ({goal.contributionCount})
          </Button>
          <Button variant="ghost" onClick={onEdit} className="px-2 py-1 text-xs">
            Edit
          </Button>
          <Button variant="ghost" onClick={onRemove} className="px-2 py-1 text-xs">
            Delete
          </Button>
        </span>
      </div>
    </li>
  )
}

function GoalDialog({ goal, onClose }: { goal: Goal | null; onClose: () => void }) {
  const save = useSaveGoal()
  const [form, setForm] = useState<GoalInput>(() => ({
    name: goal?.name ?? '',
    targetAmount: goal ? String(goal.targetAmount) : '',
    targetDate: goal?.targetDate ?? '',
    note: goal?.note ?? '',
    archived: goal?.archived ?? false,
  }))
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})

    save.mutate(
      { id: goal?.id, input: { ...form, targetDate: form.targetDate ? form.targetDate : null } },
      {
        onSuccess: onClose,
        onError: (caught) => {
          if (caught instanceof ApiError) {
            setFieldErrors(caught.fieldErrors)
            setError(Object.keys(caught.fieldErrors).length > 0 ? null : caught.message)
          } else {
            setError('Could not save. Check your connection and try again.')
          }
        },
      },
    )
  }

  return (
    <Modal open onClose={onClose} title={goal ? 'Edit goal' : 'Add a goal'}>
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        {error && <Alert>{error}</Alert>}

        <Field
          label="What are you saving for?"
          placeholder="Bagong laptop, emergency fund, Japan trip…"
          value={form.name}
          onChange={(event) => setForm({ ...form, name: event.target.value })}
          error={fieldErrors.name}
          required
        />

        <MoneyInput
          label="Target amount"
          value={form.targetAmount}
          onChange={(targetAmount) => setForm({ ...form, targetAmount })}
          error={fieldErrors.targetAmount}
          required
        />
        {goal !== null && (
          <p className="-mt-2 text-xs text-muted">
            Changing the target keeps every contribution exactly as recorded.
          </p>
        )}

        <DateField
          label="Target date"
          value={form.targetDate ?? ''}
          onChange={(event) => setForm({ ...form, targetDate: event.target.value })}
          error={fieldErrors.targetDate}
          hint="Optional — adding one lets PesoWise work out a monthly amount."
        />

        <Field
          label="Note"
          placeholder="Optional"
          value={form.note}
          onChange={(event) => setForm({ ...form, note: event.target.value })}
          error={fieldErrors.note}
        />

        {goal !== null && (
          <Select
            label="Status"
            value={form.archived ? 'archived' : 'active'}
            onChange={(event) => setForm({ ...form, archived: event.target.value === 'archived' })}
          >
            <option value="active">Active</option>
            <option value="archived">Archived — hide it, keep the history</option>
          </Select>
        )}

        <div className="mt-1 flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            {goal ? 'Save changes' : 'Add goal'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

function ContributionDialog({ goal, onClose }: { goal: Goal; onClose: () => void }) {
  const accounts = useAccounts()
  const categories = useCategories()
  const contribute = useContribute(goal.id)

  // Putting money aside is money leaving a spending account, so only expense categories apply.
  // Defaulting to the SAVINGS bucket keeps the 70-20-10 report meaningful.
  const expense = (categories.data ?? []).filter((category) => category.kind === 'EXPENSE')
  const preferred =
    expense.find((category) => category.bucket === 'SAVINGS' && category.name === 'Savings') ??
    expense.find((category) => category.bucket === 'SAVINGS') ??
    expense[0]

  const [amount, setAmount] = useState('')
  const [contributedOn, setContributedOn] = useState(() => toDateKey(new Date()))
  const [accountId, setAccountId] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const chosenAccount = accountId || accounts.data?.[0]?.id || ''
  const chosenCategory = categoryId || preferred?.id || ''

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})

    contribute.mutate(
      { amount, contributedOn, accountId: chosenAccount, categoryId: chosenCategory, note },
      {
        onSuccess: onClose,
        onError: (caught) => {
          if (caught instanceof ApiError) {
            setFieldErrors(caught.fieldErrors)
            setError(Object.keys(caught.fieldErrors).length > 0 ? null : caught.message)
          } else {
            setError('Could not save. Check your connection and try again.')
          }
        },
      },
    )
  }

  return (
    <Modal open onClose={onClose} title={`Add savings — ${goal.name}`}>
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        {error && <Alert>{error}</Alert>}

        <p className="text-sm text-muted">
          {goal.achieved
            ? `Already reached ${formatPeso(goal.targetAmount)} — anything more is extra.`
            : `${formatPeso(goal.remaining)} to go.`}
        </p>

        <MoneyInput
          label="Amount"
          value={amount}
          onChange={setAmount}
          error={fieldErrors.amount}
          required
        />

        <DateField
          label="Date"
          value={contributedOn}
          onChange={(event) => setContributedOn(event.target.value)}
          error={fieldErrors.contributedOn}
        />

        <Select
          label="Moved from"
          value={chosenAccount}
          onChange={(event) => setAccountId(event.target.value)}
          error={fieldErrors.accountId}
        >
          {(accounts.data ?? []).map((account) => (
            <option key={account.id} value={account.id}>
              {account.name}
            </option>
          ))}
        </Select>

        <Select
          label="Record it under"
          value={chosenCategory}
          onChange={(event) => setCategoryId(event.target.value)}
          error={fieldErrors.categoryId}
        >
          {expense.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
        </Select>

        <Field
          label="Note"
          placeholder="Optional"
          value={note}
          onChange={(event) => setNote(event.target.value)}
          error={fieldErrors.note}
        />

        <p className="text-xs text-muted">
          This also records a transaction, so the money shows up against your savings bucket in the
          70-20-10 split.
        </p>

        <div className="mt-1 flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={contribute.isPending}>
            Add savings
          </Button>
        </div>
      </form>
    </Modal>
  )
}

function HistoryDialog({ goal, onClose }: { goal: Goal; onClose: () => void }) {
  const history = useContributions(goal.id)
  const remove = useDeleteContribution(goal.id)
  const [error, setError] = useState<string | null>(null)

  return (
    <Modal open onClose={onClose} title={`Contributions — ${goal.name}`}>
      {error && (
        <div className="mb-4">
          <Alert>{error}</Alert>
        </div>
      )}

      {(history.data?.length ?? 0) === 0 ? (
        <p className="py-6 text-center text-sm text-muted">Nothing saved toward this yet.</p>
      ) : (
        <ul className="divide-y divide-line">
          {(history.data ?? []).map((contribution) => (
            <li key={contribution.id} className="flex items-center gap-3 py-3">
              <div className="min-w-0 flex-1">
                <p className="tnum text-sm font-medium text-ink">
                  {formatPeso(contribution.amount)}
                </p>
                <p className="truncate text-xs text-muted">
                  {formatDate(contribution.contributedOn)}
                  {contribution.note && ` · ${contribution.note}`}
                </p>
              </div>
              <Button
                variant="ghost"
                className="shrink-0 px-2 py-1 text-xs"
                onClick={() => {
                  setError(null)
                  remove.mutate(contribution.id, {
                    onError: (caught) =>
                      setError(
                        caught instanceof ApiError
                          ? caught.message
                          : 'Could not undo that contribution.',
                      ),
                  })
                }}
              >
                Undo
              </Button>
            </li>
          ))}
        </ul>
      )}

      <p className="mt-4 text-xs text-muted">
        Undoing a contribution removes the transaction it created.
      </p>

      <div className="mt-4 flex justify-end">
        <Button variant="secondary" onClick={onClose}>
          Close
        </Button>
      </div>
    </Modal>
  )
}
