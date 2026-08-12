import { useState } from 'react'
import type { FormEvent } from 'react'
import { PageHeader } from '@/components/PageHeader'
import { ConfirmDialog, Modal } from '@/components/Modal'
import { DateField, MoneyInput, Select } from '@/components/form'
import { Alert, Button, Card, CardTitle, EmptyState, Field } from '@/components/ui'
import { useAccounts, useCategories } from '@/api/useLedger'
import {
  FREQUENCY_LABELS,
  useBillRuns,
  useDeleteBill,
  usePostBill,
  useRecurringBills,
  useRunNow,
  useSaveBill,
  useSkipBill,
  type Bill,
  type BillInput,
  type Frequency,
} from '@/api/useRecurring'
import { ApiError } from '@/lib/api'
import { formatDate, formatPeso, toDateKey } from '@/lib/format'

export function RecurringPage() {
  const bills = useRecurringBills()
  const deleteBill = useDeleteBill()
  const runNow = useRunNow()

  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<Bill | null>(null)
  const [viewing, setViewing] = useState<Bill | null>(null)
  const [removing, setRemoving] = useState<Bill | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const data = bills.data
  const active = (data?.bills ?? []).filter((bill) => bill.active)
  const paused = (data?.bills ?? []).filter((bill) => !bill.active)

  return (
    <>
      <PageHeader
        title="Recurring"
        subtitle="Rent, bills, subscriptions — the money already committed each month."
        action={<Button onClick={() => setCreating(true)}>Add a bill</Button>}
      />

      {active.length > 0 && (
        <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
          <p className="text-sm text-muted">
            Committed per month:{' '}
            <span className="tnum font-medium text-ink">{formatPeso(data?.monthlyTotal ?? 0)}</span>
          </p>
          {/* Exposed because waiting until after midnight to see it work is a poor experience. */}
          <Button
            variant="secondary"
            loading={runNow.isPending}
            onClick={() =>
              runNow.mutate(undefined, {
                onSuccess: (summary) =>
                  setNotice(
                    summary.posted === 0 && summary.flagged === 0
                      ? 'Nothing was due.'
                      : `${summary.posted} recorded, ${summary.flagged} waiting for confirmation${
                          summary.skipped > 0 ? `, ${summary.skipped} already done` : ''
                        }.${summary.notes.length > 0 ? ` ${summary.notes.join(' ')}` : ''}`,
                  ),
                onError: () => setNotice('Could not run the check just now.'),
              })
            }
          >
            Run the check now
          </Button>
        </div>
      )}

      {notice && (
        <div className="mb-4">
          <Alert>{notice}</Alert>
        </div>
      )}

      <div className={bills.isFetching ? 'grid gap-6 opacity-50 transition-opacity' : 'grid gap-6'}>
        {(data?.dueNow.length ?? 0) > 0 && (
          <Card>
            <CardTitle>Due now</CardTitle>
            <ul className="divide-y divide-line">
              {(data?.dueNow ?? []).map((bill) => (
                <DueRow key={bill.id} bill={bill} onNotice={setNotice} />
              ))}
            </ul>
          </Card>
        )}

        <Card>
          <CardTitle>Scheduled</CardTitle>
          {active.length === 0 && !bills.isLoading ? (
            <EmptyState
              message="No recurring bills yet. Adding them shows what's already committed before you plan the rest."
              action={<Button onClick={() => setCreating(true)}>Add your first bill</Button>}
            />
          ) : (
            <ul className="divide-y divide-line">
              {active.map((bill) => (
                <BillRow
                  key={bill.id}
                  bill={bill}
                  onEdit={() => setEditing(bill)}
                  onHistory={() => setViewing(bill)}
                  onRemove={() => setRemoving(bill)}
                />
              ))}
            </ul>
          )}
        </Card>

        {paused.length > 0 && (
          <Card>
            <CardTitle>Paused</CardTitle>
            <ul className="divide-y divide-line">
              {paused.map((bill) => (
                <BillRow
                  key={bill.id}
                  bill={bill}
                  onEdit={() => setEditing(bill)}
                  onHistory={() => setViewing(bill)}
                  onRemove={() => setRemoving(bill)}
                />
              ))}
            </ul>
          </Card>
        )}
      </div>

      {(creating || editing) && (
        <BillDialog
          bill={editing}
          onClose={() => {
            setCreating(false)
            setEditing(null)
          }}
        />
      )}

      {viewing && <HistoryDialog bill={viewing} onClose={() => setViewing(null)} />}

      <ConfirmDialog
        open={removing !== null}
        onClose={() => setRemoving(null)}
        onConfirm={() =>
          removing && deleteBill.mutate(removing.id, { onSuccess: () => setRemoving(null) })
        }
        loading={deleteBill.isPending}
        confirmLabel="Delete"
        title="Delete this bill?"
        message={`"${removing?.name}" will stop recurring. The ${removing?.postedCount ?? 0} transaction(s) it already created stay in your ledger. To stop it temporarily instead, edit it and set it to paused.`}
      />
    </>
  )
}

