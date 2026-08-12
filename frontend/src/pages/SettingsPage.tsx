import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { PageHeader } from '@/components/PageHeader'
import { ConfirmDialog, Modal } from '@/components/Modal'
import { MoneyInput, Select } from '@/components/form'
import { Alert, Button, Card, CardTitle, Field } from '@/components/ui'
import { useTheme } from '@/theme/ThemeProvider'
import {
  useAccounts,
  useCategories,
  useDeleteAccount,
  useDeleteCategory,
  useSaveAccount,
  useSaveCategory,
} from '@/api/useLedger'
import {
  ACCOUNT_TYPE_LABELS,
  BUCKET_LABELS,
  type Account,
  type AccountInput,
  type AccountType,
  type Bucket,
  type Category,
  type CategoryInput,
  type Kind,
} from '@/api/types'
import { ApiError } from '@/lib/api'
import { formatPeso } from '@/lib/format'

/** The light-mode chart ramp, offered as colour choices for new categories. */
const COLOR_CHOICES = [
  '#0f8a6c',
  '#2563eb',
  '#d97706',
  '#7c3aed',
  '#db2777',
  '#0891b2',
  '#65a30d',
  '#64748b',
]

export function SettingsPage() {
  return (
    <>
      <PageHeader title="Settings" subtitle="Your accounts, categories, and appearance." />
      <div className="grid gap-6">
        <AccountsCard />
        <CategoriesCard />
        <AppearanceCard />

        <Link
          to="/about"
          className="flex items-center justify-between rounded-xl border border-line bg-surface p-5 text-sm font-medium text-ink transition-colors hover:bg-surface-muted"
        >
          About &amp; feedback
          <span aria-hidden className="text-muted">
            ›
          </span>
        </Link>
      </div>
    </>
  )
}

/* -------------------------------------------------------------------- accounts */

function AccountsCard() {
  const accounts = useAccounts()
  const deleteAccount = useDeleteAccount()

  const [editing, setEditing] = useState<Account | null>(null)
  const [creating, setCreating] = useState(false)
  const [deleting, setDeleting] = useState<Account | null>(null)

  return (
    <Card>
      <CardTitle action={<Button onClick={() => setCreating(true)}>Add account</Button>}>
        Accounts
      </CardTitle>

      <ul className="divide-y divide-line">
        {(accounts.data ?? []).map((account) => (
          <li key={account.id} className="flex items-center gap-3 py-3">
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-ink">{account.name}</p>
              <p className="text-xs text-muted">{ACCOUNT_TYPE_LABELS[account.type]}</p>
            </div>
            <span className="tnum shrink-0 text-sm font-medium text-ink">
              {formatPeso(account.balance)}
            </span>
            {/* Icons on mobile — "Edit"/"Remove" text next to a peso amount is what was pushing
                this row to wrap at phone widths. Desktop has the room to keep the labels. */}
            <span className="flex shrink-0 gap-1 sm:hidden">
              <button
                type="button"
                onClick={() => setEditing(account)}
                aria-label={`Edit ${account.name}`}
                className="grid size-8 place-items-center rounded-lg text-muted hover:bg-surface-muted hover:text-body"
              >
                <Pencil />
              </button>
              <button
                type="button"
                onClick={() => setDeleting(account)}
                aria-label={`Remove ${account.name}`}
                className="grid size-8 place-items-center rounded-lg text-muted hover:bg-surface-muted hover:text-expense"
              >
                <Cross />
              </button>
            </span>
            <span className="hidden shrink-0 gap-1 sm:flex">
              <Button variant="ghost" onClick={() => setEditing(account)} className="px-2 py-1 text-xs">
                Edit
              </Button>
              <Button variant="ghost" onClick={() => setDeleting(account)} className="px-2 py-1 text-xs">
                Remove
              </Button>
            </span>
          </li>
        ))}
      </ul>

      {(creating || editing) && (
        <AccountDialog
          account={editing}
          onClose={() => {
            setCreating(false)
            setEditing(null)
          }}
        />
      )}

      <ConfirmDialog
        open={deleting !== null}
        onClose={() => setDeleting(null)}
        onConfirm={() =>
          deleting && deleteAccount.mutate(deleting.id, { onSuccess: () => setDeleting(null) })
        }
        loading={deleteAccount.isPending}
        confirmLabel="Remove"
        title="Remove account?"
        // Honest about what actually happens: archived, not erased, when it has history.
        message={`"${deleting?.name}" will be hidden. Any transactions already recorded against it are kept, so your reports stay accurate.`}
      />
    </Card>
  )
}

