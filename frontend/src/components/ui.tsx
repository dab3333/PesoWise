import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode } from 'react'
import { forwardRef, useId } from 'react'

/** Tiny class joiner — the app has no need for a clsx dependency. */
export function cn(...parts: Array<string | false | null | undefined>): string {
  return parts.filter(Boolean).join(' ')
}

/* ------------------------------------------------------------------- Card */

export function Card({ children, className }: { children: ReactNode; className?: string }) {
  // Flat: a 1px border does the separating, per DESIGN.md. No shadow.
  return (
    <div className={cn('rounded-xl border border-line bg-surface p-5', className)}>{children}</div>
  )
}

export function CardTitle({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    // flex-wrap lets a long action (e.g. the 70-20-10 caption) drop to its own full-width line
    // below the title on a narrow screen, rather than squeezing onto the title's row and wrapping
    // its text mid-word — the title and the action must each stay one line, not necessarily the
    // same line as each other.
    <div className="mb-4 flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
      <h2 className="text-lg font-semibold whitespace-nowrap">{children}</h2>
      {action && <div className="whitespace-nowrap">{action}</div>}
    </div>
  )
}

export function Label({ children }: { children: ReactNode }) {
  return (
    <span className="text-xs font-medium uppercase tracking-wide text-muted">{children}</span>
  )
}

/* ----------------------------------------------------------------- Button */

type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost'

const buttonVariants: Record<ButtonVariant, string> = {
  primary: 'bg-accent text-accent-on hover:bg-accent-hover',
  secondary: 'border border-line bg-surface text-body hover:bg-surface-muted',
  // Outlined by default; solid red is reserved for the confirm step of a destructive action.
  danger: 'border border-expense/40 bg-surface text-expense hover:bg-expense/10',
  ghost: 'text-muted hover:bg-surface-muted hover:text-body',
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  loading?: boolean
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', loading = false, className, children, disabled, ...rest },
  ref,
) {
  return (
    <button
      ref={ref}
      // Disabling while loading is what actually prevents a double submit.
      disabled={disabled || loading}
      className={cn(
        'inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-lg px-4 py-2 text-sm font-medium',
        'transition-colors disabled:cursor-not-allowed disabled:opacity-50',
        buttonVariants[variant],
        className,
      )}
      {...rest}
    >
      {loading && <Spinner />}
      {children}
    </button>
  )
})

function Spinner() {
  return (
    <span
      aria-hidden
      className="size-3.5 animate-spin rounded-full border-2 border-current border-t-transparent"
    />
  )
}

/* ------------------------------------------------------------------ Field */

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  error?: string
  hint?: string
}

export const Field = forwardRef<HTMLInputElement, FieldProps>(function Field(
  { label, error, hint, className, id, ...rest },
  ref,
) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  const describedBy = error ? `${inputId}-error` : hint ? `${inputId}-hint` : undefined

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={inputId} className="text-sm font-medium text-ink">
        {label}
      </label>
      <input
        ref={ref}
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={cn(
          'rounded-lg border bg-surface px-3 py-2 text-sm text-ink placeholder:text-subtle',
          'focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent',
          error ? 'border-expense' : 'border-line',
          className,
        )}
        {...rest}
      />
      {error ? (
        <span id={`${inputId}-error`} className="text-xs text-expense">
          {error}
        </span>
      ) : hint ? (
        <span id={`${inputId}-hint`} className="text-xs text-muted">
          {hint}
        </span>
      ) : null}
    </div>
  )
})

/* ------------------------------------------------------------------ Alert */

export function Alert({ children }: { children: ReactNode }) {
  return (
    <div
      role="alert"
      className="rounded-lg border border-expense/30 bg-expense/5 px-3 py-2 text-sm text-expense"
    >
      {children}
    </div>
  )
}

/* ------------------------------------------------------------ Empty state */

export function EmptyState({ message, action }: { message: string; action?: ReactNode }) {
  // One line of text and at most one button — no illustrations, per DESIGN.md.
  return (
    <div className="flex flex-col items-center gap-3 py-10 text-center">
      <p className="text-sm text-muted">{message}</p>
      {action}
    </div>
  )
}
