import { useState } from 'react'
import type { FormEvent } from 'react'
import { useAuth } from '@/auth/AuthContext'
import { PageHeader } from '@/components/PageHeader'
import { Alert, Button, Card, CardTitle, Field, TextArea } from '@/components/ui'
import { Select } from '@/components/form'
import { FEEDBACK_CATEGORY_LABELS, useSubmitFeedback } from '@/api/useFeedback'
import type { FeedbackCategory } from '@/api/useFeedback'
import { ApiError } from '@/lib/api'

/**
 * Obfuscated against the simplest scrapers (a plain `mailto:` in the markup is what most bots
 * look for) without turning the address into an image or a puzzle for a real visitor — it is
 * still one click to a working mail link once assembled client-side.
 */
function WorkEmailLink() {
  const user = 'mreyes2'
  const domain = 'vertere-gs.com'
  const [address] = useState(() => `${user}@${domain}`)
  return (
    <a href={`mailto:${address}`} className="font-medium text-accent hover:underline">
      {address}
    </a>
  )
}

export function AboutPage() {
  return (
    <>
      <PageHeader title="About PesoWise" subtitle="What this is, who built it, and how to reach us." />

      <div className="grid gap-6">
        <Card>
          <CardTitle>What this is</CardTitle>
          <p className="text-sm text-body">
            PesoWise is a personal budgeting app for the Philippine market: envelope-style monthly
            budgeting using the 70-20-10 method, debt (utang) tracking in both directions, savings
            goals, recurring bills, and a spending dashboard — built to answer three questions
            honestly: where did my money go, am I inside my budget, and am I making progress.
          </p>
        </Card>

        <Card>
          <CardTitle>Developer</CardTitle>
          <div className="flex flex-col gap-1 text-sm">
            <p className="font-medium text-ink">Mark Dave Reyes</p>
            <p className="text-muted">Programmer/Analyst at Vertere Global Solutions</p>
          </div>
          <div className="mt-4 flex flex-col gap-1.5 text-sm">
            <p>
              <span className="text-muted">Email — </span>
              <WorkEmailLink />
            </p>
            <p>
              <span className="text-muted">GitHub — </span>
              <a
                href="https://github.com/dab3333"
                target="_blank"
                rel="noreferrer"
                className="font-medium text-accent hover:underline"
              >
                github.com/dab3333
              </a>
            </p>
            <p>
              <span className="text-muted">LinkedIn — </span>
              <a
                href="https://www.linkedin.com/in/mark-dave-reyes/"
                target="_blank"
                rel="noreferrer"
                className="font-medium text-accent hover:underline"
              >
                linkedin.com/in/mark-dave-reyes
              </a>
            </p>
          </div>
        </Card>

        <Card>
          <CardTitle>Version</CardTitle>
          <p className="tnum text-sm text-muted">v{__APP_VERSION__}</p>
        </Card>

        <FeedbackCard />
      </div>
    </>
  )
}

function FeedbackCard() {
  const { user } = useAuth()
  const submitFeedback = useSubmitFeedback()

  const [category, setCategory] = useState<FeedbackCategory>('IDEA')
  const [subject, setSubject] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [sent, setSent] = useState(false)

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})

    submitFeedback.mutate(
      {
        category,
        userEmail: user?.email ?? '',
        userName: user?.displayName ?? '',
        subject,
        message,
      },
      {
        onSuccess: () => {
          setSent(true)
          setSubject('')
          setMessage('')
        },
        onError: (caught) => {
          if (caught instanceof ApiError) {
            setFieldErrors(caught.fieldErrors)
            setError(Object.keys(caught.fieldErrors).length > 0 ? null : caught.message)
          } else {
            setError('Could not send. Check your connection and try again.')
          }
        },
      },
    )
  }

  return (
    <Card>
      <CardTitle>Send feedback</CardTitle>

      {sent && (
        <p className="mb-4 text-sm text-income">Thanks — that's been sent through.</p>
      )}

      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        {error && <Alert>{error}</Alert>}

        <Select
          label="What's this about?"
          value={category}
          onChange={(event) => setCategory(event.target.value as FeedbackCategory)}
        >
          {Object.entries(FEEDBACK_CATEGORY_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Select>

        <Field
          label="Subject"
          value={subject}
          onChange={(event) => setSubject(event.target.value)}
          error={fieldErrors.subject}
          required
        />

        <TextArea
          label="Message"
          value={message}
          onChange={(event) => setMessage(event.target.value)}
          error={fieldErrors.message}
          required
        />

        <Button type="submit" loading={submitFeedback.isPending} className="self-start">
          Send feedback
        </Button>
      </form>
    </Card>
  )
}
