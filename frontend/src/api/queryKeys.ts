import type { TransactionFilters } from './types'

/**
 * All query keys in one place, because the invalidation rules depend on them being predictable.
 *
 * The important one: writing a transaction must invalidate reports and accounts too, since
 * balances and every aggregate are derived from transactions server-side. Forgetting that is how
 * a dashboard ends up showing stale totals after an edit.
 */
export const queryKeys = {
  accounts: ['accounts'] as const,
  categories: ['categories'] as const,
  transactions: (filters: TransactionFilters) => ['transactions', filters] as const,
  reports: ['reports'] as const,
  summary: (month: string) => ['reports', 'summary', month] as const,
  byCategory: (from: string, to: string) => ['reports', 'by-category', from, to] as const,
  byBucket: (month: string) => ['reports', 'by-bucket', month] as const,
  daily: (month: string) => ['reports', 'daily', month] as const,
}

/** Everything a transaction write can affect. */
export const transactionSideEffects = [
  ['transactions'],
  ['reports'],
  ['accounts'],
] as const
