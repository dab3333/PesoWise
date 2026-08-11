/**
 * Wordmark: a flat jade rounded square holding a peso glyph, then "PesoWise".
 * Deliberately drawn rather than an image so it follows the theme accent.
 */
export function Logo({ compact = false }: { compact?: boolean }) {
  return (
    <span className="flex items-center gap-2.5">
      <span
        aria-hidden
        className="grid size-8 shrink-0 place-items-center rounded-lg bg-accent text-base font-bold text-accent-on"
      >
        ₱
      </span>
      {!compact && (
        <span className="text-lg font-semibold tracking-tight text-ink">
          Peso<span className="text-accent">Wise</span>
        </span>
      )}
    </span>
  )
}
