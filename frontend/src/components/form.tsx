import {
  Children,
  forwardRef,
  isValidElement,
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  useState,
} from 'react'
import type { ChangeEvent, KeyboardEvent, ReactElement, ReactNode, Ref, RefObject } from 'react'
import { cn } from './ui'
import { formatDate, parseLocalDate, toDateKey } from '@/lib/format'

/** Assigns a DOM node to both a forwarded ref and an internal one — used so a component can keep
 *  its own ref for measurements (positioning a popover) while still supporting forwardRef. */
function mergeRefs<T>(internal: RefObject<T | null>, forwarded: Ref<T>) {
  return (node: T | null) => {
    internal.current = node
    if (typeof forwarded === 'function') forwarded(node)
    else if (forwarded) (forwarded as { current: T | null }).current = node
  }
}

interface PopoverPosition {
  top: number
  left: number
  width: number
}

/**
 * Positions a floating panel with `position: fixed` — relative to the viewport, not to whatever
 * scrollable ancestor it happens to sit inside (a form dialog's own content box) — and flips it
 * above the trigger, or clamps it, whenever there isn't room below. Without this, a panel opened
 * near the bottom of a scrolling dialog only shows its top portion, and the user has to scroll the
 * dialog itself to see the rest, even though the panel's own height would fit the screen just fine
 * positioned somewhere else on it.
 */
function usePopoverPosition(
  open: boolean,
  triggerRef: RefObject<HTMLElement | null>,
  panelRef: RefObject<HTMLElement | null>,
): PopoverPosition | null {
  const [position, setPosition] = useState<PopoverPosition | null>(null)

  useLayoutEffect(() => {
    if (!open) {
      setPosition(null)
      return
    }

    function reposition() {
      const trigger = triggerRef.current
      if (!trigger) return
      const triggerRect = trigger.getBoundingClientRect()
      const panel = panelRef.current
      const panelHeight = panel?.offsetHeight ?? 0
      const panelWidth = panel?.offsetWidth ?? triggerRect.width
      const gap = 4
      const margin = 8
      const viewportHeight = window.innerHeight
      const viewportWidth = window.innerWidth

      const spaceBelow = viewportHeight - triggerRect.bottom
      const spaceAbove = triggerRect.top
      const openUpward = spaceBelow < panelHeight + gap + margin && spaceAbove > spaceBelow

      let top = openUpward ? triggerRect.top - panelHeight - gap : triggerRect.bottom + gap
      top = Math.max(margin, Math.min(top, viewportHeight - panelHeight - margin))

      let left = triggerRect.left
      left = Math.max(margin, Math.min(left, viewportWidth - panelWidth - margin))

      setPosition({ top, left, width: triggerRect.width })
    }

    reposition()
    // The panel's real height isn't known until it has actually painted once — a Select with
    // many options or the DateField's calendar grid — so measure again on the next frame.
    const raf = requestAnimationFrame(reposition)
    window.addEventListener('resize', reposition)
    // Capture phase: scroll doesn't bubble, and the dialog's own content is the scrolling
    // ancestor here, so capturing on document is the only way to hear it move.
    document.addEventListener('scroll', reposition, true)
    return () => {
      cancelAnimationFrame(raf)
      window.removeEventListener('resize', reposition)
      document.removeEventListener('scroll', reposition, true)
    }
  }, [open, triggerRef, panelRef])

  return position
}

/* ------------------------------------------------------------------- Select
   A native <select>'s open dropdown is rendered by the browser/OS, not the page — no CSS from
   here can ever reach it. On some mobile browsers that popup renders with a much larger font than
   the closed control, which reads as "broken" no matter how the trigger itself is styled. Built
   from a button + our own absolutely-positioned listbox instead, so the open state looks the same
   everywhere. Callers still pass <option>/<optgroup> children and get an event.target.value in
   onChange, exactly like the native element it replaces — nothing else needed to change. */

interface SelectProps {
  label: string
  error?: string
  value: string
  onChange: (event: ChangeEvent<HTMLSelectElement>) => void
  children: ReactNode
  disabled?: boolean
  required?: boolean
  className?: string
  id?: string
}

