import { useState } from 'react'
import type { FormEvent } from 'react'
import { PageHeader } from '@/components/PageHeader'
import { ConfirmDialog } from '@/components/Modal'
import { Button, Card, EmptyState, Field, Th } from '@/components/ui'
import { useAdminUsers, useUpdateAdminUser } from '@/api/useAdmin'
import type { AdminUser } from '@/api/useAdmin'
import { formatTimestamp } from '@/lib/format'

const PAGE_SIZE = 25

type PendingAction =
  | { kind: 'promote' | 'demote'; user: AdminUser }
  | { kind: 'disable' | 'enable'; user: AdminUser }

export function AdminUsersPage() {
  const [q, setQ] = useState('')
  const [searchTerm, setSearchTerm] = useState('')
  const [page, setPage] = useState(0)
  const [pending, setPending] = useState<PendingAction | null>(null)

  const users = useAdminUsers({ q: searchTerm || undefined, page, size: PAGE_SIZE })
  const updateUser = useUpdateAdminUser()
  const rows = users.data?.items ?? []
  const totalPages = users.data?.totalPages ?? 0

  function onSearch(event: FormEvent) {
    event.preventDefault()
    setSearchTerm(q.trim())
    setPage(0)
  }

  function confirmAction() {
    if (!pending) return
    const input =
      pending.kind === 'promote'
        ? { role: 'ADMIN' as const }
        : pending.kind === 'demote'
          ? { role: 'USER' as const }
          : pending.kind === 'disable'
            ? { disabled: true }
            : { disabled: false }

    updateUser.mutate({ id: pending.user.id, input }, { onSuccess: () => setPending(null) })
  }

  return (
    <>
      <PageHeader title="Users" subtitle="Every account across the system." />

      <form onSubmit={onSearch} className="mb-4 flex gap-2">
        <div className="max-w-xs flex-1">
          <Field
            label="Search"
            placeholder="Name or email"
            value={q}
            onChange={(event) => setQ(event.target.value)}
          />
        </div>
        <Button type="submit" variant="secondary" className="self-end">
          Search
        </Button>
      </form>

      <Card className="p-0">
        {rows.length === 0 && !users.isLoading ? (
          <EmptyState message="No users match that search." />
        ) : (
          <div className={users.isFetching ? 'opacity-50 transition-opacity' : ''}>
            <ul className="divide-y divide-line md:hidden">
              {rows.map((row) => (
                <li key={row.id} className="flex flex-col gap-2 px-4 py-3">
                  <div className="flex items-center justify-between gap-2">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-ink">{row.displayName}</p>
                      <p className="truncate text-xs text-muted">{row.email}</p>
                    </div>
                    <RoleBadge row={row} />
                  </div>
                  <RowActions row={row} onAction={setPending} />
                </li>
              ))}
            </ul>

            <div className="hidden overflow-x-auto md:block">
              <table className="w-full min-w-[40rem] text-sm">
                <thead className="bg-surface-muted text-left">
                  <tr>
                    <Th>Name</Th>
                    <Th>Email</Th>
                    <Th>Status</Th>
                    <Th>Joined</Th>
                    <Th className="w-56" />
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row.id} className="border-b border-line last:border-0">
                      <td className="px-4 py-3 text-ink">{row.displayName}</td>
                      <td className="px-4 py-3 text-muted">{row.email}</td>
                      <td className="px-4 py-3">
                        <RoleBadge row={row} />
                      </td>
                      <td className="px-4 py-3 text-muted">{formatTimestamp(row.createdAt)}</td>
                      <td className="px-4 py-3">
                        <RowActions row={row} onAction={setPending} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {totalPages > 1 && (
              <div className="flex items-center justify-between border-t border-line px-4 py-3">
                <span className="text-xs text-muted">
                  Page {page + 1} of {totalPages} · {users.data?.totalItems} total
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

      <ConfirmDialog
        open={pending !== null}
        onClose={() => setPending(null)}
        onConfirm={confirmAction}
        loading={updateUser.isPending}
        confirmLabel={confirmLabelFor(pending)}
        title={titleFor(pending)}
        message={messageFor(pending)}
      />
    </>
  )
}

function RoleBadge({ row }: { row: AdminUser }) {
  return (
    <div className="flex shrink-0 flex-wrap items-center justify-end gap-1">
      <span
        className={`rounded-full px-2 py-0.5 text-xs font-medium ${
          row.role === 'ADMIN' ? 'bg-accent-soft text-accent' : 'bg-surface-muted text-muted'
        }`}
      >
        {row.role === 'ADMIN' ? 'Admin' : 'User'}
      </span>
      {!row.emailVerified && (
        <span className="rounded-full bg-warning/10 px-2 py-0.5 text-xs font-medium text-warning">
          Unverified
        </span>
      )}
      {row.disabled && (
        <span className="rounded-full bg-expense/10 px-2 py-0.5 text-xs font-medium text-expense">
          Disabled
        </span>
      )}
    </div>
  )
}

function RowActions({
  row,
  onAction,
}: {
  row: AdminUser
  onAction: (action: PendingAction) => void
}) {
  return (
    <span className="flex flex-wrap gap-1">
      <Button
        variant="ghost"
        className="px-2 py-1 text-xs"
        onClick={() =>
          onAction({ kind: row.role === 'ADMIN' ? 'demote' : 'promote', user: row })
        }
      >
        {row.role === 'ADMIN' ? 'Revoke admin' : 'Make admin'}
      </Button>
      <Button
        variant={row.disabled ? 'secondary' : 'danger'}
        className="px-2 py-1 text-xs"
        onClick={() => onAction({ kind: row.disabled ? 'enable' : 'disable', user: row })}
      >
        {row.disabled ? 'Enable' : 'Disable'}
      </Button>
    </span>
  )
}

function titleFor(pending: PendingAction | null): string {
  if (!pending) return ''
  switch (pending.kind) {
    case 'promote':
      return 'Grant admin access?'
    case 'demote':
      return 'Revoke admin access?'
    case 'disable':
      return 'Disable this account?'
    case 'enable':
      return 'Re-enable this account?'
  }
}

function messageFor(pending: PendingAction | null): string {
  if (!pending) return ''
  const name = pending.user.displayName
  switch (pending.kind) {
    case 'promote':
      return `${name} will be able to see and manage every account, including yours.`
    case 'demote':
      return `${name} will lose access to the admin panel immediately on their next request.`
    case 'disable':
      return `${name} will be signed out and unable to sign back in until re-enabled.`
    case 'enable':
      return `${name} will be able to sign in again.`
  }
}

function confirmLabelFor(pending: PendingAction | null): string {
  if (!pending) return 'Confirm'
  switch (pending.kind) {
    case 'promote':
      return 'Make admin'
    case 'demote':
      return 'Revoke'
    case 'disable':
      return 'Disable'
    case 'enable':
      return 'Enable'
  }
}
