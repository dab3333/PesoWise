import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '@/auth/AuthContext'
import { Logo } from '@/components/Logo'
import { Alert, Button, Field } from '@/components/ui'
import { ApiError } from '@/lib/api'

type Mode = 'login' | 'register'

const copy: Record<Mode, { title: string; subtitle: string; submit: string }> = {
  login: {
    title: 'Welcome back',
    subtitle: 'Sign in to pick up where you left off.',
    submit: 'Sign in',
  },
  register: {
    title: 'Create your account',
    subtitle: 'Start tracking where your pesos actually go.',
    submit: 'Create account',
  },
}

/**
 * Login and register share one layout and one submit path — the only differences are the copy,
 * the extra name field, and which auth call runs.
 */
export function AuthPage({ mode }: { mode: Mode }) {
  const { login, register } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  // Send the user back to whatever they were trying to reach before the guard intervened.
  const redirectTo = (location.state as { from?: string } | null)?.from ?? '/'

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    setFieldErrors({})

    try {
      if (mode === 'login') await login(email, password)
      else await register(email, password, displayName)
      navigate(redirectTo, { replace: true })
    } catch (caught) {
      if (caught instanceof ApiError) {
        setFieldErrors(caught.fieldErrors)
        // With field errors present, the banner would just repeat "check the fields".
        setError(Object.keys(caught.fieldErrors).length > 0 ? null : caught.message)
      } else {
        setError('Could not reach the server. Check your connection and try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  const { title, subtitle, submit } = copy[mode]

  return (
    <main className="flex min-h-dvh flex-col items-center justify-center bg-canvas px-4 py-10">
      <div className="w-full max-w-sm">
        <div className="mb-8 flex justify-center">
          <Logo />
        </div>

        <div className="rounded-xl border border-line bg-surface p-6">
          <h1 className="text-xl font-semibold">{title}</h1>
          <p className="mt-1 mb-6 text-sm text-muted">{subtitle}</p>

          <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
            {error && <Alert>{error}</Alert>}

            {mode === 'register' && (
              <Field
                label="Name"
                autoComplete="name"
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
                error={fieldErrors.displayName}
                required
              />
            )}

            <Field
              label="Email"
              type="email"
              inputMode="email"
              autoComplete="email"
              placeholder="you@example.com"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              error={fieldErrors.email}
              required
            />

            <Field
              label="Password"
              type="password"
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              error={fieldErrors.password}
              hint={mode === 'register' ? 'At least 8 characters.' : undefined}
              required
            />

            <Button type="submit" loading={submitting} className="mt-2 w-full">
              {submit}
            </Button>
          </form>
        </div>

        <p className="mt-6 text-center text-sm text-muted">
          {mode === 'login' ? (
            <>
              New here?{' '}
              <Link to="/register" className="font-medium text-accent hover:underline">
                Create an account
              </Link>
            </>
          ) : (
            <>
              Already have an account?{' '}
              <Link to="/login" className="font-medium text-accent hover:underline">
                Sign in
              </Link>
            </>
          )}
        </p>
      </div>
    </main>
  )
}