function DueRow({ bill, onNotice }: { bill: Bill; onNotice: (message: string) => void }) {
  const post = usePostBill()
  const skip = useSkipBill()

  function handle(action: ReturnType<typeof usePostBill>, verb: string) {
    action.mutate(bill.id, {
      onError: (caught) =>
        onNotice(caught instanceof ApiError ? caught.message : `Could not ${verb} that bill.`),
    })
  }

  return (
    // Name+amount and meta+actions are separate rows — sharing one flex-wrap row let the fixed-
    // width amount and buttons squeeze the name column down to almost nothing at ~440px instead
    // of the row itself wrapping, which read as a margin/spacing bug rather than the layout issue
    // it actually was.
    <li className="py-3">
      <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
        <p className="min-w-0 truncate text-sm font-medium text-ink">{bill.name}</p>
        <span className="tnum shrink-0 text-sm font-medium text-ink">{formatPeso(bill.amount)}</span>
      </div>
      <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1">
        <p className="text-xs text-muted">
          {bill.daysUntilDue < 0
            ? `Was due ${formatDate(bill.nextRunDate)} — ${Math.abs(bill.daysUntilDue)} day(s) ago`
            : `Due ${formatDate(bill.nextRunDate)}`}
        </p>
        <span className="ml-auto flex flex-wrap shrink-0 gap-1">
          <Button
            className="px-3 py-1 text-xs"
            loading={post.isPending}
            onClick={() => handle(post, 'record')}
          >
            Record it
          </Button>
          <Button
            variant="ghost"
            className="px-2 py-1 text-xs"
            loading={skip.isPending}
            onClick={() => handle(skip, 'skip')}
          >
            Skip
          </Button>
        </span>
      </div>
    </li>
  )
}