interface OptionRow {
  kind: 'option'
  key: string
  value: string
  label: string
  disabled?: boolean
  optIndex: number
}
interface HeadingRow {
  kind: 'heading'
  key: string
  label: string
}
type Row = OptionRow | HeadingRow

/** Reads the same <option>/<optgroup> children a native select would take. */
function parseRows(children: ReactNode): Row[] {
  const rows: Row[] = []
  let rowKey = 0
  let optIndex = 0

  function readOption(el: ReactElement) {
    const props = el.props as { value?: unknown; children?: ReactNode; disabled?: boolean }
    rows.push({
      kind: 'option',
      key: `r${rowKey++}`,
      value: String(props.value ?? ''),
      label: typeof props.children === 'string' ? props.children : String(props.children ?? ''),
      disabled: Boolean(props.disabled),
      optIndex: optIndex++,
    })
  }

  Children.forEach(children, (child) => {
    if (!isValidElement(child)) return
    if (child.type === 'optgroup') {
      const groupProps = child.props as { label?: string; children?: ReactNode }
      rows.push({ kind: 'heading', key: `r${rowKey++}`, label: groupProps.label ?? '' })
      Children.forEach(groupProps.children, (opt) => {
        if (isValidElement(opt) && opt.type === 'option') readOption(opt)
      })
    } else if (child.type === 'option') {
      readOption(child)
    }
  })

  return rows
}

function nextEnabledIndex(options: OptionRow[], current: number, direction: 1 | -1): number {
  if (options.length === 0) return current
  let index = current
  for (let step = 0; step < options.length; step++) {
    index = (index + direction + options.length) % options.length
    if (!options[index]?.disabled) return index
  }
  return current
}