function AccountDialog({ account, onClose }: { account: Account | null; onClose: () => void }) {
  const save = useSaveAccount()
  const [form, setForm] = useState<AccountInput>(() => ({
    name: account?.name ?? '',
    type: account?.type ?? 'EWALLET',
    openingBalance: account ? String(account.openingBalance) : '0',
  }))
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})

    save.mutate(
      { id: account?.id, input: { ...form, openingBalance: form.openingBalance || '0' } },
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
    <Modal open onClose={onClose} title={account ? 'Edit account' : 'Add account'}>
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        {error && <Alert>{error}</Alert>}

        <Field
          label="Name"
          placeholder="GCash, BPI, Wallet…"
          value={form.name}
          onChange={(event) => setForm({ ...form, name: event.target.value })}
          error={fieldErrors.name}
          required
        />

        <Select
          label="Type"
          value={form.type}
          onChange={(event) => setForm({ ...form, type: event.target.value as AccountType })}
        >
          {Object.entries(ACCOUNT_TYPE_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Select>

        <MoneyInput
          label="Opening balance"
          value={form.openingBalance}
          onChange={(openingBalance) => setForm({ ...form, openingBalance })}
          error={fieldErrors.openingBalance}
        />

        <div className="mt-1 flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            {account ? 'Save changes' : 'Add account'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

/* ------------------------------------------------------------------ categories */

function CategoriesCard() {
  const categories = useCategories()
  const deleteCategory = useDeleteCategory()

  const [editing, setEditing] = useState<Category | null>(null)
  const [creating, setCreating] = useState(false)
  const [deleting, setDeleting] = useState<Category | null>(null)
  const [error, setError] = useState<string | null>(null)

  const income = (categories.data ?? []).filter((category) => category.kind === 'INCOME')
  const expense = (categories.data ?? []).filter((category) => category.kind === 'EXPENSE')

  return (
    <Card>
      <CardTitle action={<Button onClick={() => setCreating(true)}>Add category</Button>}>
        Categories
      </CardTitle>

      {error && (
        <div className="mb-4">
          <Alert>{error}</Alert>
        </div>
      )}

      <Group title="Money in" categories={income} onEdit={setEditing} onDelete={setDeleting} />
      <Group title="Money out" categories={expense} onEdit={setEditing} onDelete={setDeleting} />

      {(creating || editing) && (
        <CategoryDialog
          category={editing}
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
          if (!deleting) return
          setError(null)
          deleteCategory.mutate(deleting.id, {
            onSuccess: () => setDeleting(null),
            onError: (caught) => {
              // Built-in categories cannot be deleted; surface the server's reason.
              setError(caught instanceof ApiError ? caught.message : 'Could not remove that category.')
              setDeleting(null)
            },
          })
        }}
        loading={deleteCategory.isPending}
        confirmLabel="Remove"
        title="Remove category?"
        message={`"${deleting?.name}" will be hidden. Any transactions already filed under it are kept, so your reports stay accurate.`}
      />
    </Card>
  )
}

function Group({
  title,
  categories,
  onEdit,
  onDelete,
}: {
  title: string
  categories: Category[]
  onEdit: (category: Category) => void
  onDelete: (category: Category) => void
}) {
  if (categories.length === 0) return null

  return (
    <div className="mb-5 last:mb-0">
      <p className="mb-2 text-xs font-medium uppercase tracking-wide text-muted">{title}</p>
      <ul className="flex flex-wrap gap-2">
        {categories.map((category) => (
          <li
            key={category.id}
            className="flex items-center gap-2 rounded-full border border-line py-1.5 pr-1.5 pl-3 text-sm"
          >
            <span
              aria-hidden
              className="size-2.5 shrink-0 rounded-full"
              style={{ background: category.color }}
            />
            <span className="text-ink">{category.name}</span>
            {category.bucket && (
              <span className="text-xs text-muted">{BUCKET_LABELS[category.bucket]}</span>
            )}
            <button
              type="button"
              onClick={() => onEdit(category)}
              aria-label={`Edit ${category.name}`}
              className="grid size-6 place-items-center rounded-full text-muted hover:bg-surface-muted hover:text-body"
            >
              <Pencil />
            </button>
            {/* Built-ins have no remove button — the server refuses anyway, so offering it lies. */}
            {!category.system && (
              <button
                type="button"
                onClick={() => onDelete(category)}
                aria-label={`Remove ${category.name}`}
                className="grid size-6 place-items-center rounded-full text-muted hover:bg-surface-muted hover:text-expense"
              >
                <Cross />
              </button>
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}

function CategoryDialog({ category, onClose }: { category: Category | null; onClose: () => void }) {
  const save = useSaveCategory()
  const [form, setForm] = useState<CategoryInput>(() => ({
    name: category?.name ?? '',
    kind: category?.kind ?? 'EXPENSE',
    bucket: category?.bucket ?? 'NEEDS',
    color: category?.color ?? COLOR_CHOICES[0],
  }))
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})

    save.mutate(
      {
        id: category?.id,
        // Income carries no bucket — sending one violates a database CHECK.
        input: { ...form, bucket: form.kind === 'EXPENSE' ? form.bucket : null },
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
    <Modal open onClose={onClose} title={category ? 'Edit category' : 'Add category'}>
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        {error && <Alert>{error}</Alert>}

        <Field
          label="Name"
          placeholder="Pasalubong, Tuition…"
          value={form.name}
          onChange={(event) => setForm({ ...form, name: event.target.value })}
          error={fieldErrors.name}
          required
        />

        <Select
          label="Type"
          value={form.kind}
          // A category's type is fixed once created: changing it would rewrite the direction
          // of every transaction already filed under it.
          disabled={category !== null}
          onChange={(event) => setForm({ ...form, kind: event.target.value as Kind })}
        >
          <option value="EXPENSE">Money out</option>
          <option value="INCOME">Money in</option>
        </Select>
        {category !== null && (
          <p className="-mt-3 text-xs text-muted">
            A category's type cannot be changed after it is created.
          </p>
        )}

        {form.kind === 'EXPENSE' && (
          <Select
            label="70-20-10 bucket"
            value={form.bucket ?? 'NEEDS'}
            onChange={(event) => setForm({ ...form, bucket: event.target.value as Bucket })}
          >
            <option value="NEEDS">Needs — 70%</option>
            <option value="WANTS">Wants — 20%</option>
            <option value="SAVINGS">Savings — 10%</option>
          </Select>
        )}

        <div className="flex flex-col gap-1.5">
          <span className="text-sm font-medium text-ink">Colour</span>
          <div className="flex flex-wrap gap-2">
            {COLOR_CHOICES.map((color) => (
              <button
                key={color}
                type="button"
                onClick={() => setForm({ ...form, color })}
                aria-label={`Use colour ${color}`}
                aria-pressed={form.color === color}
                style={{ background: color }}
                className={`size-8 rounded-lg transition-transform ${
                  form.color === color ? 'ring-2 ring-accent ring-offset-2 ring-offset-surface' : ''
                }`}
              />
            ))}
          </div>
          {fieldErrors.color && <span className="text-xs text-expense">{fieldErrors.color}</span>}
        </div>

        <div className="mt-1 flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            {category ? 'Save changes' : 'Add category'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

/* ------------------------------------------------------------------ appearance */

function AppearanceCard() {
  const { preference, setPreference } = useTheme()
  const options = [
    { value: 'light', label: 'Light' },
    { value: 'dark', label: 'Dark' },
    { value: 'system', label: 'System' },
  ] as const

  return (
    <Card>
      <CardTitle>Appearance</CardTitle>
      <div className="flex gap-2">
        {options.map((option) => (
          <button
            key={option.value}
            type="button"
            onClick={() => setPreference(option.value)}
            aria-pressed={preference === option.value}
            className={`rounded-lg border px-4 py-2 text-sm font-medium transition-colors ${
              preference === option.value
                ? 'border-accent bg-accent-soft text-accent'
                : 'border-line text-body hover:bg-surface-muted'
            }`}
          >
            {option.label}
          </button>
        ))}
      </div>
    </Card>
  )
}

function Pencil() {
  return (
    <svg
      aria-hidden
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-3.5"
    >
      <path d="M12 20h9M16.5 3.5a2.1 2.1 0 013 3L7 19l-4 1 1-4z" />
    </svg>
  )
}

function Cross() {
  return (
    <svg
      aria-hidden
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      className="size-3.5"
    >
      <path d="M6 6l12 12M18 6L6 18" />
    </svg>
  )
}
