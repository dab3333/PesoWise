import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, TextareaHTMLAttributes } from 'react'
import { forwardRef, useId, useState } from 'react'
import type { Money } from '@/api/types'
import { formatPeso, toNumber } from '@/lib/format'

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

/**
 * A password `Field` with a show/hide toggle — visibility is purely local display state, so it
 * lives entirely inside this component rather than being threaded through by every caller.
 */
export const PasswordField = forwardRef<HTMLInputElement, Omit<FieldProps, 'type'>>(
  function PasswordField({ label, error, hint, className, id, ...rest }, ref) {
    const [visible, setVisible] = useState(false)
    const generatedId = useId()
    const inputId = id ?? generatedId
    const describedBy = error ? `${inputId}-error` : hint ? `${inputId}-hint` : undefined

    return (
      <div className="flex flex-col gap-1.5">
        <label htmlFor={inputId} className="text-sm font-medium text-ink">
          {label}
        </label>
        <div className="relative">
          <input
            ref={ref}
            id={inputId}
            type={visible ? 'text' : 'password'}
            aria-invalid={error ? true : undefined}
            aria-describedby={describedBy}
            className={cn(
              'w-full rounded-lg border bg-surface px-3 py-2 pr-10 text-sm text-ink placeholder:text-subtle',
              'focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent',
              error ? 'border-expense' : 'border-line',
              className,
            )}
            {...rest}
          />
          <button
            type="button"
            onClick={() => setVisible((current) => !current)}
            aria-label={visible ? 'Hide password' : 'Show password'}
            className="absolute inset-y-0 right-0 grid w-10 place-items-center text-subtle transition-colors hover:text-body"
          >
            {visible ? <EyeOffIcon /> : <EyeIcon />}
          </button>
        </div>
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
  },
)

function EyeIcon() {
  return (
    <svg
      aria-hidden
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-4.5"
    >
      <path d="M1.5 12S5.5 5 12 5s10.5 7 10.5 7-4 7-10.5 7S1.5 12 1.5 12z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  )
}

function EyeOffIcon() {
  return (
    <svg
      aria-hidden
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-4.5"
    >
      <path d="M17.94 17.94A10.94 10.94 0 0112 20c-6.5 0-10.5-7-10.5-7a18.4 18.4 0 015-5.94" />
      <path d="M9.9 4.24A10.9 10.9 0 0112 4c6.5 0 10.5 7 10.5 7a18.5 18.5 0 01-2.16 3.19" />
      <path d="M14.12 14.12a3 3 0 11-4.24-4.24" />
      <path d="M1.5 1.5l21 21" />
    </svg>
  )
}

/* --------------------------------------------------------------- TextArea */

interface TextAreaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: string
  error?: string
  hint?: string
}

/** Mirrors {@link Field} exactly — the feedback form is the first thing in the app that needs
 * more than one line of free text, so this is the first `<textarea>` in the codebase. */
export const TextArea = forwardRef<HTMLTextAreaElement, TextAreaProps>(function TextArea(
  { label, error, hint, className, id, rows = 4, ...rest },
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
      <textarea
        ref={ref}
        id={inputId}
        rows={rows}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={cn(
          'resize-y rounded-lg border bg-surface px-3 py-2 text-sm text-ink placeholder:text-subtle',
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

export function Alert({
  children,
  tone = 'error',
}: {
  children: ReactNode
  /** 'error' (default) covers every existing call site; 'success' reuses the income/jade
   *  semantic colour for confirmations like a completed export or import. */
  tone?: 'error' | 'success'
}) {
  return (
    <div
      role="alert"
      className={
        tone === 'success'
          ? 'rounded-lg border border-income/30 bg-income/5 px-3 py-2 text-sm text-income'
          : 'rounded-lg border border-expense/30 bg-expense/5 px-3 py-2 text-sm text-expense'
      }
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

/* ---------------------------------------------------------- Table pieces */
/* Lifted out of TransactionsPage and DashboardPage, which each defined their own copy before
   the admin tables needed a second (then third) one. */

export function Th({ children, className = '' }: { children?: ReactNode; className?: string }) {
  return (
    <th className={cn('px-4 py-2.5 text-xs font-medium text-muted', className)}>{children}</th>
  )
}

export function IconButton({
  label,
  onClick,
  children,
  tone = 'default',
}: {
  label: string
  onClick: () => void
  children: ReactNode
  /** 'danger' is for a destructive action's trigger icon — never the confirm step itself. */
  tone?: 'default' | 'danger'
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      title={label}
      className={cn(
        'grid size-8 place-items-center rounded-lg text-muted transition-colors hover:bg-surface-muted',
        tone === 'danger' ? 'hover:text-expense' : 'hover:text-body',
      )}
    >
      <svg
        aria-hidden
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.75"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="size-4"
      >
        {children}
      </svg>
    </button>
  )
}

/* -------------------------------------------------------------- StatTile */

export function StatTile({
  label,
  value,
  tone = 'neutral',
}: {
  label: string
  value: Money
  tone?: 'income' | 'expense' | 'net' | 'neutral'
}) {
  const amount = toNumber(value)
  const color =
    tone === 'income'
      ? 'text-income'
      : tone === 'expense'
        ? 'text-expense'
        : tone === 'net' && amount < 0
          ? 'text-expense'
          : 'text-ink'

  return (
    <div className="rounded-xl border border-line bg-surface p-5">
      <p className="text-xs font-medium uppercase tracking-wide text-muted">{label}</p>
      {/* Proportional figures, not tabular: equal-width digits read loose at display sizes. */}
      <p className={`mt-2 text-2xl font-semibold sm:text-3xl ${color}`}>{formatPeso(amount)}</p>
    </div>
  )
}

/** Like {@link StatTile} but for a plain count rather than a peso amount — no currency symbol. */
export function CountTile({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-xl border border-line bg-surface p-5">
      <p className="text-xs font-medium uppercase tracking-wide text-muted">{label}</p>
      <p className="tnum mt-2 text-2xl font-semibold text-ink sm:text-3xl">{value}</p>
    </div>
  )
}
