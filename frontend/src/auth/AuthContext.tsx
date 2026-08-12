import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { TOKEN_KEY, api, clearToken, getToken, setToken, setUnauthorizedHandler } from '@/lib/api'

export type Role = 'USER' | 'ADMIN'

export interface User {
  id: string
  email: string
  displayName: string
  role: Role
  emailVerified: boolean
  createdAt: string
}

interface AuthResponse {
  token: string
  expiresInSeconds: number
  user: User
}

/**
 * What registration returns now that an account is unusable until confirmed — a status, not a
 * session. `verified` is true only when the server has mail delivery switched off, in which case
 * there is no inbox to check and the user can sign in immediately.
 */
export interface RegistrationResult {
  email: string
  verified: boolean
  message: string
}

interface AuthContextValue {
  user: User | null
  /** True until the stored token has been checked, so guards don't redirect prematurely. */
  initialising: boolean
  login: (email: string, password: string) => Promise<void>
  /** Resolves to a status. It deliberately does not sign the new account in. */
  register: (email: string, password: string, displayName: string) => Promise<RegistrationResult>
  logout: () => void
}

export function isAdmin(user: User | null): boolean {
  return user?.role === 'ADMIN'
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [initialising, setInitialising] = useState(true)
  const queryClient = useQueryClient()

  // The cache is keyed by query, not by user — without clearing it, switching accounts (or
  // logging out and back in as someone else) would render the previous user's cached
  // transactions, budgets, etc. until every query happened to refetch.
  const logout = useCallback(() => {
    clearToken()
    setUser(null)
    queryClient.clear()
  }, [queryClient])

  // A 401 from any request means the session is over; drop the user so the guard redirects.
  useEffect(() => {
    setUnauthorizedHandler(() => setUser(null))
    return () => setUnauthorizedHandler(null)
  }, [])

  // A token in localStorage is not proof of a live session — it may be expired or belong to a
  // deleted account. Verify it against /me before trusting it.
  useEffect(() => {
    if (!getToken()) {
      setInitialising(false)
      return
    }

    let cancelled = false
    api
      .get<User>('/api/auth/me')
      .then((me) => {
        if (!cancelled) setUser(me)
      })
      .catch(() => {
        if (!cancelled) clearToken()
      })
      .finally(() => {
        if (!cancelled) setInitialising(false)
      })

    return () => {
      cancelled = true
    }
  }, [])

  // localStorage is shared by every tab on this origin, so signing in as someone else in another
  // tab overwrites the one token this tab was using too. Without this, the first tab would keep
  // rendering as the old user until its next 401 — quietly mixing two people's data in one tab.
  // The `storage` event only fires in OTHER tabs than the one that made the change, which is
  // exactly the tab that needs to notice.
  useEffect(() => {
    function onStorage(event: StorageEvent) {
      if (event.key !== TOKEN_KEY || event.storageArea !== localStorage) return

      if (!event.newValue) {
        // Signed out elsewhere: drop this tab's session without touching a token that is
        // already gone.
        setUser(null)
        queryClient.clear()
        return
      }

      if (event.newValue === event.oldValue) return

      // A different (or re-signed-in) account's token landed in storage — re-verify it and
      // adopt that identity, discarding whatever was cached under the previous one.
      queryClient.clear()
      api
        .get<User>('/api/auth/me')
        .then((me) => setUser(me))
        .catch(() => {
          clearToken()
          setUser(null)
        })
    }

    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [queryClient])

  const login = useCallback(
    async (email: string, password: string) => {
      const response = await api.post<AuthResponse>(
        '/api/auth/login',
        { email, password },
        { skipAuthRedirect: true },
      )
      // Drop any cached data from whoever was signed in before — a different login must never
      // render through the previous session's cached queries.
      queryClient.clear()
      setToken(response.token)
      setUser(response.user)
    },
    [queryClient],
  )

  // No token, no setUser: the account cannot be used until the emailed link is followed, so
  // there is nothing to sign in to yet.
  const register = useCallback(
    (email: string, password: string, displayName: string) =>
      api.post<RegistrationResult>(
        '/api/auth/register',
        { email, password, displayName },
        { skipAuthRedirect: true },
      ),
    [],
  )

  const value = useMemo<AuthContextValue>(
    () => ({ user, initialising, login, register, logout }),
    [user, initialising, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside an AuthProvider')
  return context
}
