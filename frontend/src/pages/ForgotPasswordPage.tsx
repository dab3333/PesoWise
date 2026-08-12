import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { AuthShell } from '@/components/AuthShell'
import { Alert, Button, Field } from '@/components/ui'
import { ApiError, api } from '@/lib/api'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    setFieldErrors({})

    try {
      await api.post('/api/auth/forgot-password', { email }, { skipAuthRedirect: true })
      setSent(true)
    } catch (caught) {
      // Only a malformed address or an unreachable server can land here — the endpoint answers
      // 204 for both known and unknown accounts.
      if (caught instanceof ApiError) {
        setFieldErrors(caught.fieldErrors)
        setError(Object.keys(caught.fieldErrors).length > 0 ? null : caught.message)
      } else {
        setError('Could not reach the server. Check your connection and try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  const backToSignIn = (
    <Link to="/login" className="font-medium text-accent hover:underline">
      Back to sign in
    </Link>
  )

  if (sent) {
    return (
      <AuthShell title="Check your email" footer={backToSignIn}>
        {/* Phrased as a conditional on purpose: confirming that an address is registered would
            turn this form into a way to enumerate accounts. */}
        <p className="text-sm text-muted">
          If an account exists for <span className="font-medium text-ink">{email}</span>, a reset
          link is on its way. It works for one hour and can only be used once.
        </p>
      </AuthShell>
    )
  }

  return (
    <AuthShell
      title="Reset your password"
      subtitle="We'll email you a link to choose a new one."
      footer={backToSignIn}
    >
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        {error && <Alert>{error}</Alert>}

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

        <Button type="submit" loading={submitting} className="mt-2 w-full">
          Send reset link
        </Button>
      </form>
    </AuthShell>
  )
}
