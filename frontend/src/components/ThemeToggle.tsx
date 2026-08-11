import { useTheme } from '@/theme/ThemeProvider'

const sun = 'M12 4V2m0 20v-2m8-8h2M2 12h2m13.7-5.7l1.4-1.4M4.9 19.1l1.4-1.4m11.4 0l1.4 1.4M4.9 4.9l1.4 1.4'
const moon = 'M21 12.8A9 9 0 1111.2 3a7 7 0 009.8 9.8z'

/**
 * Cycles light → dark → system. A three-way toggle in one button keeps the sidebar footer
 * uncluttered; the title attribute names the next state so the cycle is discoverable.
 */
export function ThemeToggle() {
  const { preference, resolved, setPreference } = useTheme()

  const next = preference === 'light' ? 'dark' : preference === 'dark' ? 'system' : 'light'
  const label = preference === 'system' ? `System (${resolved})` : preference

  return (
    <button
      type="button"
      onClick={() => setPreference(next)}
      title={`Theme: ${label}. Switch to ${next}.`}
      aria-label={`Theme: ${label}. Switch to ${next}.`}
      className="grid size-9 shrink-0 place-items-center rounded-lg text-muted transition-colors hover:bg-surface-muted hover:text-body"
    >
      <svg
        aria-hidden
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.75"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="size-5"
      >
        {resolved === 'dark' ? <path d={moon} /> : <circle cx="12" cy="12" r="4" />}
        {resolved === 'light' && <path d={sun} />}
      </svg>
      {/* A dot marks "following the system" so it is distinguishable from an explicit choice. */}
      {preference === 'system' && <span className="absolute mt-6 size-1 rounded-full bg-accent" />}
    </button>
  )
}
