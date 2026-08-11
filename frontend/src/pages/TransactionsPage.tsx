import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { PageHeader } from '@/components/PageHeader'
import { MonthNav } from '@/components/MonthNav'
import { Modal, ConfirmDialog } from '@/components/Modal'
import { Select, MoneyInput } from '@/components/form'
import { Alert, Button, Card, EmptyState, Field } from '@/components/ui'
import {
  useAccounts,
  useCategories,
  useDeleteTransaction,
  useSaveTransaction,
  useTransactions,
} from '@/api/useLedger'
import type { Transaction, TransactionInput } from '@/api/types'
import { ApiError } from '@/lib/api'
import { formatDate, formatPeso, toDateKey, toMonthKey } from '@/lib/format'

const PAGE_SIZE = 25

export function TransactionsPage() {
  const [month, setMonth] = useState(() => toMonthKey(new Date()))
  const [categoryId, setCategoryId] = useState('')
  const [accountId, setAccountId] = useState('')
  const [page, setPage] = useState(0)

  const [editing, setEditing] = useState<Transaction | null>(null)
  const [creating, setCreating] = useState(false)
  const [deleting, setDeleting] = useState<Transaction | null>(null)

  const accounts = useAccounts()
  const categories = useCategories()
  const deleteTransaction = useDeleteTransaction()

  const filters = useMemo(
    () => ({
      from: `${month}-01`,
      to: lastDayOf(month),
      categoryId: categoryId || undefined,
      accountId: accountId || undefined,
      page,
      size: PAGE_SIZE,
    }),
    [month, categoryId, accountId, page],
  )

  const transactions = useTransactions(filters)
  const rows = transactions.data?.content ?? []
  const totalPages = transactions.data?.totalPages ?? 0

  /** Any filter change invalidates the current page number. */
  function changeFilter(apply: () => void) {
    apply()
    setPage(0)
  }

  return (
    <>
      <PageHeader
        title="Transactions"
        subtitle="Every peso in and out."
        action={<Button onClick={() => setCreating(true)}>Add transaction</Button>}
      />

      {/* One filter row scoping the table below it. */}
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <MonthNav month={month} onChange={(next) => changeFilter(() => setMonth(next))} />

        <div className="min-w-40">
          <Select
            label="Category"
            value={categoryId}
            onChange={(event) => changeFilter(() => setCategoryId(event.target.value))}
          >
            <option value="">All categories</option>
            {(categories.data ?? []).map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </Select>
        </div>

        <div className="min-w-40">
          <Select
            label="Account"
            value={accountId}
            onChange={(event) => changeFilter(() => setAccountId(event.target.value))}
          >
            <option value="">All accounts</option>
            {(accounts.data ?? []).map((account) => (
              <option key={account.id} value={account.id}>
                {account.name}
              </option>
            ))}
          </Select>
        </div>
      </div>

      <Card className="p-0">
        {rows.length === 0 && !transactions.isLoading ? (
          <EmptyState
            message="Nothing recorded for this month yet."
            action={<Button onClick={() => setCreating(true)}>Add your first transaction</Button>}
          />
        ) : (
          <div className={transactions.isFetching ? 'opacity-50 transition-opacity' : ''}>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[40rem] text-sm">
                <thead className="bg-surface-muted text-left">
                  <tr>
                    <Th>Date</Th>
                    <Th>Category</Th>
                    <Th>Account</Th>
                    <Th>Note</Th>
                    <Th className="text-right">Amount</Th>
                    <Th className="w-24" />
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row.id} className="border-b border-line last:border-0">
                      <td className="tnum px-4 py-3 whitespace-nowrap text-muted">
                        {formatDate(row.txnDate)}
                      </td>
                      <td className="px-4 py-3">
                        <span className="flex items-center gap-2">
                          {/* The stored category colour as an identity dot, not an encoding. */}
                          <span
                            aria-hidden
                            className="size-2 shrink-0 rounded-full"
                            style={{ background: row.categoryColor }}
                          />
                          <span className="text-ink">{row.categoryName}</span>
                        </span>
                      </td>
                      <td className="px-4 py-3 text-muted">{row.accountName}</td>
                      <td className="max-w-48 truncate px-4 py-3 text-muted">{row.note ?? '—'}</td>
                      <td
                        className={`tnum px-4 py-3 text-right font-medium whitespace-nowrap ${
                          row.kind === 'EXPENSE' ? 'text-expense' : 'text-income'
                        }`}
                      >
                        {/* Sign and colour together carry the direction. */}
                        {row.kind === 'EXPENSE' ? '−' : '+'}
                        {formatPeso(row.amount)}
                      </td>
                      <td className="px-4 py-3 text-right">
                        {row.sourceType === 'MANUAL' ? (
                          <span className="flex justify-end gap-1">
                            <IconButton label="Edit" onClick={() => setEditing(row)}>
                              <path d="M12 20h9M16.5 3.5a2.1 2.1 0 013 3L7 19l-4 1 1-4z" />
                            </IconButton>
                            <IconButton label="Delete" onClick={() => setDeleting(row)}>
                              <path d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6M10 11v6M14 11v6" />
                            </IconButton>
                          </span>
                        ) : (
                          // Rows created by planning-service are edited at their source, so
                          // the two records cannot drift apart.
                          <span className="text-xs text-subtle">automatic</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {totalPages > 1 && (
              <div className="flex items-center justify-between border-t border-line px-4 py-3">
                <span className="text-xs text-muted">
                  Page {page + 1} of {totalPages} · {transactions.data?.totalElements} total
                </span>
                <span className="flex gap-2">
                  <Button variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>
                    Previous
                  </Button>
                  <Button
                    variant="secondary"
                    disabled={page + 1 >= totalPages}
                    onClick={() => setPage(page + 1)}
                  >
                    Next
                  </Button>
                </span>
              </div>
            )}
          </div>
        )}
      </Card>

      {(creating || editing) && (
        <TransactionDialog
          transaction={editing}
          onClose={() => {
            setCreating(false)
            setEditing(null)
          }}
        />
      )}

      <ConfirmDialog
        open={deleting !== null}
        onClose={() => setDeleting(null)}
        onConfirm={() => {
          if (deleting) {
            deleteTransaction.mutate(deleting.id, { onSuccess: () => setDeleting(null) })
          }
        }}
        loading={deleteTransaction.isPending}
        title="Delete transaction?"
        message={
          deleting
            ? `${formatPeso(deleting.amount)} for ${deleting.categoryName} on ${formatDate(deleting.txnDate)} will be removed. This cannot be undone.`
            : ''
        }
      />
    </>
  )
}

function TransactionDialog({
  transaction,
  onClose,
}: {
  transaction: Transaction | null
  onClose: () => void
}) {
  const accounts = useAccounts()
  const categories = useCategories()
  const save = useSaveTransaction()

  const [form, setForm] = useState<TransactionInput>(() => ({
    accountId: transaction?.accountId ?? '',
    categoryId: transaction?.categoryId ?? '',
    amount: transaction ? String(transaction.amount) : '',
    txnDate: transaction?.txnDate ?? toDateKey(new Date()),
    note: transaction?.note ?? '',
  }))
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  // Default to the first account so the common case needs no extra choice.
  const accountId = form.accountId || accounts.data?.[0]?.id || ''

  const income = (categories.data ?? []).filter((category) => category.kind === 'INCOME')
  const expense = (categories.data ?? []).filter((category) => category.kind === 'EXPENSE')

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})

    save.mutate(
      { id: transaction?.id, input: { ...form, accountId } },
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
    <Modal open onClose={onClose} title={transaction ? 'Edit transaction' : 'Add transaction'}>
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        {error && <Alert>{error}</Alert>}

        <Select
          label="Category"
          value={form.categoryId}
          onChange={(event) => setForm({ ...form, categoryId: event.target.value })}
          error={fieldErrors.categoryId}
          required
        >
          <option value="" disabled>
            Choose a category
          </option>
          {/* Grouped, because the category is what decides income versus expense. */}
          <optgroup label="Money in">
            {income.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </optgroup>
          <optgroup label="Money out">
            {expense.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </optgroup>
        </Select>

        <MoneyInput
          label="Amount"
          value={form.amount}
          onChange={(amount) => setForm({ ...form, amount })}
          error={fieldErrors.amount}
          required
        />

        <Select
          label="Account"
          value={accountId}
          onChange={(event) => setForm({ ...form, accountId: event.target.value })}
          error={fieldErrors.accountId}
        >
          {(accounts.data ?? []).map((account) => (
            <option key={account.id} value={account.id}>
              {account.name}
            </option>
          ))}
        </Select>

        <Field
          label="Date"
          type="date"
          value={form.txnDate}
          onChange={(event) => setForm({ ...form, txnDate: event.target.value })}
          error={fieldErrors.txnDate}
          required
        />

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
            {transaction ? 'Save changes' : 'Add transaction'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

function Th({ children, className = '' }: { children?: React.ReactNode; className?: string }) {
  return (
    <th className={`px-4 py-2.5 text-xs font-medium text-muted ${className}`}>{children}</th>
  )
}

function IconButton({
  label,
  onClick,
  children,
}: {
  label: string
  onClick: () => void
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      title={label}
      className="grid size-8 place-items-center rounded-lg text-muted transition-colors hover:bg-surface-muted hover:text-body"
    >
      <svg
        aria-hidden
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.75"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="size-4"
      >
        {children}
      </svg>
    </button>
  )
}

/** @param month a YYYY-MM key */
function lastDayOf(month: string): string {
  const [year, monthNumber] = month.split('-').map(Number)
  return `${month}-${String(new Date(year, monthNumber, 0).getDate()).padStart(2, '0')}`
}
