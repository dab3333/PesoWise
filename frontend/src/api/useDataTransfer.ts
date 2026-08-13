import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api, downloadJson } from '@/lib/api'

/**
 * The export/import file is round-tripped through the two owning services as opaque blobs — the
 * frontend never reads or edits individual fields, only counts array lengths for the
 * confirmation message, so these stay loosely typed rather than mirroring every backend field.
 */
interface LedgerExport {
  accounts: unknown[]
  categories: unknown[]
  transactions: unknown[]
}

interface PlanningExport {
  budgets: unknown[]
  goals: unknown[]
  goalContributions: unknown[]
  debts: unknown[]
  debtPayments: unknown[]
  recurringBills: unknown[]
  recurringRuns: unknown[]
  debtInterestAccruals: unknown[]
}

export interface PesoWiseExportFile {
  version: 1
  exportedAt: string
  ledger: LedgerExport
  planning: PlanningExport
}

/**
 * Old-id → new-id maps returned by ledger-service's import — planning-service's import needs
 * these to remap its own {@code categoryId}/{@code accountId}/{@code ledgerTxnId} references
 * onto whatever ledger-service just regenerated them as. Import always generates fresh ids
 * rather than reusing the file's own, so "restore my own backup" and "load someone else's
 * export into a different account" are the same code path with no special-casing.
 */
interface LedgerImportResult {
  summary: { accounts: number; categories: number; transactions: number }
  accountIds: Record<string, string>
  categoryIds: Record<string, string>
  transactionIds: Record<string, string>
}

/** A human-readable count of every record type in a file, for the import confirmation dialog. */
export function describeCounts(file: PesoWiseExportFile): string {
  const parts: Array<[string, number]> = [
    ['account', file.ledger.accounts.length],
    ['category', file.ledger.categories.length],
    ['transaction', file.ledger.transactions.length],
    ['budget', file.planning.budgets.length],
    ['goal', file.planning.goals.length],
    ['debt', file.planning.debts.length],
    ['recurring bill', file.planning.recurringBills.length],
  ]
  return parts
    .filter(([, count]) => count > 0)
    .map(([label, count]) => `${count} ${label}${count === 1 ? '' : 's'}`)
    .join(', ')
}

/**
 * Reads a File the user picked and validates it has the shape an export produced, before ever
 * showing a confirmation dialog or hitting the network with it.
 */
export async function parseExportFile(file: File): Promise<PesoWiseExportFile> {
  let parsed: unknown
  try {
    parsed = JSON.parse(await file.text())
  } catch {
    throw new Error('That file is not valid JSON.')
  }

  if (
    typeof parsed !== 'object' ||
    parsed === null ||
    !('ledger' in parsed) ||
    !('planning' in parsed)
  ) {
    throw new Error("That doesn't look like a PesoWise export file.")
  }

  return parsed as PesoWiseExportFile
}

/**
 * Fetches both services' exports and combines them into one file — ledger-service and
 * planning-service each own their own tables (see architecture.md), so there is no single
 * backend endpoint that returns everything; the frontend is what stitches the two halves
 * together for the user.
 */
export async function exportData(): Promise<void> {
  const [ledger, planning] = await Promise.all([
    api.get<LedgerExport>('/api/data/ledger/export'),
    api.get<PlanningExport>('/api/data/planning/export'),
  ])

  const file: PesoWiseExportFile = {
    version: 1,
    exportedAt: new Date().toISOString(),
    ledger,
    planning,
  }

  downloadJson(`pesowise-export-${file.exportedAt.slice(0, 10)}.json`, file)
}

/**
 * Imports a previously-exported file, replacing all of the current user's data — whether that's
 * the same account restoring its own backup, or a different account loading someone else's
 * export. Ledger's import runs first (its response carries the id maps planning's import needs
 * to remap onto), then planning's — mirroring the project's dual-write precedent (see
 * architecture.md) of accepting a narrow non-atomic window between two independently-owned
 * stores rather than trying to coordinate a distributed transaction across them. If ledger
 * succeeds and planning then fails, the thrown error says so explicitly: re-running the whole
 * import again is safe, since import always wipes before it reinserts.
 */
export function useImportData() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (file: PesoWiseExportFile) => {
      const ledgerResult = await api.post<LedgerImportResult>('/api/data/ledger/import', file.ledger)
      try {
        await api.post('/api/data/planning/import', {
          data: file.planning,
          ledgerIds: {
            categoryIds: ledgerResult.categoryIds,
            accountIds: ledgerResult.accountIds,
            transactionIds: ledgerResult.transactionIds,
          },
        })
      } catch (caught) {
        const message = caught instanceof Error ? caught.message : 'an unknown error'
        throw new Error(
          `Your accounts, categories, and transactions were replaced, but the rest of the ` +
            `import (budgets, goals, debts, recurring bills) failed: ${message}. It's safe to ` +
            `try the import again.`,
        )
      }
    },
    // Every query in the app is now stale at once — the same situation a logout/account-switch
    // handles in AuthContext.tsx, which clears the whole cache rather than listing every key.
    onSuccess: () => queryClient.clear(),
  })
}
