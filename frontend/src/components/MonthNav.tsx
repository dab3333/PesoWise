import { formatMonth, shiftMonth, toMonthKey } from '@/lib/format'

/**
 * The single filter row that scopes the whole dashboard — deliberately above the cards rather
 * than inside any one of them, so every chart re-renders against the same slice.
 */
export function MonthNav({
  month,
  onChange,
}: {
  month: string
  onChange: (month: string) => void
}) {
  const thisMonth = toMonthKey(new Date())
  // No forward navigation past the current month: there is nothing there yet.
  const atLatest = month >= thisMonth

  return (
    <div className="flex items-center gap-1">
      <NavButton label="Previous month" onClick={() => onChange(shiftMonth(month, -1))}>
        <path d="M15 18l-6-6 6-6" />
      </NavButton>

      <span className="min-w-[9.5rem] text-center text-sm font-medium text-ink">
        {formatMonth(month)}
      </span>

      <NavButton
        label="Next month"
        disabled={atLatest}
        onClick={() => onChange(shiftMonth(month, 1))}
      >
        <path d="M9 18l6-6-6-6" />
      </NavButton>

      {!atLatest && (
        <button
          type="button"
          onClick={() => onChange(thisMonth)}
          className="ml-1 rounded-lg px-2.5 py-1.5 text-xs font-medium text-accent hover:bg-accent-soft"
        >
          Today
        </button>
      )}
    </div>
  )
}

function NavButton({
  label,
  onClick,
  disabled,
  children,
}: {
  label: string
  onClick: () => void
  disabled?: boolean
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      className="grid size-9 place-items-center rounded-lg border border-line text-muted transition-colors hover:bg-surface-muted hover:text-body disabled:cursor-not-allowed disabled:opacity-40"
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