export const Select = forwardRef<HTMLButtonElement, SelectProps>(function Select(
  { label, error, value, onChange, children, disabled, className, id },
  ref,
) {
  const generatedId = useId()
  const selectId = id ?? generatedId
  const labelId = `${selectId}-label`

  const rows = parseRows(children)
  const options = rows.filter((row): row is OptionRow => row.kind === 'option')
  const selected = options.find((opt) => opt.value === value)

  const [open, setOpen] = useState(false)
  const [highlighted, setHighlighted] = useState(0)
  const containerRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const panelRef = useRef<HTMLUListElement>(null)
  const position = usePopoverPosition(open, triggerRef, panelRef)

  // A dropdown menu closes on an outside tap — unlike the app's form modals, which stay open
  // until an explicit close (see Modal.tsx), this is a lightweight per-field menu, not a form.
  useEffect(() => {
    if (!open) return
    function onPointerDown(event: PointerEvent) {
      if (!containerRef.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('pointerdown', onPointerDown)
    return () => document.removeEventListener('pointerdown', onPointerDown)
  }, [open])

  useEffect(() => {
    if (open) {
      const index = options.findIndex((opt) => opt.value === value)
      setHighlighted(index >= 0 ? index : 0)
    }
    // Only reset the highlight when the menu opens, not on every value/options change while open.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  function commit(next: string) {
    onChange({ target: { value: next } } as ChangeEvent<HTMLSelectElement>)
    setOpen(false)
  }

  // Focus stays on the trigger button throughout — the highlighted option is communicated via
  // aria-activedescendant, the standard combobox pattern, rather than moving focus into the list.
  function onKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (disabled) return
    if (!open) {
      if (['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(event.key)) {
        event.preventDefault()
        setOpen(true)
      }
      return
    }
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setHighlighted((index) => nextEnabledIndex(options, index, 1))
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setHighlighted((index) => nextEnabledIndex(options, index, -1))
    } else if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      const opt = options[highlighted]
      if (opt && !opt.disabled) commit(opt.value)
    } else if (event.key === 'Escape') {
      event.preventDefault()
      setOpen(false)
    }
  }

  const activeOption = options[highlighted]
  const activeId = activeOption ? `${selectId}-opt-${activeOption.optIndex}` : undefined

  return (
    <div ref={containerRef} className="flex flex-col gap-1.5">
      <label id={labelId} htmlFor={selectId} className="text-sm font-medium text-ink">
        {label}
      </label>
      <button
        ref={mergeRefs(triggerRef, ref)}
        type="button"
        id={selectId}
        disabled={disabled}
        role="combobox"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={`${selectId}-listbox`}
        aria-labelledby={labelId}
        aria-invalid={error ? true : undefined}
        aria-activedescendant={open ? activeId : undefined}
        onClick={() => setOpen((wasOpen) => !wasOpen)}
        onKeyDown={onKeyDown}
        className={cn(
          'flex items-center justify-between gap-2 rounded-lg border bg-surface px-3 py-2 text-left text-sm',
          'focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent',
          disabled ? 'cursor-not-allowed opacity-50' : 'cursor-pointer',
          error ? 'border-expense' : 'border-line',
          className,
        )}
      >
        <span className={cn('truncate', selected?.disabled ? 'text-subtle' : 'text-ink')}>
          {selected?.label ?? ''}
        </span>
        <svg
          aria-hidden
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.75"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="size-4 shrink-0 text-muted"
        >
          <path d="M6 9l6 6 6-6" />
        </svg>
      </button>

      {open && (
        <ul
          ref={panelRef}
          id={`${selectId}-listbox`}
          role="listbox"
          aria-labelledby={labelId}
          style={
            position
              ? { position: 'fixed', top: position.top, left: position.left, width: position.width }
              : { position: 'fixed', visibility: 'hidden' }
          }
          className="z-30 max-h-60 overflow-auto rounded-lg border border-line bg-surface py-1 text-sm"
        >
          {rows.map((row) => {
            if (row.kind === 'heading') {
              return (
                <li
                  key={row.key}
                  role="presentation"
                  className="px-3 py-1 text-xs font-medium uppercase tracking-wide text-muted"
                >
                  {row.label}
                </li>
              )
            }
            const isHighlighted = activeOption?.key === row.key
            return (
              <li
                key={row.key}
                id={`${selectId}-opt-${row.optIndex}`}
                role="option"
                aria-selected={row.value === value}
                aria-disabled={row.disabled}
                onClick={() => !row.disabled && commit(row.value)}
                className={cn(
                  'px-3 py-2',
                  row.disabled
                    ? 'cursor-not-allowed text-subtle'
                    : cn(
                        'cursor-pointer',
                        row.value === value
                          ? 'bg-accent-soft text-accent'
                          : isHighlighted
                            ? 'bg-surface-muted text-ink'
                            : 'text-ink',
                      ),
                )}
              >
                {row.label}
              </li>
            )
          })}
        </ul>
      )}

      {error && <span className="text-xs text-expense">{error}</span>}
    </div>
  )
})

/* ---------------------------------------------------------------- DateField
   Same reasoning as Select: a native <input type="date">'s open calendar is browser/OS chrome,
   not page content, and on mobile it tends to render as a large full-width picker with no way to
   restyle or resize it from CSS. Built from a button + our own calendar grid instead, so the open
   state is the same compact size everywhere. Same value shape as the native input it replaces —
   a YYYY-MM-DD string in, an event with that same shape in .target.value out. */

const WEEKDAY_LABELS = ['S', 'M', 'T', 'W', 'T', 'F', 'S']

function startOfMonth(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), 1)
}

function addMonths(date: Date, delta: number): Date {
  return new Date(date.getFullYear(), date.getMonth() + delta, 1)
}

function addDays(date: Date, delta: number): Date {
  const next = new Date(date)
  next.setDate(next.getDate() + delta)
  return next
}

function isSameDay(a: Date, b: Date): boolean {
  return (
    a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
  )
}

/** 42 cells (6 full weeks) so the grid is a constant height regardless of the month's length. */
function buildMonthGrid(viewMonth: Date): Date[] {
  const first = startOfMonth(viewMonth)
  const gridStart = addDays(first, -first.getDay())
  return Array.from({ length: 42 }, (_, i) => addDays(gridStart, i))
}

