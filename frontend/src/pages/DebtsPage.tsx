import { useState } from 'react'
import type { FormEvent } from 'react'
import { PageHeader } from '@/components/PageHeader'
import { ConfirmDialog, Modal } from '@/components/Modal'
import { MoneyInput, Select } from '@/components/form'
import { Alert, Button, Card, CardTitle, EmptyState, Field } from '@/components/ui'
import { useAccounts, useCategories } from '@/api/useLedger'
import {
  DIRECTION_LABELS,
  useDebtPayments,
  useDebts,
  useDeleteDebt,
  useDeletePayment,
  useRecordPayment,
  useSaveDebt,
  type Debt,
  type DebtDirection,
  type DebtInput,
} from '@/api/useDebts'
import { ApiError } from '@/lib/api'
import { formatDate, formatPeso, toDateKey, toNumber } from '@/lib/format'

export function DebtsPage() {
  const debts = useDebts()
  const deleteDebt = useDeleteDebt()

  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<Debt | null>(null)
  const [paying, setPaying] = useState<Debt | null>(null)
  const [viewing, setViewing] = useState<Debt | null>(null)
  const [removing, setRemoving] = useState<Debt | null>(null)

  const data = debts.data
  const active = (data?.debts ?? []).filter((debt) => debt.status === 'ACTIVE')
  const settled = (data?.debts ?? []).filter((debt) => debt.status === 'SETTLED')

  return (
    <>
      <PageHeader
        title="Debts"
        subtitle="Utang in both directions — what you owe, and what is owed to you."
        action={<Button onClick={() => setCreating(true)}>Add a debt</Button>}
      />

      {(data?.debts.length ?? 0) > 0 && (
        <div className="mb-6 grid gap-4 sm:grid-cols-3">
          <Tile label="I owe" value={data?.totalOwedByMe ?? 0} tone="bad" />
          <Tile label="Owed to me" value={data?.totalOwedToMe ?? 0} tone="good" />
          <Tile
            label="Net position"
            value={data?.netPosition ?? 0}
            tone={toNumber(data?.netPosition ?? 0) < 0 ? 'bad' : 'good'}
          />
        </div>
      )}

      <div className={debts.isFetching ? 'grid gap-6 opacity-50 transition-opacity' : 'grid gap-6'}>
        <Card>
          <CardTitle>Outstanding</CardTitle>
          {active.length === 0 && !debts.isLoading ? (
            <EmptyState
              message="No outstanding debts. Nakakatuwa."
              action={<Button onClick={() => setCreating(true)}>Add a debt</Button>}
            />
          ) : (
            <ul className="divide-y divide-line">
              {active.map((debt) => (
                <DebtRow
                  key={debt.id}
                  debt={debt}
                  onPay={() => setPaying(debt)}
                  onEdit={() => setEditing(debt)}
                  onHistory={() => setViewing(debt)}
                  onRemove={() => setRemoving(debt)}
                />
              ))}
            </ul>
          )}
        </Card>

        {settled.length > 0 && (
          <Card>
            <CardTitle>Settled</CardTitle>
            <ul className="divide-y divide-line">
              {settled.map((debt) => (
                <DebtRow
                  key={debt.id}
                  debt={debt}
                  onEdit={() => setEditing(debt)}
                  onHistory={() => setViewing(debt)}
                  onRemove={() => setRemoving(debt)}
                />
              ))}
            </ul>
          </Card>
        )}
      </div>

      {(creating || editing) && (
        <DebtDialog
          debt={editing}
          onClose={() => {
            setCreating(false)
            setEditing(null)
          }}
        />
      )}

      {paying && <PaymentDialog debt={paying} onClose={() => setPaying(null)} />}
      {viewing && <HistoryDialog debt={viewing} onClose={() => setViewing(null)} />}

      <ConfirmDialog
        open={removing !== null}
        onClose={() => setRemoving(null)}
        onConfirm={() =>
          removing && deleteDebt.mutate(removing.id, { onSuccess: () => setRemoving(null) })
        }
        loading={deleteDebt.isPending}
        confirmLabel="Remove"
        title="Remove this debt?"
        // Honest about what survives: the money really moved, so those transactions stay.
        message={`"${removing?.name}" and its ${removing?.paymentCount ?? 0} payment record(s) will be removed. The transactions those payments created stay in your ledger, because the money really did move.`}
      />
    </>
  )
}

function Tile({ label, value, tone }: { label: string; value: Money; tone: 'good' | 'bad' }) {
  const amount = toNumber(value)
  // A zero net position is neither good nor bad; keep it neutral.
  const color = amount === 0 ? 'text-ink' : tone === 'bad' ? 'text-expense' : 'text-income'
  return (
    <div className="rounded-xl border border-line bg-surface p-5">
      <p className="text-xs font-medium uppercase tracking-wide text-muted">{label}</p>
      <p className={`mt-2 text-2xl font-semibold ${color}`}>{formatPeso(amount)}</p>
    </div>
  )
}

