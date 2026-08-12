import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { AuthShell } from '@/components/AuthShell'
import { Button } from '@/components/ui'
import { ApiError, api } from '@/lib/api'

type State = 'verifying' | 'done' | 'failed' | 'missing'

/** Lands here from the confirmation email; the token is redeemed on arrival. */
export function VerifyEmailPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const token = params.get('token')

  const [state, setState] = useState<State>(token ? 'verifying' : 'missing')
  const [message, setMessage] = useState<string | null>(null)
  // StrictMode mounts effects twice in development. Without this the second run redeems an
  // already-spent token and the page reports a failure for a verification that just succeeded.
  const attempted = useRef(false)

  useEffect(() => {
    if (!token || attempted.current) return
    attempted.current = true

    let cancelled = false
    api
      .post('/api/auth/verify-email', { token }, { skipAuthRedirect: true })
      .then(() => {
        if (!cancelled) setState('done')
      })
      .catch((caught: unknown) => {
        if (cancelled) return
        setMessage(caught instanceof ApiError ? caught.message : 'Could not reach the server.')
        setState('failed')
      })

    return () => {
      cancelled = true
    }
  }, [token])

  const signIn = (
    <Link to="/login" className="font-medium text-accent hover:underline">
      Back to sign in
    </Link>
  )

  if (state === 'verifying') {
    return (
      <AuthShell title="Confirming your email">
        <p className="text-sm text-muted" role="status">
          One moment…
        </p>
      </AuthShell>
    )
  }

  if (state === 'done') {
    return (
      <AuthShell title="You're all set" subtitle="Your email address is confirmed.">
        <Button className="w-full" onClick={() => navigate('/login', { replace: true })}>
          Sign in
        </Button>
      </AuthShell>
    )
  }

  return (
    <AuthShell
      title={state === 'missing' ? 'That link is incomplete' : 'That link did not work'}
      footer={signIn}
    >
      <p className="text-sm text-muted">
        {state === 'missing'
          ? 'The confirmation link is missing its token. Some email clients split long links across lines — copying the whole thing should sort it.'
          : message}
      </p>
      <p className="mt-4 text-sm text-muted">
        You can request a new link from the sign-in page.
      </p>
    </AuthShell>
  )
}
