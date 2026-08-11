import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { QueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { Money } from './types'

export type Frequency = 'WEEKLY' | 'MONTHLY' | 'YEARLY'

export const FREQUENCY_LABELS: Record<Frequency, string> = {
  WEEKLY: 'Weekly',
  MONTHLY: 'Monthly',
  YEARLY: 'Yearly',
}

export interface Bill {
  id: string
  name: string
  categoryId: string
  accountId: string
  amount: Money
  frequency: Frequency
  /** For monthly bills, the anchor day of the month. */
  dayOfPeriod: number | null
  nextRunDate: string
  daysUntilDue: number
  dueNow: boolean
  autoPost: boolean
  active: boolean
  note: string | null
  postedCount: number
}

export interface BillOverview {
  /** Weekly and yearly bills normalised to a monthly figure. */
  monthlyTotal: Money
  dueNow: Bill[]
  bills: Bill[]
}

export interface BillRun {
  id: string
  dueDate: string
  ledgerTxnId: string | null
  skipped: boolean
}

export interface RunSummary {
  posted: number
  flagged: number
  skipped: number
  notes: string[]
}

export interface BillInput {
  name: string
  categoryId: string
  accountId: string
  amount: string
  frequency: Frequency
  nextRunDate: string
  autoPost: boolean
  active?: boolean
  note: string
}

/** Posting a bill writes a ledger transaction, so reports, balances and budgets all move. */
function invalidateAll(queryClient: QueryClient): void {
  for (const key of [['recurring'], ['transactions'], ['reports'], ['accounts'], ['budgets']]) {
    queryClient.invalidateQueries({ queryKey: key })
  }
}

export function useRecurringBills() {
  return useQuery({
    queryKey: ['recurring'],
    queryFn: () => api.get<BillOverview>('/api/recurring'),
  })
}

export function useBillRuns(billId: string | null) {
  return useQuery({
    queryKey: ['recurring', billId, 'runs'],
    queryFn: () => api.get<BillRun[]>(`/api/recurring/${billId}/runs`),
    enabled: billId !== null,
  })
}

export function useSaveBill() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: BillInput }) =>
      id ? api.put<Bill>(`/api/recurring/${id}`, input) : api.post<Bill>('/api/recurring', input),
    onSuccess: () => invalidateAll(queryClient),
  })
}

export function useDeleteBill() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.delete<void>(`/api/recurring/${id}`),
    onSuccess: () => invalidateAll(queryClient),
  })
}

/** Confirms a due bill — the path for bills whose amount varies. */
export function usePostBill() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.post<BillRun>(`/api/recurring/${id}/post`),
    onSuccess: () => invalidateAll(queryClient),
  })
}

/** Marks the current occurrence dealt with without recording anything. */
export function useSkipBill() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.post<BillRun>(`/api/recurring/${id}/skip`),
    onSuccess: () => invalidateAll(queryClient),
  })
}

/**
 * Runs the daily pass now, rather than waiting until after midnight. Safe to call twice — the pass
 * is idempotent by design.
 */
export function useRunNow() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => api.post<RunSummary>('/api/recurring/run'),
    onSuccess: () => invalidateAll(queryClient),
  })
}
