import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { QueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { queryKeys, transactionSideEffects } from './queryKeys'
import type {
  Account,
  AccountInput,
  BucketBreakdown,
  Category,
  CategoryInput,
  CategoryTotal,
  DailyTotal,
  PageResponse,
  Summary,
  Transaction,
  TransactionFilters,
  TransactionInput,
} from './types'

/* --------------------------------------------------------------------- accounts */

export function useAccounts() {
  return useQuery({
    queryKey: queryKeys.accounts,
    queryFn: () => api.get<Account[]>('/api/accounts'),
  })
}

export function useSaveAccount() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: AccountInput }) =>
      id
        ? api.put<Account>(`/api/accounts/${id}`, input)
        : api.post<Account>('/api/accounts', input),
    // Editing an opening balance changes every derived balance, so reports go too.
    onSuccess: () => invalidate(queryClient, [['accounts'], ['reports']]),
  })
}

export function useDeleteAccount() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.delete<void>(`/api/accounts/${id}`),
    onSuccess: () => invalidate(queryClient, [['accounts'], ['transactions'], ['reports']]),
  })
}

/* ------------------------------------------------------------------- categories */

export function useCategories() {
  return useQuery({
    queryKey: queryKeys.categories,
    queryFn: () => api.get<Category[]>('/api/categories'),
  })
}

export function useSaveCategory() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: CategoryInput }) =>
      id
        ? api.put<Category>(`/api/categories/${id}`, input)
        : api.post<Category>('/api/categories', input),
    // A recolour or a bucket change alters the charts, so reports are invalidated too.
    onSuccess: () => invalidate(queryClient, [['categories'], ['transactions'], ['reports']]),
  })
}

export function useDeleteCategory() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.delete<void>(`/api/categories/${id}`),
    onSuccess: () => invalidate(queryClient, [['categories'], ['transactions'], ['reports']]),
  })
}

/* ----------------------------------------------------------------- transactions */

export function useTransactions(filters: TransactionFilters) {
  return useQuery({
    queryKey: queryKeys.transactions(filters),
    queryFn: () => api.get<PageResponse<Transaction>>(`/api/transactions${toQuery(filters)}`),
    // Holds the previous page while the next loads, so the table does not flash empty.
    placeholderData: (previous) => previous,
  })
}

export function useSaveTransaction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: TransactionInput }) =>
      id
        ? api.put<Transaction>(`/api/transactions/${id}`, input)
        : api.post<Transaction>('/api/transactions', input),
    onSuccess: () => invalidate(queryClient, transactionSideEffects),
  })
}

export function useDeleteTransaction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.delete<void>(`/api/transactions/${id}`),
    onSuccess: () => invalidate(queryClient, transactionSideEffects),
  })
}

/* --------------------------------------------------------------------- reports */

export function useSummary(month: string) {
  return useQuery({
    queryKey: queryKeys.summary(month),
    queryFn: () => api.get<Summary>(`/api/reports/summary?month=${month}`),
  })
}

export function useSpendByCategory(from: string, to: string) {
  return useQuery({
    queryKey: queryKeys.byCategory(from, to),
    queryFn: () => api.get<CategoryTotal[]>(`/api/reports/by-category?from=${from}&to=${to}`),
  })
}

export function useBucketBreakdown(month: string) {
  return useQuery({
    queryKey: queryKeys.byBucket(month),
    queryFn: () => api.get<BucketBreakdown[]>(`/api/reports/by-bucket?month=${month}`),
  })
}

export function useDailyTotals(month: string) {
  return useQuery({
    queryKey: queryKeys.daily(month),
    queryFn: () => api.get<DailyTotal[]>(`/api/reports/daily?month=${month}`),
  })
}

/* ----------------------------------------------------------------------- utils */

function toQuery(filters: TransactionFilters): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(filters)) {
    // Skip empty filters so the server applies its own defaults.
    if (value !== undefined && value !== '' && value !== null) params.set(key, String(value))
  }
  const query = params.toString()
  return query ? `?${query}` : ''
}

/** Invalidates by key prefix, so ['reports'] catches every report query. */
function invalidate(queryClient: QueryClient, prefixes: readonly (readonly string[])[]): void {
  for (const prefix of prefixes) {
    queryClient.invalidateQueries({ queryKey: prefix })
  }
}
