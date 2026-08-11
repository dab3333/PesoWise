import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { QueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { Money } from './types'

export interface Goal {
  id: string
  name: string
  targetAmount: Money
  /** Derived server-side from the contributions — never a stored total. */
  savedAmount: Money
  /** Zero once the target is met; never negative, since over-saving is not a shortfall. */
  remaining: Money
  percentComplete: Money
  targetDate: string | null
  daysUntilTarget: number | null
  /** What to set aside each remaining month to land on the target date. */
  monthlyNeeded: Money | null
  achieved: boolean
  behindSchedule: boolean
  archived: boolean
  note: string | null
  contributionCount: number
}

export interface GoalOverview {
  totalTarget: Money
  totalSaved: Money
  activeCount: number
  achievedCount: number
  goals: Goal[]
}

export interface Contribution {
  id: string
  goalId: string
  amount: Money
  contributedOn: string
  note: string | null
  ledgerTxnId: string | null
}

export interface GoalInput {
  name: string
  targetAmount: string
  targetDate: string | null
  note: string
  archived?: boolean
}

export interface ContributionInput {
  amount: string
  contributedOn: string
  accountId: string
  categoryId: string
  note: string
}

/** A contribution writes a ledger transaction, so it moves reports, balances and budgets too. */
function invalidateAll(queryClient: QueryClient): void {
  for (const key of [['goals'], ['transactions'], ['reports'], ['accounts'], ['budgets']]) {
    queryClient.invalidateQueries({ queryKey: key })
  }
}

export function useGoals() {
  return useQuery({
    queryKey: ['goals'],
    queryFn: () => api.get<GoalOverview>('/api/goals'),
  })
}

export function useContributions(goalId: string | null) {
  return useQuery({
    queryKey: ['goals', goalId, 'contributions'],
    queryFn: () => api.get<Contribution[]>(`/api/goals/${goalId}/contributions`),
    enabled: goalId !== null,
  })
}

export function useSaveGoal() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: GoalInput }) =>
      id ? api.put<Goal>(`/api/goals/${id}`, input) : api.post<Goal>('/api/goals', input),
    onSuccess: () => invalidateAll(queryClient),
  })
}

export function useDeleteGoal() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.delete<void>(`/api/goals/${id}`),
    onSuccess: () => invalidateAll(queryClient),
  })
}

export function useContribute(goalId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: ContributionInput) =>
      api.post<Contribution>(`/api/goals/${goalId}/contributions`, input),
    onSuccess: () => invalidateAll(queryClient),
  })
}

export function useDeleteContribution(goalId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (contributionId: string) =>
      api.delete<void>(`/api/goals/${goalId}/contributions/${contributionId}`),
    onSuccess: () => invalidateAll(queryClient),
  })
}
