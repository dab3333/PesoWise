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

## Chart palette

Dashboard charts get a **categorical ramp** derived from jade plus deliberately distinct hues,
all flat fills, no gradient defs:

```
#0f8a6c  jade      #2563eb  blue      #d97706  amber
#7c3aed  violet    #db2777  pink      #0891b2  cyan
#65a30d  lime      #64748b  slate (always last — "Other")
```

Category colours are stored per-category in the database (the `color` column), assigned from
this ramp on seed, so a category is the same colour on every chart on every page.

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