const monthLabelFormatter = new Intl.DateTimeFormat('en-PH', { month: 'long', year: 'numeric' })

interface DateFieldProps {
  label: string
  value: string
  onChange: (event: ChangeEvent<HTMLInputElement>) => void
  error?: string
  hint?: string
  required?: boolean
  id?: string
}

export const DateField = forwardRef<HTMLButtonElement, DateFieldProps>(function DateField(
  { label, value, onChange, error, hint, id },
  ref,
) {
  const generatedId = useId()
  const fieldId = id ?? generatedId
  const labelId = `${fieldId}-label`

  const selectedDate = value ? parseLocalDate(value) : null
  const today = new Date()

  const [open, setOpen] = useState(false)
  const [viewMonth, setViewMonth] = useState(() => startOfMonth(selectedDate ?? today))
  const [focused, setFocused] = useState(() => selectedDate ?? today)
  const containerRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const panelRef = useRef<HTMLDivElement>(null)
  const position = usePopoverPosition(open, triggerRef, panelRef)

  useEffect(() => {
    if (!open) return
    function onPointerDown(event: PointerEvent) {
      if (!containerRef.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('pointerdown', onPointerDown)
    return () => document.removeEventListener('pointerdown', onPointerDown)
  }, [open])

  function openAt(date: Date) {
    setViewMonth(startOfMonth(date))
    setFocused(date)
    setOpen(true)
  }

  function commit(date: Date) {
    onChange({ target: { value: toDateKey(date) } } as ChangeEvent<HTMLInputElement>)
    setOpen(false)
  }

  function moveFocus(days: number) {
    const next = addDays(focused, days)
    setFocused(next)
    if (next.getMonth() !== viewMonth.getMonth() || next.getFullYear() !== viewMonth.getFullYear()) {
      setViewMonth(startOfMonth(next))
    }
  }

  function onKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (!open) {
      if (['ArrowDown', 'Enter', ' '].includes(event.key)) {
        event.preventDefault()
        openAt(selectedDate ?? today)
      }
      return
    }
    const moves: Record<string, number> = {
      ArrowLeft: -1,
      ArrowRight: 1,
      ArrowUp: -7,
      ArrowDown: 7,
    }
    if (event.key in moves) {
      event.preventDefault()
      moveFocus(moves[event.key])
    } else if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      commit(focused)
    } else if (event.key === 'Escape') {
      event.preventDefault()
      setOpen(false)
    }
  }

  const grid = buildMonthGrid(viewMonth)

  return (
    <div ref={containerRef} className="flex flex-col gap-1.5">
      <label id={labelId} htmlFor={fieldId} className="text-sm font-medium text-ink">
        {label}
      </label>
      <button
        ref={mergeRefs(triggerRef, ref)}
        type="button"
        id={fieldId}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-labelledby={labelId}
        aria-invalid={error ? true : undefined}
        onClick={() => (open ? setOpen(false) : openAt(selectedDate ?? today))}
        onKeyDown={onKeyDown}
        className={cn(
          'flex items-center justify-between gap-2 rounded-lg border bg-surface px-3 py-2 text-left text-sm',
          'focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent',
          'cursor-pointer',
          error ? 'border-expense' : 'border-line',
        )}
      >
        <span className={cn('truncate', selectedDate ? 'text-ink' : 'text-subtle')}>
          {selectedDate ? formatDate(value) : 'Select a date'}
        </span>
        <svg
          aria-hidden
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.75"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="size-4 shrink-0 text-muted"
        >
          <rect x="3" y="5" width="18" height="16" rx="2" />
          <path d="M3 10h18M8 3v4M16 3v4" />
        </svg>
      </button>

      {open && (
        <div
          ref={panelRef}
          role="dialog"
          aria-modal="false"
          aria-labelledby={labelId}
          style={
            position
              ? { position: 'fixed', top: position.top, left: position.left }
              : { position: 'fixed', visibility: 'hidden' }
          }
          className="z-30 w-72 rounded-xl border border-line bg-surface p-3"
        >
          <div className="mb-2 flex items-center justify-between">
            <button
              type="button"
              aria-label="Previous month"
              onClick={() => setViewMonth((month) => addMonths(month, -1))}
              className="grid size-7 place-items-center rounded-lg text-muted hover:bg-surface-muted hover:text-body"
            >
              <svg aria-hidden viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className="size-4">
                <path d="M15 6l-6 6 6 6" />
              </svg>
            </button>
            <span className="text-sm font-medium text-ink">{monthLabelFormatter.format(viewMonth)}</span>
            <button
              type="button"
              aria-label="Next month"
              onClick={() => setViewMonth((month) => addMonths(month, 1))}
              className="grid size-7 place-items-center rounded-lg text-muted hover:bg-surface-muted hover:text-body"
            >
              <svg aria-hidden viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className="size-4">
                <path d="M9 6l6 6-6 6" />
              </svg>
            </button>
          </div>

          <div className="grid grid-cols-7 text-center text-xs text-muted">
            {WEEKDAY_LABELS.map((day, index) => (
              <span key={index} className="py-1">
                {day}
              </span>
            ))}
          </div>

          <div className="grid grid-cols-7 gap-0.5">
            {grid.map((day) => {
              const inMonth = day.getMonth() === viewMonth.getMonth()
              const isSelected = selectedDate && isSameDay(day, selectedDate)
              const isToday = isSameDay(day, today)
              const isFocused = isSameDay(day, focused)
              return (
                <button
                  key={day.toISOString()}
                  type="button"
                  tabIndex={-1}
                  onClick={() => commit(day)}
                  className={cn(
                    'aspect-square rounded-lg text-sm',
                    !inMonth && 'text-subtle',
                    inMonth && !isSelected && 'text-ink',
                    isSelected && 'bg-accent text-accent-on',
                    !isSelected && isToday && 'font-semibold text-accent',
                    !isSelected && isFocused && 'bg-surface-muted',
                    !isSelected && 'hover:bg-surface-muted',
                  )}
                >
                  {day.getDate()}
                </button>
              )
            })}
          </div>

          <button
            type="button"
            onClick={() => openAt(today)}
            className="mt-2 w-full rounded-lg py-1.5 text-center text-xs font-medium text-accent hover:bg-accent-soft"
          >
            Today
          </button>
        </div>
      )}

      {error ? (
        <span className="text-xs text-expense">{error}</span>
      ) : hint ? (
        <span className="text-xs text-muted">{hint}</span>
      ) : null}
    </div>
  )
})

