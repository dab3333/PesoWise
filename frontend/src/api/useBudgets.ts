import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { Bucket, Money } from './types'

export interface BudgetLine {
  categoryId: string
  categoryName: string
  color: string
  bucket: Bucket | null
  /** Null for a category with spending but no limit set. */
  limitAmount: Money | null
  spent: Money
  /** Negative when overspent — the UI shows that rather than clamping to zero. */
  remaining: Money | null
  percentUsed: Money
  overBudget: boolean
}

export interface BudgetOverview {
  month: string
  income: Money
  totalLimit: Money
  totalSpent: Money
  totalRemaining: Money
  unbudgetedSpend: Money
  budgeted: BudgetLine[]
  unbudgeted: BudgetLine[]
}

export interface SuggestedLine {
  categoryId: string
  categoryName: string
  color: string
  bucket: Bucket
  limitAmount: Money
  /** True when the amount came from this category's own spending history. */
  fromHistory: boolean
}

export interface BucketAllocation {
  bucket: Bucket
  targetPercent: number
  amount: Money
}

export interface Suggestion {
  month: string
  expectedIncome: Money
  incomeWasEstimated: boolean
  buckets: BucketAllocation[]
  lines: SuggestedLine[]
}

const budgetKey = (month: string) => ['budgets', month] as const

export function useBudgetOverview(month: string) {
  return useQuery({
    queryKey: budgetKey(month),
    queryFn: () => api.get<BudgetOverview>(`/api/budgets?month=${month}`),
    placeholderData: (previous) => previous,
  })
}

export function useSaveBudget(month: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { categoryId: string; limitAmount: string }) =>
      api.put<void>(`/api/budgets?month=${month}`, input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['budgets'] }),
  })
}

/** Applies many limits in one request, so a suggestion saves all-or-nothing. */
export function useSaveBudgets(month: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (budgets: Array<{ categoryId: string; limitAmount: string }>) =>
      api.put<void>(`/api/budgets/bulk?month=${month}`, { budgets }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['budgets'] }),
  })
}

export function useDeleteBudget(month: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (categoryId: string) =>
      api.delete<void>(`/api/budgets/${categoryId}?month=${month}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['budgets'] }),
  })
}

/**
 * Fetches a suggestion. A mutation rather than a query because it is an explicit user action
 * with a body, and must not run on mount or refetch on its own.
 *
 * @param expectedIncome omit to have the server estimate from last month's actual income
 */
export function useSuggestion(month: string) {
  return useMutation({
    mutationFn: (expectedIncome?: string) =>
      api.post<Suggestion>(
        `/api/budgets/suggestion?month=${month}`,
        expectedIncome ? { expectedIncome } : {},
      ),
  })
}

export function useCopyPreviousMonth(month: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => api.post<{ copied: number }>(`/api/budgets/copy-previous?month=${month}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['budgets'] }),
  })
}
