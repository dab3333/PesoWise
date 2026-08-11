import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { QueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { Money } from './types'

export type DebtDirection = 'OWED_BY_ME' | 'OWED_TO_ME'
export type DebtStatus = 'ACTIVE' | 'SETTLED'

export const DIRECTION_LABELS: Record<DebtDirection, string> = {
  OWED_BY_ME: 'I owe',
  OWED_TO_ME: 'Owed to me',
}

export interface Debt {
  id: string
  name: string
  direction: DebtDirection
  counterparty: string | null
  principal: Money
  balance: Money
  paidAmount: Money
  percentPaid: Money
  interestRate: Money | null
  dueDate: string | null
  /** Negative when overdue; null when there is no due date. */
  daysUntilDue: number | null
  overdue: boolean
  status: DebtStatus
  paymentCount: number
}

export interface DebtOverview {
  totalOwedByMe: Money
  totalOwedToMe: Money
  /** owedToMe − owedByMe. Positive means more is owed to you than by you. */
  netPosition: Money
  debts: Debt[]
}

export interface DebtPayment {
  id: string
  debtId: string
  amount: Money
  paidOn: string
  note: string | null
  /** The ledger transaction this payment created. */
  ledgerTxnId: string | null
}

export interface DebtInput {
  name: string
  direction: DebtDirection
  counterparty: string
  principal: string
  interestRate: string | null
  dueDate: string | null
}

export interface PaymentInput {
  amount: string
  paidOn: string
  accountId: string
  categoryId: string
  note: string
}

/**
 * A debt payment writes a transaction to the ledger, so it changes spending reports, account
 * balances and budget progress too. Invalidating only `debts` is how the dashboard ends up
 * disagreeing with the debt list.
 */
function invalidateAll(queryClient: QueryClient): void {
  for (const key of [['debts'], ['transactions'], ['reports'], ['accounts'], ['budgets']]) {
    queryClient.invalidateQueries({ queryKey: key })
  }
}

export function useDebts() {
  return useQuery({
    queryKey: ['debts'],
    queryFn: () => api.get<DebtOverview>('/api/debts'),
  })
}

export function useDebtPayments(debtId: string | null) {
  return useQuery({
    queryKey: ['debts', debtId, 'payments'],
    queryFn: () => api.get<DebtPayment[]>(`/api/debts/${debtId}/payments`),
    enabled: debtId !== null,
  })
}

export function useSaveDebt() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: DebtInput }) =>
      id
        ? // Principal and direction are fixed once created, so the update body is narrower.
          api.put<Debt>(`/api/debts/${id}`, {
            name: input.name,
            counterparty: input.counterparty,
            interestRate: input.interestRate,
            dueDate: input.dueDate,
          })
        : api.post<Debt>('/api/debts', input),
    onSuccess: () => invalidateAll(queryClient),
  })
}

export function useDeleteDebt() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.delete<void>(`/api/debts/${id}`),
    onSuccess: () => invalidateAll(queryClient),
  })
}

export function useRecordPayment(debtId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: PaymentInput) =>
      api.post<DebtPayment>(`/api/debts/${debtId}/payments`, input),
    onSuccess: () => invalidateAll(queryClient),
  })
}

export function useDeletePayment(debtId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (paymentId: string) =>
      api.delete<void>(`/api/debts/${debtId}/payments/${paymentId}`),
    onSuccess: () => invalidateAll(queryClient),
  })
}