function BillRow({
  bill,
  onEdit,
  onHistory,
  onRemove,
}: {
  bill: Bill
  onEdit: () => void
  onHistory: () => void
  onRemove: () => void
}) {
  return (
    // See DueRow above for why this is two rows rather than one flex-wrap row.
    <li className="py-3">
      <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
        <p className="min-w-0 truncate text-sm font-medium text-ink">
          {bill.name}
          {!bill.autoPost && (
            <span className="ml-2 text-xs font-normal text-muted">needs confirming</span>
          )}
        </p>
        <span className="tnum shrink-0 text-sm font-medium text-ink">{formatPeso(bill.amount)}</span>
      </div>
      <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1">
        <p className="text-xs text-muted">
          {FREQUENCY_LABELS[bill.frequency]}
          {bill.active && <> · next {formatDate(bill.nextRunDate)}</>}
          {bill.note && <> · {bill.note}</>}
        </p>
        <span className="ml-auto flex flex-wrap shrink-0 gap-1">
          <Button variant="ghost" onClick={onHistory} className="px-2 py-1 text-xs">
            History ({bill.postedCount})
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

function BillDialog({ bill, onClose }: { bill: Bill | null; onClose: () => void }) {
  const accounts = useAccounts()
  const categories = useCategories()
  const save = useSaveBill()

  // A recurring bill is an outgoing payment, so only expense categories apply.
  const expense = (categories.data ?? []).filter((category) => category.kind === 'EXPENSE')

  const [form, setForm] = useState<BillInput>(() => ({
    name: bill?.name ?? '',
    categoryId: bill?.categoryId ?? '',
    accountId: bill?.accountId ?? '',
    amount: bill ? String(bill.amount) : '',
    frequency: bill?.frequency ?? 'MONTHLY',
    nextRunDate: bill?.nextRunDate ?? toDateKey(new Date()),
    autoPost: bill?.autoPost ?? false,
    active: bill?.active ?? true,
    note: bill?.note ?? '',
  }))
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const chosenCategory = form.categoryId || expense[0]?.id || ''
  const chosenAccount = form.accountId || accounts.data?.[0]?.id || ''

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})

    save.mutate(
      { id: bill?.id, input: { ...form, categoryId: chosenCategory, accountId: chosenAccount } },
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
    <Modal open onClose={onClose} title={bill ? 'Edit bill' : 'Add a recurring bill'}>
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        {error && <Alert>{error}</Alert>}

        <Field
          label="What is it?"
          placeholder="Rent, Meralco, Netflix…"
          value={form.name}
          onChange={(event) => setForm({ ...form, name: event.target.value })}
          error={fieldErrors.name}
          required
        />

        <MoneyInput
          label="Amount"
          value={form.amount}
          onChange={(amount) => setForm({ ...form, amount })}
          error={fieldErrors.amount}
          required
        />

        <Select
          label="How often"
          value={form.frequency}
          onChange={(event) => setForm({ ...form, frequency: event.target.value as Frequency })}
        >
          {Object.entries(FREQUENCY_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Select>

        <DateField
          label={bill ? 'Next due' : 'First due'}
          value={form.nextRunDate}
          onChange={(event) => setForm({ ...form, nextRunDate: event.target.value })}
          error={fieldErrors.nextRunDate}
          hint={
            form.frequency === 'MONTHLY'
              ? 'A bill set to the 31st stays on the 31st, using the last day in shorter months.'
              : undefined
          }
        />

        <Select
          label="Category"
          value={chosenCategory}
          onChange={(event) => setForm({ ...form, categoryId: event.target.value })}
          error={fieldErrors.categoryId}
        >
          {expense.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
        </Select>

        <Select
          label="Paid from"
          value={chosenAccount}
          onChange={(event) => setForm({ ...form, accountId: event.target.value })}
          error={fieldErrors.accountId}
        >
          {(accounts.data ?? []).map((account) => (
            <option key={account.id} value={account.id}>
              {account.name}
            </option>
          ))}
        </Select>

        <Select
          label="When it falls due"
          value={form.autoPost ? 'auto' : 'confirm'}
          onChange={(event) => setForm({ ...form, autoPost: event.target.value === 'auto' })}
        >
          {/* Confirming is the default: a bill whose amount varies should never post itself. */}
          <option value="confirm">Ask me to confirm — best when the amount varies</option>
          <option value="auto">Record it automatically — for fixed amounts</option>
        </Select>

        {bill && (
          <Select
            label="Status"
            value={form.active ? 'active' : 'paused'}
            onChange={(event) => setForm({ ...form, active: event.target.value === 'active' })}
          >
            <option value="active">Active</option>
            <option value="paused">Paused — stop it without deleting</option>
          </Select>
        )}

        <Field
          label="Note"
          placeholder="Optional"
          value={form.note}
          onChange={(event) => setForm({ ...form, note: event.target.value })}
          error={fieldErrors.note}
        />

        <div className="mt-1 flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            {bill ? 'Save changes' : 'Add bill'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

function HistoryDialog({ bill, onClose }: { bill: Bill; onClose: () => void }) {
  const history = useBillRuns(bill.id)

  return (
    <Modal open onClose={onClose} title={`History — ${bill.name}`}>
      {(history.data?.length ?? 0) === 0 ? (
        <p className="py-6 text-center text-sm text-muted">Nothing recorded yet.</p>
      ) : (
        <ul className="max-h-72 divide-y divide-line overflow-auto">
          {(history.data ?? []).map((run) => (
            <li key={run.id} className="flex items-center gap-3 py-2.5 text-sm">
              <span className="flex-1 text-body">{formatDate(run.dueDate)}</span>
              {run.skipped ? (
                <span className="text-xs text-muted">Skipped</span>
              ) : (
                <span className="tnum text-ink">{formatPeso(bill.amount)}</span>
              )}
            </li>
          ))}
        </ul>
      )}

      <p className="mt-4 text-xs text-muted">
        Each occurrence is recorded once. Running the check again — or restarting the app — cannot
        charge the same date twice.
      </p>

      <div className="mt-4 flex justify-end">
        <Button variant="secondary" onClick={onClose}>
          Close
        </Button>
      </div>
    </Modal>
  )
}
