/**
 * Mirrors the ledger-service DTOs.
 *
 * Money arrives as a JSON number from Jackson's BigDecimal serialisation, but is typed as
 * `number | string` and always passed through the formatters — so a future switch to string
 * serialisation (which is the safer choice for large amounts) needs no changes here.
 */
export type Money = number | string

export type AccountType = 'CASH' | 'BANK' | 'EWALLET' | 'CREDIT_CARD'
export type Kind = 'INCOME' | 'EXPENSE'
export type Bucket = 'NEEDS' | 'WANTS' | 'SAVINGS'
export type SourceType = 'MANUAL' | 'RECURRING_BILL' | 'DEBT_PAYMENT' | 'GOAL_CONTRIBUTION'

export const ACCOUNT_TYPE_LABELS: Record<AccountType, string> = {
  CASH: 'Cash',
  BANK: 'Bank',
  EWALLET: 'E-wallet',
  CREDIT_CARD: 'Credit card',
}

export const BUCKET_LABELS: Record<Bucket, string> = {
  NEEDS: 'Needs',
  WANTS: 'Wants',
  SAVINGS: 'Savings',
}

export interface Account {
  id: string
  name: string
  type: AccountType
  openingBalance: Money
  /** Derived server-side across all time, not the selected month. */
  balance: Money
}

export interface AccountInput {
  name: string
  type: AccountType
  openingBalance: string
}

export interface Category {
  id: string
  name: string
  kind: Kind
  bucket: Bucket | null
  color: string
  /** Seeded categories can be renamed and recoloured, but never deleted. */
  system: boolean
}

export interface CategoryInput {
  name: string
  kind: Kind
  bucket: Bucket | null
  color: string
}

export interface Transaction {
  id: string
  accountId: string
  accountName: string
  categoryId: string
  categoryName: string
  categoryColor: string
  kind: Kind
  amount: Money
  txnDate: string
  note: string | null
  sourceType: SourceType
  sourceId: string | null
}

export interface TransactionInput {
  accountId: string
  categoryId: string
  amount: string
  txnDate: string
  note: string
}

export interface TransactionFilters {
  from?: string
  to?: string
  categoryId?: string
  accountId?: string
  page?: number
  size?: number
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface Summary {
  month: string
  income: Money
  expense: Money
  net: Money
}

export interface CategoryTotal {
  categoryId: string
  categoryName: string
  color: string
  kind: Kind
  total: Money
}

export interface BucketBreakdown {
  bucket: Bucket
  targetPercent: number
  targetAmount: Money
  actualAmount: Money
  actualPercent: Money
}

export interface DailyTotal {
  date: string
  income: Money
  expense: Money
}
