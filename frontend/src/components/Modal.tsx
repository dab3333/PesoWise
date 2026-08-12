import { useEffect, useRef } from 'react'
import type { ReactNode } from 'react'

/**
 * Uses the native <dialog> element, which brings focus trapping, Escape handling, and the
 * top-layer backdrop for free rather than reimplementing them.
 */
export function Modal({
  open,
  onClose,
  title,
  children,
}: {
  open: boolean
  onClose: () => void
  title: string
  children: ReactNode
}) {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return

    if (open && !dialog.open) dialog.showModal()
    else if (!open && dialog.open) dialog.close()
  }, [open])

  if (!open) return null

  return (
    <dialog
      ref={ref}
      // Escape fires 'cancel'; 'close' also covers programmatic closes.
      onCancel={(event) => {
        event.preventDefault()
        onClose()
      }}
      onClose={onClose}
      className="m-auto w-[calc(100%-2rem)] max-w-md rounded-xl border border-line bg-surface p-0 text-body backdrop:bg-slate-900/40"
    >
      <div className="flex items-center justify-between border-b border-line px-5 py-4">
        <h2 className="text-base font-semibold text-ink">{title}</h2>
        <button
          type="button"
          onClick={onClose}
          aria-label="Close"
          className="grid size-8 place-items-center rounded-lg text-muted hover:bg-surface-muted hover:text-body"
        >
          <svg
            aria-hidden
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.75"
            strokeLinecap="round"
            className="size-4"
          >
            <path d="M6 6l12 12M18 6L6 18" />
          </svg>
        </button>
      </div>
      <div className="p-5">{children}</div>
    </dialog>
  )
}

/** Confirmation for a destructive action. Solid red appears only here, never on the trigger. */
export function ConfirmDialog({
  open,
  onClose,
  onConfirm,
  title,
  message,
  confirmLabel = 'Delete',
  loading = false,
}: {
  open: boolean
  onClose: () => void
  onConfirm: () => void
  title: string
  message: string
  confirmLabel?: string
  loading?: boolean
}) {
  return (
    <Modal open={open} onClose={onClose} title={title}>
      <p className="text-sm text-body">{message}</p>
      <div className="mt-5 flex justify-end gap-2">
        <button
          type="button"
          onClick={onClose}
          className="rounded-lg border border-line bg-surface px-4 py-2 text-sm font-medium hover:bg-surface-muted"
        >
          Cancel
        </button>
        <button
          type="button"
          onClick={onConfirm}
          disabled={loading}
          className="rounded-lg bg-expense px-4 py-2 text-sm font-medium text-white hover:opacity-90 disabled:opacity-50"
        >
          {confirmLabel}
        </button>
      </div>
    </Modal>
  )
}
