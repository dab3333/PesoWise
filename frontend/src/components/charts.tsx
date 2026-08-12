import { useState } from 'react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { formatDate, formatPeso, formatPesoCompact, parseLocalDate, toNumber } from '@/lib/format'
import type { CategoryTotal, DailyTotal } from '@/api/types'
import { Button } from './ui'

/* Chart colours come from CSS variables, which SVG resolves natively — so the marks follow the
   theme swap with no JS and no re-render. Both sets are validated; see docs/design-system.md. */
const INCOME = 'var(--chart-income)'
const EXPENSE = 'var(--chart-expense)'
const GRID = 'var(--chart-grid)'
const AXIS_TEXT = 'var(--muted)'

const axisTick = { fontSize: 11, fill: AXIS_TEXT }

/**
 * Two independent touch quirks stack here:
 * 1. Recharts only recomputes the active tooltip point on `touchmove`, never on `touchstart` or
 *    `touchend` (see RechartsWrapper's touch handlers) — a real finger tap usually has enough
 *    jitter to fire a touchmove anyway, which is why this half-works on real devices, but a clean
 *    tap does nothing.
 * 2. The browser follows every tap with a synthetic mouse-compatibility sequence, ending in a
 *    `mouseleave` fired 10-25ms after the click as it parks the emulated pointer elsewhere. That
 *    leave reaches Recharts' own onMouseLeave and unconditionally hides the tooltip — with no way
 *    to intercept it, since Recharts dispatches its hide action before calling any handler we pass.
 * Firing a synthetic mousemove on touchstart reuses the (already-correct) mouse-hover path to fix
 * #1, and a second, delayed one re-asserts it after the trailing leave from #2 has had time to
 * land, so the tap's result is always what's left on screen.
 */
function forwardTouchAsHover(_state: unknown, event: React.TouchEvent<SVGGraphicsElement>) {
  const touch = event.touches[0]
  if (!touch) return
  const target = event.currentTarget
  const fire = () =>
    target.dispatchEvent(
      new MouseEvent('mousemove', { bubbles: true, clientX: touch.clientX, clientY: touch.clientY }),
    )
  fire()
  setTimeout(fire, 80)
}

/** Shared tooltip shell so both charts read identically. */
function TooltipShell({ title, rows }: { title: string; rows: Array<[string, string, string?]> }) {
  return (
    <div className="rounded-lg border border-line bg-surface px-3 py-2 text-xs shadow-sm">
      <p className="mb-1 font-medium text-ink">{title}</p>
      {rows.map(([label, value, color]) => (
        <p key={label} className="flex items-center gap-2 text-body">
          {color && (
            <span aria-hidden className="size-2 shrink-0 rounded-full" style={{ background: color }} />
          )}
          <span className="text-muted">{label}</span>
          <span className="tnum ml-auto font-medium text-ink">{value}</span>
        </p>
      ))}
    </div>
  )
}

/* ------------------------------------------------------------- spend by category
   One series over nominal categories, so every bar is the same colour — a per-bar ramp would
   double-encode length as hue. Horizontal, because Filipino category names are long
   ("Load & Internet") and would never fit under vertical columns. */

