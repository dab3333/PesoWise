import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useAuth } from '@/auth/AuthContext'
import { AppShell } from '@/components/AppShell'
import { AuthPage } from '@/pages/AuthPage'
import { BudgetsPage } from '@/pages/BudgetsPage'
import { DashboardPage } from '@/pages/DashboardPage'
import { DebtsPage } from '@/pages/DebtsPage'
import { ForgotPasswordPage } from '@/pages/ForgotPasswordPage'
import { GoalsPage } from '@/pages/GoalsPage'
import { RecurringPage } from '@/pages/RecurringPage'
import { ResetPasswordPage } from '@/pages/ResetPasswordPage'
import { SettingsPage } from '@/pages/SettingsPage'
import { TransactionsPage } from '@/pages/TransactionsPage'
import { VerifyEmailPage } from '@/pages/VerifyEmailPage'

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
        path="/forgot-password"
        element={
          <RedirectIfAuthed>
            <ForgotPasswordPage />
          </RedirectIfAuthed>
        }
      />
      <Route
        path="/reset-password"
        element={
          <RedirectIfAuthed>
            <ResetPasswordPage />
          </RedirectIfAuthed>
        }
      />
      {/* Not wrapped in RedirectIfAuthed: someone can be signed in on this device and still
          need to confirm a link, and bouncing them to the dashboard would silently drop the
          token without redeeming it. */}
      <Route path="/verify-email" element={<VerifyEmailPage />} />

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
        <Route path="debts" element={<DebtsPage />} />
        <Route path="goals" element={<GoalsPage />} />
        <Route path="recurring" element={<RecurringPage />} />
        <Route path="settings" element={<SettingsPage />} />

      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
