import { formatPeso, toNumber } from '@/lib/format'
import type { Money } from '@/api/types'

/**
 * A ratio against a limit — the right form for budget usage and the 70-20-10 split, rather than
 * a pie of three slices.
 *
 * The colour carries the warning, so no icon is needed: jade under 80%, amber to 100%, red over.
 * The percentage is always written out as well, so the state is never conveyed by colour alone.
 */
export function Meter({
  label,
  actual,
  target,
  caption,
}: {
  label: string
  actual: Money
  target: Money
  caption?: string
}) {
  const actualValue = toNumber(actual)
  const targetValue = toNumber(target)

  // With no target set, there is nothing to be over — show an empty track rather than 100%.
  const percent = targetValue > 0 ? (actualValue / targetValue) * 100 : 0
  const over = percent > 100

  const fill = over ? 'bg-expense' : percent >= 80 ? 'bg-warning' : 'bg-accent'
  const text = over ? 'text-expense' : percent >= 80 ? 'text-warning' : 'text-muted'

  return (
    <div>
      <div className="flex items-baseline justify-between gap-3">
        <span className="truncate text-sm font-medium text-ink">{label}</span>
        <span className="tnum shrink-0 text-sm text-body">
          {formatPeso(actualValue)}
          <span className="text-muted"> / {formatPeso(targetValue)}</span>
        </span>
      </div>

      <div
        role="meter"
        aria-label={label}
        aria-valuenow={Math.round(percent)}
        aria-valuemin={0}
        aria-valuemax={100}
        className="mt-2 h-2 overflow-hidden rounded-full bg-surface-muted"
      >
        <div
          // Capped at 100% so an overspend does not overflow the track; the red says "over".
          style={{ width: `${Math.min(percent, 100)}%` }}
          className={`h-full rounded-full ${fill}`}
        />
      </div>

      <div className="mt-1.5 flex items-baseline justify-between gap-3">
        <span className={`tnum text-xs font-medium ${text}`}>
          {percent.toFixed(0)}% used{over && ' — over budget'}
        </span>
        {caption && <span className="text-xs text-muted">{caption}</span>}
      </div>
    </div>
  )
}