/**
 * Amount input. Uses inputMode="decimal" so mobile shows a numeric keypad, and keeps the value as
 * a string all the way to the API — parsing to a float here would reintroduce the rounding error
 * that BigDecimal exists to avoid.
 */
export const MoneyInput = forwardRef<HTMLInputElement, {
  label: string
  value: string
  onChange: (value: string) => void
  error?: string
  required?: boolean
}>(function MoneyInput({ label, value, onChange, error, required }, ref) {
  const id = useId()

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium text-ink">
        {label}
      </label>
      <div className="relative">
        <span
          aria-hidden
          className="pointer-events-none absolute inset-y-0 left-3 grid place-items-center text-sm text-muted"
        >
          ₱
        </span>
        <input
          ref={ref}
          id={id}
          type="text"
          inputMode="decimal"
          required={required}
          value={value}
          // Allow only digits, one dot, and a leading minus (credit cards open negative).
          onChange={(event) => {
            const next = event.target.value
            if (next === '' || /^-?\d*\.?\d{0,2}$/.test(next)) onChange(next)
          }}
          placeholder="0.00"
          aria-invalid={error ? true : undefined}
          className={cn(
            'tnum w-full rounded-lg border bg-surface py-2 pr-3 pl-7 text-sm text-ink placeholder:text-subtle',
            'focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent',
            error ? 'border-expense' : 'border-line',
          )}
        />
      </div>
      {error && <span className="text-xs text-expense">{error}</span>}
    </div>
  )
})
