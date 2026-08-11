/**
 * Every peso amount in the app goes through here. Formatting money inline per component is
 * how you end up with ₱1234.5 in one place and PHP 1,234.50 in another.
 */
const pesoFormatter = new Intl.NumberFormat('en-PH', {
  style: 'currency',
  currency: 'PHP',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

/** Amounts arrive from the API as strings (BigDecimal), so accept both. */
export function formatPeso(amount: number | string): string {
  return pesoFormatter.format(toNumber(amount))
}

/** Compact form for chart axes and tight tiles: ₱1.2k, ₱3.4M. */
export function formatPesoCompact(amount: number | string): string {
  const value = toNumber(amount)
  const abs = Math.abs(value)
  const sign = value < 0 ? '-' : ''

  if (abs >= 1_000_000) return `${sign}₱${(abs / 1_000_000).toFixed(1)}M`
  if (abs >= 1_000) return `${sign}₱${(abs / 1_000).toFixed(1)}k`
  return `${sign}₱${abs.toFixed(0)}`
}

/** Signed display for transaction rows: expenses render with a true minus sign. */
export function formatSignedPeso(amount: number | string, kind: 'INCOME' | 'EXPENSE'): string {
  const formatted = formatPeso(Math.abs(toNumber(amount)))
  return kind === 'EXPENSE' ? `−${formatted}` : `+${formatted}`
}

export function toNumber(amount: number | string): number {
  const value = typeof amount === 'string' ? Number.parseFloat(amount) : amount
  return Number.isFinite(value) ? value : 0
}

const dateFormatter = new Intl.DateTimeFormat('en-PH', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
})

const monthFormatter = new Intl.DateTimeFormat('en-PH', { month: 'long', year: 'numeric' })

/**
 * @param isoDate a plain date such as 2026-08-11
 *
 * Parsed as local time on purpose: `new Date('2026-08-11')` is UTC midnight, which renders as
 * the 10th in UTC+8. A transaction dated the 11th must never display as the 10th.
 */
export function formatDate(isoDate: string): string {
  return dateFormatter.format(parseLocalDate(isoDate))
}

/** @param month a YYYY-MM period key */
export function formatMonth(month: string): string {
  return monthFormatter.format(parseLocalDate(`${month}-01`))
}

export function parseLocalDate(isoDate: string): Date {
  const [year, month, day] = isoDate.split('-').map(Number)
  return new Date(year, (month ?? 1) - 1, day ?? 1)
}

/** The YYYY-MM key for a date, in local time. */
export function toMonthKey(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

/** The YYYY-MM-DD key for a date, in local time. */
export function toDateKey(date: Date): string {
  return `${toMonthKey(date)}-${String(date.getDate()).padStart(2, '0')}`
}

/** @param offset months to shift, e.g. -1 for the previous month */
export function shiftMonth(month: string, offset: number): string {
  const date = parseLocalDate(`${month}-01`)
  date.setMonth(date.getMonth() + offset)
  return toMonthKey(date)
}
