import { useState } from 'react'
import { PageHeader } from '@/components/PageHeader'
import { Modal } from '@/components/Modal'
import { Select } from '@/components/form'
import { Button, Card, EmptyState, TextArea } from '@/components/ui'
import {
  FEEDBACK_CATEGORY_LABELS,
  FEEDBACK_STATUS_LABELS,
  useAdminFeedback,
  useUpdateFeedbackStatus,
} from '@/api/useFeedback'
import type { Feedback, FeedbackStatus } from '@/api/useFeedback'
import { formatTimestamp } from '@/lib/format'

const PAGE_SIZE = 25

export function AdminFeedbackPage() {
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<Feedback | null>(null)

  const feedback = useAdminFeedback({ status: status || undefined, page, size: PAGE_SIZE })
  const rows = feedback.data?.items ?? []
  const totalPages = feedback.data?.totalPages ?? 0

  return (
    <>
      <PageHeader title="Feedback" subtitle="What people are telling us." />

      <div className="mb-4 max-w-xs">
        <Select
          label="Status"
          value={status}
          onChange={(event) => {
            setStatus(event.target.value)
            setPage(0)
          }}
        >
          <option value="">All</option>
          {Object.entries(FEEDBACK_STATUS_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Select>
      </div>

      <Card className="p-0">
        {rows.length === 0 && !feedback.isLoading ? (
          <EmptyState message="Nothing here yet." />
        ) : (
          <div className={feedback.isFetching ? 'opacity-50 transition-opacity' : ''}>
            <ul className="divide-y divide-line">
              {rows.map((row) => (
                <li key={row.id}>
                  <button
                    type="button"
                    onClick={() => setSelected(row)}
                    className="flex w-full items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-surface-muted"
                  >
                    <StatusBadge status={row.status} />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-ink">{row.subject}</p>
                      <p className="truncate text-xs text-muted">
                        {FEEDBACK_CATEGORY_LABELS[row.category]} · {row.userName} ·{' '}
                        {formatTimestamp(row.createdAt)}
                      </p>
                    </div>
                  </button>
                </li>
              ))}
            </ul>

            {totalPages > 1 && (
              <div className="flex items-center justify-between border-t border-line px-4 py-3">
                <span className="text-xs text-muted">
                  Page {page + 1} of {totalPages} · {feedback.data?.totalItems} total
                </span>
                <span className="flex gap-2">
                  <Button variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>
                    Previous
                  </Button>
                  <Button
                    variant="secondary"
                    disabled={page + 1 >= totalPages}
                    onClick={() => setPage(page + 1)}
                  >
                    Next
                  </Button>
                </span>
              </div>
            )}
          </div>
        )}
      </Card>

      {selected && <FeedbackDetail feedback={selected} onClose={() => setSelected(null)} />}
    </>
  )
}

function StatusBadge({ status }: { status: FeedbackStatus }) {
  const tone =
    status === 'NEW'
      ? 'bg-info/10 text-info'
      : status === 'REVIEWING'
        ? 'bg-warning/10 text-warning'
        : 'bg-income/10 text-income'

  return (
    <span className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-medium ${tone}`}>
      {FEEDBACK_STATUS_LABELS[status]}
    </span>
  )
}

function FeedbackDetail({ feedback, onClose }: { feedback: Feedback; onClose: () => void }) {
  const updateStatus = useUpdateFeedbackStatus()
  const [status, setStatus] = useState<FeedbackStatus>(feedback.status)
  const [adminNote, setAdminNote] = useState(feedback.adminNote ?? '')

  function onSave() {
    updateStatus.mutate(
      { id: feedback.id, input: { status, adminNote: adminNote.trim() || undefined } },
      { onSuccess: onClose },
    )
  }

  return (
    <Modal open onClose={onClose} title={feedback.subject}>
      <div className="flex flex-col gap-4">
        <div className="text-xs text-muted">
          {FEEDBACK_CATEGORY_LABELS[feedback.category]} · {feedback.userName} ({feedback.userEmail}) ·{' '}
          {formatTimestamp(feedback.createdAt)}
        </div>

        <p className="rounded-lg bg-surface-muted p-3 text-sm whitespace-pre-wrap text-body">
          {feedback.message}
        </p>

        <Select
          label="Status"
          value={status}
          onChange={(event) => setStatus(event.target.value as FeedbackStatus)}
        >
          {Object.entries(FEEDBACK_STATUS_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Select>

        <TextArea
          label="Admin note"
          hint="Visible to other admins only."
          value={adminNote}
          onChange={(event) => setAdminNote(event.target.value)}
        />

        <div className="mt-1 flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="button" loading={updateStatus.isPending} onClick={onSave}>
            Save
          </Button>
        </div>
      </div>
    </Modal>
  )
}
