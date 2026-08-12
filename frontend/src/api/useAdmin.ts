import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, downloadFile } from '@/lib/api'
import type { Role } from '@/auth/AuthContext'
import type { Money } from './types'
import type { FeedbackCounts } from './useFeedback'

/**
 * One panel of the admin overview. `available: false` means that service's call failed inside
 * admin-service — `data` is null and `error` explains why, so the page can render every other
 * panel instead of failing the whole request over one down dependency.
 */
export interface Section<T> {
  available: boolean
  data: T | null
  error: string | null
}

export interface SignupDay {
  date: string
  count: number
}

export interface UserStats {
  totalUsers: number
  verifiedUsers: number
  disabledUsers: number
  adminUsers: number
  signupsLast30Days: SignupDay[]
}

export interface DailyPoint {
  date: string
  income: Money
  expense: Money
}

export interface LedgerStats {
  transactionCount: number
  activeUsers: number
  totalIncome: Money
  totalExpense: Money
  dailyLast30Days: DailyPoint[]
}

export interface PlanningStats {
  budgetLinesThisMonth: number
  activeDebts: number
  settledDebts: number
  totalOwedByUsers: Money
  totalOwedToUsers: Money
  activeGoals: number
  totalGoalTargets: Money
  totalGoalSaved: Money
  activeRecurringBills: number
}

export interface OverviewResponse {
  users: Section<UserStats>
  ledger: Section<LedgerStats>
  planning: Section<PlanningStats>
  feedback: FeedbackCounts
}

export interface AdminUser {
  id: string
  email: string
  displayName: string
  role: Role
  emailVerified: boolean
  disabled: boolean
  createdAt: string
}

export interface AdminUserPage {
  items: AdminUser[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface UpdateAdminUserInput {
  role?: Role
  disabled?: boolean
}

export interface AuditEntry {
  id: string
  actorUserId: string
  action: string
  targetType: string | null
  targetId: string | null
  detail: string | null
  createdAt: string
}

export interface AuditPage {
  items: AuditEntry[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export function useOverview() {
  return useQuery({
    queryKey: ['admin', 'overview'],
    queryFn: () => api.get<OverviewResponse>('/api/admin/overview'),
    // The point of this page is to notice when something is wrong; a stale "everything is fine"
    // is the one failure mode a dashboard cannot afford. 30s matches the app-wide default
    // staleness everywhere else, so this isn't a special case, just not cached indefinitely.
    staleTime: 30_000,
  })
}

export function useAdminUsers(params: { q?: string; page: number; size: number }) {
  const query = new URLSearchParams()
  if (params.q) query.set('q', params.q)
  query.set('page', String(params.page))
  query.set('size', String(params.size))

  return useQuery({
    queryKey: ['admin', 'users', params.q ?? null, params.page, params.size],
    queryFn: () => api.get<AdminUserPage>(`/api/admin/users?${query}`),
  })
}

export function useUpdateAdminUser() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: UpdateAdminUserInput }) =>
      api.patch<AdminUser>(`/api/admin/users/${id}`, input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'overview'] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'audit'] })
    },
  })
}

export function useAuditLog(params: { page: number; size: number }) {
  return useQuery({
    queryKey: ['admin', 'audit', params.page, params.size],
    queryFn: () => api.get<AuditPage>(`/api/admin/audit?page=${params.page}&size=${params.size}`),
  })
}

/** Not a query: a report download is an action, not state to cache. */
export function downloadUsersReport(): Promise<void> {
  return downloadFile('/api/admin/reports/users.csv', 'users.csv')
}
