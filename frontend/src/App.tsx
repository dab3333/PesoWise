import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useAuth } from '@/auth/AuthContext'
import { AppShell } from '@/components/AppShell'
import { AuthPage } from '@/pages/AuthPage'
import { BudgetsPage } from '@/pages/BudgetsPage'
import { DashboardPage } from '@/pages/DashboardPage'
import { PlaceholderPage } from '@/pages/PlaceholderPage'
import { SettingsPage } from '@/pages/SettingsPage'
import { TransactionsPage } from '@/pages/TransactionsPage'

/** Blocks rendering until the stored token has been checked, then redirects if unauthenticated. */
function RequireAuth({ children }: { children: ReactNode }) {
  const { user, initialising } = useAuth()
  const location = useLocation()

  if (initialising) return <FullPageLoader />
  // Remember where they were headed so login can send them back there.
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return <>{children}</>
}

/** Keeps signed-in users off the login and register pages. */
function RedirectIfAuthed({ children }: { children: ReactNode }) {
  const { user, initialising } = useAuth()

  if (initialising) return <FullPageLoader />
  if (user) return <Navigate to="/" replace />
  return <>{children}</>
}

function FullPageLoader() {
  return (
    <div className="grid min-h-dvh place-items-center bg-canvas">
      <span
        aria-label="Loading"
        role="status"
        className="size-6 animate-spin rounded-full border-2 border-accent border-t-transparent"
      />
    </div>
  )
}

export default function App() {
  return (
    <Routes>
      <Route
        path="/login"
        element={
          <RedirectIfAuthed>
            <AuthPage mode="login" />
          </RedirectIfAuthed>
        }
      />
      <Route
        path="/register"
        element={
          <RedirectIfAuthed>
            <AuthPage mode="register" />
          </RedirectIfAuthed>
        }
      />

      <Route
        element={
          <RequireAuth>
            <AppShell />
          </RequireAuth>
        }
      >
        <Route index element={<DashboardPage />} />
        <Route path="transactions" element={<TransactionsPage />} />
        <Route path="budgets" element={<BudgetsPage />} />
        <Route path="settings" element={<SettingsPage />} />

        {/* Replaced by real pages as each build step lands. */}
        <Route path="debts" element={<PlaceholderPage title="Debts" step="step 7" />} />
        <Route path="goals" element={<PlaceholderPage title="Goals" step="step 8" />} />
        <Route path="recurring" element={<PlaceholderPage title="Recurring" step="step 9" />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