type Money = import('@/api/types').Money

function DebtRow({
  debt,
  onPay,
  onEdit,
  onHistory,
  onRemove,
}: {
  debt: Debt
  onPay?: () => void
  onEdit: () => void
  onHistory: () => void
  onRemove: () => void
}) {
  const percent = Math.min(toNumber(debt.percentPaid), 100)
  const owed = debt.direction === 'OWED_BY_ME'

  return (
    <li className="py-4">
      <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
        <div className="min-w-0">
          <p className="truncate text-sm font-medium text-ink">
            {debt.name}
            <span className={`ml-2 text-xs font-normal ${owed ? 'text-expense' : 'text-income'}`}>
              {DIRECTION_LABELS[debt.direction]}
            </span>
          </p>
          <p className="mt-0.5 text-xs text-muted">
            {debt.counterparty && <span>{debt.counterparty} · </span>}
            {formatPeso(debt.paidAmount)} of {formatPeso(debt.principal)} paid
            {debt.interestRate !== null && toNumber(debt.interestRate) > 0 && (
              <span> · {toNumber(debt.interestRate)}% interest</span>
            )}
          </p>
        </div>
        <p className="tnum shrink-0 text-sm font-medium text-ink">
          {debt.status === 'SETTLED' ? (
            <span className="text-income">Settled</span>
          ) : (
            <>{formatPeso(debt.balance)} left</>
          )}
        </p>
      </div>

      {debt.status === 'ACTIVE' && (
        <div className="mt-2 h-2 overflow-hidden rounded-full bg-surface-muted">
          <div
            style={{ width: `${percent}%` }}
            className={`h-full rounded-full ${owed ? 'bg-accent' : 'bg-info'}`}
          />
        </div>
      )}

      <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1">
        {debt.dueDate && debt.status === 'ACTIVE' && (
          <span className={`text-xs ${debt.overdue ? 'font-medium text-expense' : 'text-muted'}`}>
            {debt.overdue
              ? `Overdue by ${Math.abs(debt.daysUntilDue ?? 0)} day(s)`
              : `Due ${formatDate(debt.dueDate)}`}
          </span>
        )}
        <span className="ml-auto flex gap-1">
          {onPay && (
            <Button onClick={onPay} className="px-3 py-1 text-xs">
              Record payment
            </Button>
          )}
          <Button variant="ghost" onClick={onHistory} className="px-2 py-1 text-xs">
            History ({debt.paymentCount})
          </Button>
          <Button variant="ghost" onClick={onEdit} className="px-2 py-1 text-xs">
            Edit
          </Button>
          <Button variant="ghost" onClick={onRemove} className="px-2 py-1 text-xs">
            Remove
          </Button>
        </span>
      </div>
    </li>
  )
}

