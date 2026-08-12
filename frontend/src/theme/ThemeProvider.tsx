import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'

export type ThemePreference = 'light' | 'dark' | 'system'
type ResolvedTheme = 'light' | 'dark'

const STORAGE_KEY = 'pesowise.theme'

interface ThemeContextValue {
  preference: ThemePreference
  resolved: ResolvedTheme
  setPreference: (preference: ThemePreference) => void
}

const ThemeContext = createContext<ThemeContextValue | null>(null)

function readPreference(): ThemePreference {
  const stored = localStorage.getItem(STORAGE_KEY)
  // Light is the default until the user says otherwise — 'system' is opt-in via Settings, not
  // the out-of-the-box behaviour, so a fresh install doesn't quietly inherit the OS's choice.
  return stored === 'light' || stored === 'dark' || stored === 'system' ? stored : 'light'
}

function systemTheme(): ResolvedTheme {
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

/**
 * Resolves the theme in JS and always stamps data-theme on <html>. The CSS therefore needs one
 * set of dark values keyed on the attribute, with no prefers-color-scheme duplication.
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [preference, setPreferenceState] = useState<ThemePreference>(readPreference)
  const [systemResolved, setSystemResolved] = useState<ResolvedTheme>(systemTheme)

  // Follow the OS live while the preference is 'system'.
  useEffect(() => {
    const query = window.matchMedia('(prefers-color-scheme: dark)')
    const onChange = (event: MediaQueryListEvent) => {
      setSystemResolved(event.matches ? 'dark' : 'light')
    }
    query.addEventListener('change', onChange)
    return () => query.removeEventListener('change', onChange)
  }, [])

  const resolved: ResolvedTheme = preference === 'system' ? systemResolved : preference

  useEffect(() => {
    document.documentElement.dataset.theme = resolved
    // Keeps form controls and scrollbars in step with the app chrome.
    document.documentElement.style.colorScheme = resolved
  }, [resolved])

  const setPreference = useCallback((next: ThemePreference) => {
    setPreferenceState(next)
    localStorage.setItem(STORAGE_KEY, next)
  }, [])

  const value = useMemo(
    () => ({ preference, resolved, setPreference }),
    [preference, resolved, setPreference],
  )

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useTheme(): ThemeContextValue {
  const context = useContext(ThemeContext)
  if (!context) throw new Error('useTheme must be used inside a ThemeProvider')
  return context
}
