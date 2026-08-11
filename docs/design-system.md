# PesoWise — Design Direction

Reference point: [Lista](https://www.lista.com.ph/personal-budgeting-app) — clean Philippine
personal-finance app, card-based, friendly. PesoWise keeps that clarity but takes its own
identity: **flat, minimalist, modern. No gradients anywhere.**

## Hard rules

1. **No gradients.** No `linear-gradient`, no `radial-gradient`, no gradient text, no gradient
   chart fills. Flat solid fills only.
2. **No glassmorphism, no heavy shadows.** Separation comes from a 1px border and whitespace.
   At most one very soft shadow, reserved for modals and dropdowns.
3. **One accent colour.** Jade carries brand and primary action. Everything else is neutral,
   except the semantic colours below, which only ever signal state — never decoration.
4. **Whitespace over ornament.** If a divider, box, or icon isn't doing work, remove it.

## Palette

Jade as the single brand accent — money-adjacent without being the generic "finance green,"
and it reads as distinctly Filipino-fintech alongside a cool slate neutral ramp.

```
Brand — Jade
  --jade-50   #ecfdf5      subtle fills, selected rows
  --jade-100  #d1fae5      badge backgrounds
  --jade-600  #0f8a6c      PRIMARY: buttons, active nav, focus ring, key chart series
  --jade-700  #0b6e56      hover / pressed
  --jade-900  #064e3b      headings on jade fills

Neutral — Slate
  --slate-50  #f8fafc      app background (light)
  --slate-100 #f1f5f9      subtle surface, table header
  --slate-200 #e2e8f0      BORDERS — the primary separation device
  --slate-400 #94a3b8      placeholder, disabled, axis labels
  --slate-500 #64748b      secondary text
  --slate-700 #334155      body text
  --slate-900 #0f172a      headings, primary numbers
  white       #ffffff      card surface (light)

Semantic — state only, never decoration
  income  / positive  #0f8a6c   (jade-600 — income is the good case, reuse the brand)
  expense / negative  #dc2626   red-600
  warning / near cap  #d97706   amber-600
  info                #2563eb   blue-600
```

**Dark mode** (`prefers-color-scheme` + a manual toggle): background `--slate-900`, surface
`#1a2333`, borders `#2b3648`, body text `--slate-200`, and the accent lightens to `#22b088`
so it stays legible on dark. Same tokens, swapped values — no separate stylesheet.

## Charts

### Form first, colour last

The form follows the reader's job, not habit. These are the decisions and why:

| Chart | Form | Why not the obvious thing |
| --- | --- | --- |
| Spend by category | **Horizontal bars**, sorted high→low, **all bars one colour** | Not a donut: with 13 categories a donut can't be compared, and long names ("Load & Internet") don't fit. It is one series over nominal categories, so a colour ramp would double-encode bar length as hue and say nothing new. |
| Daily trend | **Line**, 2 series, legend always shown | Two measures on **one axis** — both are pesos. Never a second y-scale. |
| Income / expense / net | **Stat tiles** | A three-bar chart of three numbers is a worse table. |
| 70-20-10 split | **Three meters**, actual against target | It is a ratio against a limit, which is a meter, not a pie of three slices. |
| Budget progress | **Meters**, one per category | Same job, same form — so they read as the same thing. |

### Palette — validated, not eyeballed

Chart marks use **separate tokens from text**. A mark needs 3:1 against the surface; text needs
WCAG contrast; the two land on different values. Text always wears text tokens — a coloured mark
beside a label carries the identity, the label itself never does.

Both sets were checked with the dataviz palette validator (lightness band, chroma floor, CVD
separation, normal-vision floor, contrast) and **pass all six checks**:

```
light — surface #ffffff              dark — surface #1a2333
  --chart-1  #0f8a6c  jade             --chart-1  #059669
  --chart-2  #2563eb  blue             --chart-2  #3b82f6
  --chart-3  #d97706  amber            --chart-3  #d97706
  --chart-4  #7c3aed  violet           --chart-4  #8b5cf6
  --chart-5  #db2777  pink             --chart-5  #ec4899
  --chart-6  #0891b2  cyan             --chart-6  #0891b2
  --chart-7  #65a30d  lime             --chart-7  #65a30d

  --chart-income  #0f8a6c              --chart-income  #059669
  --chart-expense #dc2626              --chart-expense #ef4444
```

**Dark is re-stepped, not flipped.** The dark lightness band (L 0.48–0.67) is narrower than
light's (0.43–0.77), so the light steps do not carry over — four of the seven had to move.

`--chart-other` (`#64748b` light / `#94a3b8` dark) is the de-emphasis and "Other" colour. It sits
**deliberately below the chroma floor** because it is not a categorical hue: it must never be
pressed into service as series 8. Past seven meaningful classes, fold the tail into "Other" or
show a table — never generate an eighth hue.

Category `color` values are stored per-category in the database and assigned from the light ramp
on seed. They are used for **identity dots** beside category names, not as a chart encoding
channel — which is why a fixed stored hex is safe in both themes.

### Mark and chrome rules

- Flat fills only. No gradient defs, ever.
- Bars: thin, `4px` rounded data-end anchored to the baseline, `2px` surface gap between
  adjacent bars.
- Lines: `2px`, markers ≥ `8px` with a `2px` surface ring where they overlap.
- Grid and axes: solid hairlines in `--chart-grid`, one shade off the surface. **Never dashed** —
  dashing reads as "threshold" when it is just a grid.
- Direct-label selectively (the endpoint, the extreme). Never a number on every point.
- Every chart has a **table-view twin**, so no value is reachable only by hovering.
- Hover: crosshair and tooltip on the line chart, per-mark tooltip on bars, with hit areas
  larger than the marks.
- On refetch, hold the previous render at reduced opacity. No skeleton flash, no layout jump.
- Filters (the month selector) sit in **one row above** everything they scope — never inside a
  chart card.

## Typography

- **Inter**, self-hosted via `@fontsource-variable/inter` — no CDN, so the app works offline
  and the Docker build has no external font dependency. Fallback: `system-ui, sans-serif`.
- Money and all figures use **tabular numerals** (`font-variant-numeric: tabular-nums`) so
  columns of pesos align. This is non-negotiable for a budgeting app.
- Scale: `text-3xl/semibold` page titles · `text-lg/semibold` card titles ·
  `text-sm` body · `text-xs/medium uppercase tracking-wide text-slate-500` labels.
- **Money formatting:** `₱1,234.56` via `Intl.NumberFormat('en-PH', {style:'currency',
  currency:'PHP'})` in one shared `formatPeso()` helper. Never hand-roll it per component.

## Shape and spacing

- Radius: `rounded-xl` (12px) cards · `rounded-lg` (8px) inputs and buttons · `rounded-full`
  pills and progress bars.
- Cards: `bg-white border border-slate-200 rounded-xl p-5`. No shadow.
- Spacing on a 4px grid; `gap-4` within a card, `gap-6` between cards.
- Focus ring is always visible and jade: `focus-visible:ring-2 ring-jade-600 ring-offset-2`.

## Layout

Persistent **left sidebar** (240px, collapses to icons under `lg`, becomes a bottom tab bar on
mobile): Dashboard · Transactions · Budgets · Debts · Goals · Recurring · Settings. Content
area maxes out at `max-w-6xl` so tables don't sprawl on wide monitors.

## Component notes

- **Buttons** — primary: flat `bg-jade-600` white text. Secondary: white with
  `border-slate-200`. Destructive: white with red text and red border; solid red only in the
  confirm dialog.
- **Budget progress bar** — flat `h-2 rounded-full` track in `slate-100`, fill in jade;
  switches to amber past 80% and red past 100%. The colour *is* the warning, so no icon needed.
- **Empty states** — one line of plain text and a single primary button. No illustrations.
- **Amounts in lists** — right-aligned, tabular, expenses prefixed `−` in red, income `+` in
  jade. The sign and colour together carry the meaning.
- **Tables** — no vertical rules and no zebra striping; `border-b border-slate-200` between
  rows and a `slate-100` header.
