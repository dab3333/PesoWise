import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { AuthShell } from '@/components/AuthShell'
import { Alert, Button, Field } from '@/components/ui'
import { ApiError, api } from '@/lib/api'

export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const token = params.get('token')

  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const backToRequest = (
    <Link to="/forgot-password" className="font-medium text-accent hover:underline">
      Request a new link
    </Link>
  )

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})

    // Checked here rather than server-side: the confirmation field exists only to catch a typo
    // in this form, and the API has no use for a second copy of the password.
    if (password !== confirmation) {
      setFieldErrors({ confirmation: 'The two passwords do not match.' })
      return
    }

    setSubmitting(true)
    try {
      await api.post('/api/auth/reset-password', { token, password }, { skipAuthRedirect: true })
      // Straight to sign-in rather than signing them in: proving they can read the mailbox is
      // not the same as proving they know the password they just set.
      navigate('/login', { replace: true, state: { resetComplete: true } })
    } catch (caught) {
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

  if (!token) {
    return (
      <AuthShell title="That link is incomplete" footer={backToRequest}>
        <p className="text-sm text-muted">
          The reset link is missing its token. Some email clients split long links across lines —
          copying the whole thing, or requesting a fresh one, should sort it.
        </p>
      </AuthShell>
    )
  }

  return (
    <AuthShell
      title="Choose a new password"
      subtitle="This link can only be used once."
      footer={backToRequest}
    >
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        {error && <Alert>{error}</Alert>}

        <Field
          label="New password"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          error={fieldErrors.password}
          hint="At least 8 characters."
          required
        />

        <Field
          label="Confirm new password"
          type="password"
          autoComplete="new-password"
          value={confirmation}
          onChange={(event) => setConfirmation(event.target.value)}
          error={fieldErrors.confirmation}
          required
        />

        <Button type="submit" loading={submitting} className="mt-2 w-full">
          Set new password
        </Button>
      </form>
    </AuthShell>
  )
}