function DebtDialog({ debt, onClose }: { debt: Debt | null; onClose: () => void }) {
  const save = useSaveDebt()
  const [form, setForm] = useState<DebtInput>(() => ({
    name: debt?.name ?? '',
    direction: debt?.direction ?? 'OWED_BY_ME',
    counterparty: debt?.counterparty ?? '',
    principal: debt ? String(debt.principal) : '',
    interestRate: debt?.interestRate !== null && debt?.interestRate !== undefined
      ? String(debt.interestRate)
      : '',
    dueDate: debt?.dueDate ?? '',
  }))
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})

    save.mutate(
      {
        id: debt?.id,
        input: {
          ...form,
          // Empty strings would fail server-side validation for these optional fields.
          interestRate: form.interestRate ? form.interestRate : null,
          dueDate: form.dueDate ? form.dueDate : null,
        },
      },
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
    <Modal open onClose={onClose} title={debt ? 'Edit debt' : 'Add a debt'}>
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        {error && <Alert>{error}</Alert>}

        <Field
          label="What is it?"
          placeholder="Pag-IBIG loan, utang kay Kuya Ben…"
          value={form.name}
          onChange={(event) => setForm({ ...form, name: event.target.value })}
          error={fieldErrors.name}
          required
        />

        <Select
          label="Direction"
          value={form.direction}
          // Fixed after creation: flipping it would invert every payment already recorded.
          disabled={debt !== null}
          onChange={(event) =>
            setForm({ ...form, direction: event.target.value as DebtDirection })
          }
        >
          <option value="OWED_BY_ME">I owe this</option>
          <option value="OWED_TO_ME">Someone owes me</option>
        </Select>

        <Field
          label="Who?"
          placeholder="Optional"
          value={form.counterparty}
          onChange={(event) => setForm({ ...form, counterparty: event.target.value })}
          error={fieldErrors.counterparty}
        />

        {debt === null ? (
          <MoneyInput
            label="Amount"
            value={form.principal}
            onChange={(principal) => setForm({ ...form, principal })}
            error={fieldErrors.principal}
            required
          />
        ) : (
          <p className="text-xs text-muted">
            The amount is fixed at {formatPeso(debt.principal)} — record payments to reduce the
            balance.
          </p>
        )}

        <Field
          label="Interest rate (%)"
          type="text"
          inputMode="decimal"
          placeholder="Optional"
          value={form.interestRate ?? ''}
          onChange={(event) => setForm({ ...form, interestRate: event.target.value })}
          error={fieldErrors.interestRate}
          hint="Recorded for reference — PesoWise does not compute interest yet."
        />

        <Field
          label="Due date"
          type="date"
          value={form.dueDate ?? ''}
          onChange={(event) => setForm({ ...form, dueDate: event.target.value })}
          error={fieldErrors.dueDate}
        />

        <div className="mt-1 flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            {debt ? 'Save changes' : 'Add debt'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

function PaymentDialog({ debt, onClose }: { debt: Debt; onClose: () => void }) {
  const accounts = useAccounts()
  const categories = useCategories()
  const record = useRecordPayment(debt.id)

  const owed = debt.direction === 'OWED_BY_ME'
  // Paying a debt is money out; being repaid is money in. Offer only the matching kind, so the
  // ledger direction can never contradict the debt.
  const relevant = (categories.data ?? []).filter((category) =>
    owed ? category.kind === 'EXPENSE' : category.kind === 'INCOME',
  )
  const preferred =
    relevant.find((category) => category.name === (owed ? 'Debt Payment' : 'Other Income')) ??
    relevant[0]

  const [amount, setAmount] = useState('')
  const [paidOn, setPaidOn] = useState(() => toDateKey(new Date()))
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

    record.mutate(
      { amount, paidOn, accountId: chosenAccount, categoryId: chosenCategory, note },
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
    <Modal open onClose={onClose} title={owed ? 'Record a payment' : 'Record a repayment'}>
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        {error && <Alert>{error}</Alert>}

        <p className="text-sm text-muted">
          {formatPeso(debt.balance)} still outstanding on {debt.name}.
        </p>

        <MoneyInput
          label="Amount"
          value={amount}
          onChange={setAmount}
          error={fieldErrors.amount}
          required
        />

        <Field
          label="Date"
          type="date"
          value={paidOn}
          onChange={(event) => setPaidOn(event.target.value)}
          error={fieldErrors.paidOn}
          required
        />

        <Select
          label={owed ? 'Paid from' : 'Received into'}
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
          {relevant.map((category) => (
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

        {/* Says plainly that this writes a transaction, so the side effect is not a surprise. */}
        <p className="text-xs text-muted">
          This also records a {owed ? 'expense' : 'income'} transaction, so the payment shows up in
          your reports and budgets.
        </p>

        <div className="mt-1 flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={record.isPending}>
            Record payment
          </Button>
        </div>
      </form>
    </Modal>
  )
}

function HistoryDialog({ debt, onClose }: { debt: Debt; onClose: () => void }) {
  const history = useDebtPayments(debt.id)
  const remove = useDeletePayment(debt.id)
  const [error, setError] = useState<string | null>(null)

  return (
    <Modal open onClose={onClose} title={`Payments — ${debt.name}`}>
      {error && (
        <div className="mb-4">
          <Alert>{error}</Alert>
        </div>
      )}

      {(history.data?.length ?? 0) === 0 ? (
        <p className="py-6 text-center text-sm text-muted">No payments recorded yet.</p>
      ) : (
        <ul className="divide-y divide-line">
          {(history.data ?? []).map((payment) => (
            <li key={payment.id} className="flex items-center gap-3 py-3">
              <div className="min-w-0 flex-1">
                <p className="tnum text-sm font-medium text-ink">{formatPeso(payment.amount)}</p>
                <p className="truncate text-xs text-muted">
                  {formatDate(payment.paidOn)}
                  {payment.note && ` · ${payment.note}`}
                </p>
              </div>
              <Button
                variant="ghost"
                className="shrink-0 px-2 py-1 text-xs"
                onClick={() => {
                  setError(null)
                  remove.mutate(payment.id, {
                    onError: (caught) =>
                      setError(
                        caught instanceof ApiError ? caught.message : 'Could not undo that payment.',
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
        Undoing a payment restores the balance and removes the transaction it created.
      </p>

      <div className="mt-4 flex justify-end">
        <Button variant="secondary" onClick={onClose}>
          Close
        </Button>
      </div>
    </Modal>
  )
}
