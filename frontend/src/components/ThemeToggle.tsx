import { useTheme } from '@/theme/ThemeProvider'

const sun = 'M12 4V2m0 20v-2m8-8h2M2 12h2m13.7-5.7l1.4-1.4M4.9 19.1l1.4-1.4m11.4 0l1.4 1.4M4.9 4.9l1.4 1.4'
const moon = 'M21 12.8A9 9 0 1111.2 3a7 7 0 009.8 9.8z'

/**
 * Flips the currently-displayed theme directly — every click changes what's on screen,
 * regardless of whether the preference was 'light', 'dark', or 'system' beforehand. A three-way
 * cycle through 'system' meant the first click from a light system was a no-op; a direct flip of
 * the resolved theme never is. 'system' stays available as an explicit choice in Settings.
 */
export function ThemeToggle() {
  const { resolved, setPreference } = useTheme()

  const next = resolved === 'dark' ? 'light' : 'dark'

  return (
    <button
      type="button"
      onClick={() => setPreference(next)}
      title={`Switch to ${next} theme`}
      aria-label={`Switch to ${next} theme`}
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
    </button>
  )
}