export function SpendByCategoryChart({ data }: { data: CategoryTotal[] }) {
  const [showTable, setShowTable] = useState(false)

  const rows = data
    .filter((row) => row.kind === 'EXPENSE' && toNumber(row.total) > 0)
    .sort((a, b) => toNumber(b.total) - toNumber(a.total))

  if (rows.length === 0) {
    return <p className="py-8 text-center text-sm text-muted">No spending recorded this month.</p>
  }

  // Each bar needs vertical room for its label; a fixed height would crush 13 categories.
  const height = Math.max(rows.length * 34 + 16, 140)

  return (
    <div>
      {showTable ? (
        <DataTable
          headers={['Category', 'Spent']}
          rows={rows.map((row) => [row.categoryName, formatPeso(row.total)])}
        />
      ) : (
        <ResponsiveContainer width="100%" height={height} className="chart-enter">
          <BarChart
            data={rows}
            layout="vertical"
            margin={{ top: 0, right: 56, bottom: 0, left: 0 }}
            onTouchStart={forwardTouchAsHover}
          >
            {/* Solid hairline verticals only — never dashed, which would read as a threshold. */}
            <CartesianGrid horizontal={false} stroke={GRID} />
            <XAxis
              type="number"
              tickFormatter={(value: number) => formatPesoCompact(value)}
              tick={axisTick}
              axisLine={false}
              tickLine={false}
            />
            <YAxis
              type="category"
              dataKey="categoryName"
              width={112}
              tick={axisTick}
              axisLine={false}
              tickLine={false}
            />
            <Tooltip
              cursor={{ fill: 'var(--surface-muted)' }}
              // Params are left unannotated so Recharts' own generics apply contextually;
              // naming them explicitly fights the library's variance.
              content={({ active, payload }) => {
                const row = active && payload?.length ? (payload[0].payload as CategoryTotal) : null
                return row ? (
                  <TooltipShell title={row.categoryName} rows={[['Spent', formatPeso(row.total)]]} />
                ) : null
              }}
            />
            <Bar dataKey="total" radius={[0, 4, 4, 0]} barSize={14} isAnimationActive={false}>
              {rows.map((row) => (
                // Same fill for every bar; the stored category colour is an identity dot
                // elsewhere, not an encoding channel here.
                <Cell key={row.categoryId} fill="var(--chart-1)" />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      )}
      <TableToggle showTable={showTable} onToggle={() => setShowTable((value) => !value)} />
    </div>
  )
}

/* ----------------------------------------------------------------- daily trend
   Two series on ONE axis — both are pesos, so a second y-scale would invent a relationship
   that is not in the data. */

export function DailyTrendChart({ data }: { data: DailyTotal[] }) {
  const [showTable, setShowTable] = useState(false)

  const hasActivity = data.some((day) => toNumber(day.income) + toNumber(day.expense) > 0)
  if (!hasActivity) {
    return <p className="py-8 text-center text-sm text-muted">No activity recorded this month.</p>
  }

  const rows = data.map((day) => ({
    ...day,
    day: parseLocalDate(day.date).getDate(),
    incomeValue: toNumber(day.income),
    expenseValue: toNumber(day.expense),
  }))

  return (
    <div>
      {/* A legend is always present for two series, so identity is never colour-alone. */}
      <div className="mb-3 flex items-center gap-4 text-xs">
        <LegendSwatch color={INCOME} label="Income" />
        <LegendSwatch color={EXPENSE} label="Expense" />
      </div>

      {showTable ? (
        <DataTable
          headers={['Date', 'Income', 'Expense']}
          rows={rows
            .filter((row) => row.incomeValue > 0 || row.expenseValue > 0)
            .map((row) => [formatDate(row.date), formatPeso(row.income), formatPeso(row.expense)])}
        />
      ) : (
        <ResponsiveContainer width="100%" height={240} className="chart-enter">
          <LineChart
            data={rows}
            margin={{ top: 4, right: 8, bottom: 0, left: 0 }}
            onTouchStart={forwardTouchAsHover}
          >
            <CartesianGrid vertical={false} stroke={GRID} />
            <XAxis dataKey="day" tick={axisTick} axisLine={false} tickLine={false} minTickGap={16} />
            <YAxis
              tickFormatter={(value: number) => formatPesoCompact(value)}
              tick={axisTick}
              axisLine={false}
              tickLine={false}
              width={56}
            />
            <Tooltip
              // The crosshair, so a reader can line up a day without landing on the marker.
              cursor={{ stroke: GRID, strokeWidth: 1 }}
              content={({ active, payload }) => {
                const row = active && payload?.length
                  ? (payload[0].payload as (typeof rows)[number])
                  : null
                return row ? (
                  <TooltipShell
                    title={formatDate(row.date)}
                    rows={[
                      ['Income', formatPeso(row.income), INCOME],
                      ['Expense', formatPeso(row.expense), EXPENSE],
                    ]}
                  />
                ) : null
              }}
            />
            {/* 2px lines, 8px markers only on hover — a dot per day would be noise. */}
            <Line
              type="monotone"
              dataKey="incomeValue"
              stroke={INCOME}
              strokeWidth={2}
              dot={false}
              activeDot={{ r: 4, strokeWidth: 2, stroke: 'var(--surface)' }}
              isAnimationActive={false}
            />
            <Line
              type="monotone"
              dataKey="expenseValue"
              stroke={EXPENSE}
              strokeWidth={2}
              dot={false}
              activeDot={{ r: 4, strokeWidth: 2, stroke: 'var(--surface)' }}
              isAnimationActive={false}
            />
          </LineChart>
        </ResponsiveContainer>
      )}
      <TableToggle showTable={showTable} onToggle={() => setShowTable((value) => !value)} />
    </div>
  )
}

/* --------------------------------------------------------------------- shared */

function LegendSwatch({ color, label }: { color: string; label: string }) {
  return (
    <span className="flex items-center gap-1.5 text-muted">
      <span aria-hidden className="h-0.5 w-4 rounded-full" style={{ background: color }} />
      {label}
    </span>
  )
}

/** Every chart has a table twin, so no value is reachable only by hovering. */
function DataTable({ headers, rows }: { headers: string[]; rows: string[][] }) {
  return (
    <div className="max-h-64 overflow-auto">
      <table className="w-full text-sm">
        <thead className="sticky top-0 bg-surface-muted">
          <tr>
            {headers.map((header, index) => (
              <th
                key={header}
                className={`px-3 py-2 text-xs font-medium text-muted ${index === 0 ? 'text-left' : 'text-right'}`}
              >
                {header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row[0]} className="border-b border-line last:border-0">
              {row.map((cell, index) => (
                <td
                  key={index}
                  className={index === 0 ? 'px-3 py-2 text-body' : 'tnum px-3 py-2 text-right text-ink'}
                >
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function TableToggle({ showTable, onToggle }: { showTable: boolean; onToggle: () => void }) {
  return (
    <div className="mt-3 flex justify-end">
      <Button variant="ghost" onClick={onToggle} className="px-2 py-1 text-xs">
        {showTable ? 'Show chart' : 'Show table'}
      </Button>
    </div>
  )
}
