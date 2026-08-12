import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '@/auth/AuthContext'
import type { Gender, Occupation } from '@/auth/AuthContext'
import { AuthShell } from '@/components/AuthShell'
import { Select } from '@/components/form'
import { Alert, Button, Field, PasswordField } from '@/components/ui'
import { ApiError, api } from '@/lib/api'

type Mode = 'login' | 'register'

const GENDER_LABELS: Record<Gender, string> = {
  MALE: 'Male',
  FEMALE: 'Female',
  UNSPECIFIED: 'Prefer not to say',
}

// A Philippines-relevant list (OFW is a large enough share of the population to matter for a
// PH-focused finance app) rather than a generic HR taxonomy.
const OCCUPATION_LABELS: Record<Occupation, string> = {
  STUDENT: 'Student',
  EMPLOYED_PRIVATE: 'Employed — private sector',
  EMPLOYED_GOVERNMENT: 'Employed — government',
  SELF_EMPLOYED: 'Self-employed / freelancer',
  BUSINESS_OWNER: 'Business owner',
  OFW: 'OFW (Overseas Filipino Worker)',
  UNEMPLOYED: 'Unemployed',
  RETIRED: 'Retired',
  OTHER: 'Other',
}

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
 * Login and register share one layout and one submit path — the differences are the copy, the
 * extra name field, which auth call runs, and what success means. Signing in navigates; signing
 * up does not, because the account is not usable until the emailed link is followed.
 */
export function AuthPage({ mode }: { mode: Mode }) {
  const { login, register } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [age, setAge] = useState('')
  const [gender, setGender] = useState<Gender | ''>('')
  const [occupation, setOccupation] = useState<Occupation | ''>('')
  const [occupationOther, setOccupationOther] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  // Set when registration succeeds and the address still needs confirming — the form is replaced
  // rather than left on screen, because there is nothing useful left to do with it.
  const [pendingEmail, setPendingEmail] = useState<string | null>(null)
  // Set when a correct password is refused because the address was never confirmed.
  const [unverified, setUnverified] = useState(false)

  // Send the user back to whatever they were trying to reach before the guard intervened.
  const redirectTo = (location.state as { from?: string } | null)?.from ?? '/'

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    setFieldErrors({})
    setUnverified(false)

    try {
      if (mode === 'login') {
        await login(email, password)
        navigate(redirectTo, { replace: true })
      } else {
        // Nothing on the backend receives confirmPassword — it exists purely so a typo doesn't
        // silently create an account with a password the user can't reproduce.
        if (password !== confirmPassword) {
          setFieldErrors({ confirmPassword: "Passwords don't match." })
          return
        }

        const result = await register({
          email,
          password,
          firstName,
          lastName,
          age: Number(age),
          gender: gender as Gender,
          occupation: occupation as Occupation,
          occupationOther: occupation === 'OTHER' ? occupationOther : undefined,
        })
        // With delivery switched off there is no inbox to check, so send them to sign in.
        if (result.verified) navigate('/login', { replace: true })
        else setPendingEmail(result.email)
      }
    } catch (caught) {
      if (caught instanceof ApiError) {
        setFieldErrors(caught.fieldErrors)
        // With field errors present, the banner would just repeat "check the fields".
        setError(Object.keys(caught.fieldErrors).length > 0 ? null : caught.message)
        setUnverified(caught.code === 'EMAIL_NOT_VERIFIED')
      } else {
        setError('Could not reach the server. Check your connection and try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (pendingEmail) {
    return <CheckYourInbox email={pendingEmail} />
  }

  const { title, subtitle, submit } = copy[mode]

  return (
    <AuthShell
      title={title}
      subtitle={subtitle}
      footer={
        mode === 'login' ? (
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
        )
      }
    >
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        {error && <Alert>{error}</Alert>}
        {unverified && <ResendVerification email={email} />}

        {mode === 'register' && (
          <>
            <div className="grid grid-cols-2 gap-3">
              <Field
                label="First name"
                autoComplete="given-name"
                value={firstName}
                onChange={(event) => setFirstName(event.target.value)}
                error={fieldErrors.firstName}
                required
              />
              <Field
                label="Last name"
                autoComplete="family-name"
                value={lastName}
                onChange={(event) => setLastName(event.target.value)}
                error={fieldErrors.lastName}
                required
              />
            </div>

            <Field
              label="Age"
              type="number"
              inputMode="numeric"
              min={1}
              max={120}
              value={age}
              onChange={(event) => setAge(event.target.value)}
              error={fieldErrors.age}
              required
            />

            <Select
              label="Gender"
              value={gender}
              onChange={(event) => setGender(event.target.value as Gender)}
              error={fieldErrors.gender}
              required
            >
              <option value="" disabled>
                Choose one
              </option>
              {Object.entries(GENDER_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>

            <Select
              label="Occupation"
              value={occupation}
              onChange={(event) => setOccupation(event.target.value as Occupation)}
              error={fieldErrors.occupation}
              required
            >
              <option value="" disabled>
                Choose one
              </option>
              {Object.entries(OCCUPATION_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>

            {occupation === 'OTHER' && (
              <Field
                label="What do you do?"
                value={occupationOther}
                onChange={(event) => setOccupationOther(event.target.value)}
                error={fieldErrors.occupationOther}
                required
              />
            )}
          </>
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

        <PasswordField
          label="Password"
          autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          error={fieldErrors.password}
          hint={mode === 'register' ? 'At least 8 characters.' : undefined}
          required
        />

        {mode === 'register' && (
          <PasswordField
            label="Confirm password"
            autoComplete="new-password"
            value={confirmPassword}
            onChange={(event) => setConfirmPassword(event.target.value)}
            error={fieldErrors.confirmPassword}
            required
          />
        )}

        {mode === 'login' && (
          <Link
            to="/forgot-password"
            className="-mt-1 self-start text-sm font-medium text-accent hover:underline"
          >
            Forgot your password?
          </Link>
        )}

        <Button type="submit" loading={submitting} className="mt-2 w-full">
          {submit}
        </Button>
      </form>
    </AuthShell>
  )
}

/** Replaces the form after signing up. */
function CheckYourInbox({ email }: { email: string }) {
  return (
    <AuthShell
      title="Check your email"
      subtitle={`We sent a confirmation link to ${email}.`}
      footer={
        <Link to="/login" className="font-medium text-accent hover:underline">
          Back to sign in
        </Link>
      }
    >
      <div className="flex flex-col gap-4">
        <p className="text-sm text-muted">
          Follow the link to finish setting up your account. It works for 24 hours.
        </p>
        <ResendVerification email={email} />
      </div>
    </AuthShell>
  )
}

/**
 * Offers another confirmation link.
 *
 * <p>The server answers 204 whether or not the address exists, so this can only ever report that
 * it asked — never whether anything was sent. Claiming "sent!" would be a small lie that turns
 * the button into an account-existence check.
 */
function ResendVerification({ email }: { email: string }) {
  const [state, setState] = useState<'idle' | 'sending' | 'sent'>('idle')

  async function resend() {
    setState('sending')
    try {
      await api.post('/api/auth/resend-verification', { email }, { skipAuthRedirect: true })
    } catch {
      // Nothing actionable to show: the endpoint reports nothing either way by design.
    }
    setState('sent')
  }

  if (state === 'sent') {
    return (
      <p className="text-sm text-muted">
        If that address needs confirming, a new link is on its way.
      </p>
    )
  }

  return (
    <Button
      type="button"
      variant="secondary"
      loading={state === 'sending'}
      disabled={!email}
      onClick={resend}
    >
      Send the link again
    </Button>
  )
}
