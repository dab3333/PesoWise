import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { api, clearToken, getToken, setToken, setUnauthorizedHandler } from '@/lib/api'

export interface User {
  id: string
  email: string
  displayName: string
  createdAt: string
}

interface AuthResponse {
  token: string
  expiresInSeconds: number
  user: User
}

interface AuthContextValue {
  user: User | null
  /** True until the stored token has been checked, so guards don't redirect prematurely. */
  initialising: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, displayName: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [initialising, setInitialising] = useState(true)

  const logout = useCallback(() => {
    clearToken()
    setUser(null)
  }, [])

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

  const authenticate = useCallback(async (path: string, body: unknown) => {
    const response = await api.post<AuthResponse>(path, body, { skipAuthRedirect: true })
    setToken(response.token)
    setUser(response.user)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      initialising,
      login: (email, password) => authenticate('/api/auth/login', { email, password }),
      register: (email, password, displayName) =>
        authenticate('/api/auth/register', { email, password, displayName }),
      logout,
    }),
    [user, initialising, authenticate, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside an AuthProvider')
  return context
}
