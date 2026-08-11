import { forwardRef, useId } from 'react'
import type { ReactNode, SelectHTMLAttributes } from 'react'
import { cn } from './ui'

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string
  error?: string
  children: ReactNode
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { label, error, className, id, children, ...rest },
  ref,
) {
  const generatedId = useId()
  const selectId = id ?? generatedId

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={selectId} className="text-sm font-medium text-ink">
        {label}
      </label>
      <select
        ref={ref}
        id={selectId}
        aria-invalid={error ? true : undefined}
        className={cn(
          'rounded-lg border bg-surface px-3 py-2 text-sm text-ink',
          'focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent',
          error ? 'border-expense' : 'border-line',
          className,
        )}
        {...rest}
      >
        {children}
      </select>
      {error && <span className="text-xs text-expense">{error}</span>}
    </div>
  )
})

/**
 * Amount input. Uses inputMode="decimal" so mobile shows a numeric keypad, and keeps the value as
 * a string all the way to the API — parsing to a float here would reintroduce the rounding error
 * that BigDecimal exists to avoid.
 */
export const MoneyInput = forwardRef<HTMLInputElement, {
  label: string
  value: string
  onChange: (value: string) => void
  error?: string
  required?: boolean
}>(function MoneyInput({ label, value, onChange, error, required }, ref) {
  const id = useId()

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium text-ink">
        {label}
      </label>
      <div className="relative">
        <span
          aria-hidden
          className="pointer-events-none absolute inset-y-0 left-3 grid place-items-center text-sm text-muted"
        >
          ₱
        </span>
        <input
          ref={ref}
          id={id}
          type="text"
          inputMode="decimal"
          required={required}
          value={value}
          // Allow only digits, one dot, and a leading minus (credit cards open negative).
          onChange={(event) => {
            const next = event.target.value
            if (next === '' || /^-?\d*\.?\d{0,2}$/.test(next)) onChange(next)
          }}
          placeholder="0.00"
          aria-invalid={error ? true : undefined}
          className={cn(
            'tnum w-full rounded-lg border bg-surface py-2 pr-3 pl-7 text-sm text-ink placeholder:text-subtle',
            'focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent',
            error ? 'border-expense' : 'border-line',
          )}
        />
      </div>
      {error && <span className="text-xs text-expense">{error}</span>}
    </div>
  )
})
