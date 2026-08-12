/**
 * Wordmark: a flat jade rounded square holding a peso glyph, then "PesoWise".
 * Deliberately drawn rather than an image so it follows the theme accent.
 */
export function Logo({
  compact = false,
  // For sitting directly on a solid jade fill (the auth page's brand panel) — the mark's own
  // jade-on-surface colours would otherwise disappear against a jade background.
  inverted = false,
}: {
  compact?: boolean
  inverted?: boolean
}) {
  return (
    <span className="flex items-center gap-2.5">
      <span
        aria-hidden
        className={
          'grid size-8 shrink-0 place-items-center rounded-lg text-base font-bold ' +
          (inverted ? 'bg-accent-on text-accent' : 'bg-accent text-accent-on')
        }
      >
        ₱
      </span>
      {!compact && (
        <span
          className={
            'text-lg font-semibold tracking-tight ' + (inverted ? 'text-accent-on' : 'text-ink')
          }
        >
          Peso
          <span className={inverted ? 'text-accent-on/70' : 'text-accent'}>Wise</span>
        </span>
      )}
    </span>
  )
}
