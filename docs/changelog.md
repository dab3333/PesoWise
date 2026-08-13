# Changelog — v1.0 → v1.2.1

This documents every fix and change made between the v1.0 build-out and later releases. It's
organized by root cause, not by commit, since several fixes were corrected more than once as
real-device/DevTools testing (v1.1) or live-stack Playwright testing (v1.2.1) surfaced deeper
issues.

## v1.0 — "v1 DONE" → "V1 Auth Fix"

### Cross-user cache leak on login/logout

**Bug:** TanStack Query's cache is keyed by query, not by user. Logging out and back in as a
different account (or registering a new one in the same session) could render the previous
user's cached transactions, budgets, goals, etc. until each query happened to refetch.

**Fix:** `queryClient.clear()` added to both `logout()` and `authenticate()` in
[`AuthContext.tsx`](../frontend/src/auth/AuthContext.tsx) — every session boundary now starts
from an empty cache.

---

## v1.1 — "Mobile optimized"

Two testing rounds, both driven by real-device and Chrome DevTools device-toolbar screenshots
(360×740 and 440×956 — iPhone 16 Pro Max — viewports), each fix verified against the running
Docker Compose stack via Playwright before being marked done.

### Round 1 — seven initial mobile issues

| Issue | Root cause | Fix |
| --- | --- | --- |
| Debt direction dropdown not editable | — | Made the field interactive on mobile |
| Theme toggle needed two taps | State/render timing bug in `ThemeToggle` | Fixed the toggle handler in [`ThemeToggle.tsx`](../frontend/src/components/ThemeToggle.tsx) / [`ThemeProvider.tsx`](../frontend/src/theme/ThemeProvider.tsx) |
| Native `<select>` dropdowns oversized on mobile | Browser/OS renders native select popups; unstylable from page CSS | First pass: font-size override (later found insufficient — see Round 2) |
| Hover-only Budgets buttons invisible on mobile (no hover state) | Buttons relied on `:hover` to appear, which mobile has no equivalent for | Made buttons always visible on mobile |
| Chart tooltip failed on second tap | Recharts only updates tooltip state on `touchmove` (never `touchstart`/`touchend`); combined with the browser firing a trailing synthetic `mouseleave` ~10–25ms after every tap's `click` (as it "parks" the emulated touch pointer), which hits Recharts' `mouseLeaveChart()` reducer and clears `axisInteraction.hover.active` | `forwardTouchAsHover` in [`charts.tsx`](../frontend/src/components/charts.tsx): dispatches a synthetic `mousemove` on `touchstart`, plus a second delayed (80ms) re-dispatch so it always fires *after* the trailing `mouseleave` and wins |
| 7-tab bottom nav overcrowded | Too many top-level destinations for a mobile tab bar | Restructured to 4 tabs + a "More" sheet (IA choice confirmed with the user via `AskUserQuestion`) in [`AppShell.tsx`](../frontend/src/components/AppShell.tsx) |
| No page-transition polish | — | Added `.page-transition` keyframe animation in [`index.css`](../frontend/src/index.css) |

### Round 1 follow-ups

- One-time chart entrance animation (`.chart-enter` in `index.css`), shown only on first render
  of a page visit, not on every re-render.
- First attempt at removing the black border on chart tap: `.recharts-surface:focus { outline:
  none }` — incomplete (see Round 2).
- Two-row button/title wrapping at 360px width (e.g. "Record payment" in Debts, the "70-20-10
  split" dashboard title): fixed with `flex-wrap`-based layouts so whole elements reflow
  instead of text wrapping mid-word/mid-button — [`ui.tsx`](../frontend/src/components/ui.tsx)
  (`Button` `whitespace-nowrap`, `CardTitle` restructure).
- Settings → Accounts edit/remove converted to icon buttons on mobile, kept as text buttons on
  desktop — [`SettingsPage.tsx`](../frontend/src/pages/SettingsPage.tsx).
- Added a profile avatar (initials circle) — new [`Avatar.tsx`](../frontend/src/components/Avatar.tsx)
  — shown next to name/email on desktop, and replacing the bare "sign out" text on mobile with
  a tappable avatar that opens a dropdown (name, email, sign-out) plus a confirmation step.

### Round 2 — corrections after further screenshot-driven testing

Each of these is a case where the Round 1 fix was reported back as *not actually fixed*:

**"Manage budgets" lands mid-scroll, not at the top of the Budgets page.**
React Router doesn't reset scroll position on navigation. Fixed with
`useEffect(() => window.scrollTo(0, 0), [location.pathname])` in `AppShell.tsx`.

**"The dropdown options issue is not fixed, the list is still big in mobile view."**
The first fix (`select { font-size: 16px }`) was a no-op: a bare element selector always loses
CSS specificity to Tailwind's `.text-sm` utility class, regardless of source order, so the
override never actually applied. Root-caused via specificity rules, and instead of patching
around it further, native `<select>` was replaced entirely with a custom `Select` component
(button + `<ul role="listbox">` combobox) in [`form.tsx`](../frontend/src/components/form.tsx) —
this decision was confirmed with the user via `AskUserQuestion` before building it.

**"The black border when clicking a chart is still appearing, specially in mobile view."**
The first fix only suppressed the outline on the root `<svg class="recharts-surface">`. The
actual element receiving focus on tap was a different internal Recharts `<g
class="recharts-zIndex-layer_2000" tabindex="-1">` — a z-index accessibility portal layer that
Recharts' own source comments say "should not be tabbable" but still receives default browser
focus styling. Fixed by widening the rule to `.recharts-wrapper *:focus { outline: none }` and
re-adding the app's jade focus ring only via `.recharts-wrapper .recharts-surface:focus-visible`
in `index.css` — so keyboard focus still gets a visible ring, but tap/click focus doesn't show
a black outline.

**"In the sign out option in mobile view, I asked it to be just a dropdown, not a modal."**
The first implementation used the shared `Modal` component (a centered `<dialog>`). Rebuilt as
a fixed-position anchored panel with an invisible full-screen dismiss-on-outside-tap button —
distinct from the app's form `Modal`s, which intentionally do not close on outside click.

### Round 2 follow-ups — new capabilities requested directly

**Custom date pickers.** Following the custom `Select` component, the user asked for the same
treatment on date inputs, which also opened oversized native browser calendar popups on mobile.
Built `DateField` in `form.tsx` — a button + calendar-grid panel (`startOfMonth`, `addMonths`,
`addDays`, `isSameDay`, a 42-cell/6-week `buildMonthGrid`) with full keyboard support (arrow
keys move by day, Enter commits, Escape closes, a "Today" shortcut) and the same
`onChange({ target: { value: toDateKey(date) } })` contract as the native input it replaced, so
no call-site logic needed to change. Swapped in at six call sites: `TransactionsPage.tsx`
(transaction date), `DebtsPage.tsx` (due date, paid-on date), `GoalsPage.tsx` (target date,
contributed-on date), `RecurringPage.tsx` (next-run date).

**Shared floating-position engine.** Both `Select` and `DateField` needed to open near a
trigger button that could be anywhere on screen — including inside a scrollable `<dialog>`
where an `absolute`-positioned panel could open past the visible area, forcing the user to
scroll the dialog itself just to see the full panel. Built `usePopoverPosition(open, triggerRef,
panelRef)` in `form.tsx`: measures the trigger via `getBoundingClientRect()`, positions the
panel with `position: fixed` in viewport coordinates, flips upward when there isn't room below,
and clamps within an 8px margin on all sides. A `mergeRefs` helper combines each component's
forwarded ref with the internal ref needed for measurement. Both panels now render correctly
positioned regardless of where their trigger sits inside a scrolling ancestor, without needing
a portal to `document.body` (native `<dialog>` top-layer stacking means a `position: fixed`
descendant still paints above the dialog's own backdrop).

**Compact mobile transaction list.** The transactions table required horizontal scrolling on
mobile to see all columns — flagged as "a very critical feature of the app." Added a
`<ul className="divide-y md:hidden">` compact 2-line card list per row (colored category dot,
truncated name + date/account/note metadata, a dedicated right-aligned amount column, and a
fixed-width trailing slot for either edit/delete icon buttons or an "auto" label for
system-generated rows) in [`TransactionsPage.tsx`](../frontend/src/pages/TransactionsPage.tsx).
The original `<table>` is kept, now wrapped in `hidden overflow-x-auto md:block` for desktop
only.

**Filter row layout at both 360×740 and 440×956.** Category and Account filters needed to
always share one line on mobile regardless of viewport width — at 440px the category filter
had started wrapping up next to the date filter instead. Fixed by wrapping both selects in a
`flex gap-3 sm:contents` div, each sized `min-w-0 flex-1 sm:min-w-40 sm:flex-initial` — `sm:contents`
makes the wrapper "disappear" from layout at the desktop breakpoint so the two selects rejoin
the original single-row desktop layout, while on mobile the wrapper's own flex row guarantees
they never separate.

**Recurring page row squeeze at 440×956.** Reproduced and confirmed via Playwright screenshot
before fixing, per explicit request. Root cause: the name column (`min-w-0 flex-1`) had no
minimum width, so fixed-width `shrink-0` siblings (amount, action buttons) forced it to
shrink to near-zero and wrap internally into three cramped lines instead of the row itself
wrapping. Fixed in [`RecurringPage.tsx`](../frontend/src/pages/RecurringPage.tsx) by splitting
`DueRow`/`BillRow` into two explicit rows — name+amount on one line
(`flex flex-wrap items-baseline justify-between`), metadata + action buttons on a second
(`flex flex-wrap items-center gap-x-3 gap-y-1`) — mirroring the pattern already used in
`DebtsPage`.

**Date picker requiring scroll inside a modal.** Root cause: the calendar panel was
`position: absolute` relative to a `position: relative` ancestor inside a scrollable
`<dialog>`, so a calendar opened near the bottom of the dialog's content extended past the
visible area. Solved by the `usePopoverPosition` engine built above — the panel now positions
itself in viewport coordinates and flips upward when needed, so the whole calendar is always
visible with no dialog-internal scrolling required.

**Transaction list amount misalignment.** Root cause: the amount lived inside the same
`min-w-0 flex-1` box as the category name, right-aligned via `justify-between` within that box
— so its right edge equaled that box's right edge, which sat immediately next to a
variable-width trailing sibling (two icon buttons vs. a short "auto" label), shifting the
amount's visual position row to row. Fixed by giving the amount its own dedicated column and
reserving a fixed `w-[4.25rem]` width for the trailing slot regardless of its content —
verified via `getBoundingClientRect()` assertions showing every row's amount right edge
matching exactly.

---

## v1.2.1 — Data export/import

Full feature writeup, including the mid-build design change (preserve-ids → always-fresh-ids),
is in `build-plan.md`'s Phase 7. This is the bug-history summary.

| Bug | Root cause | Fix |
| --- | --- | --- |
| Cross-account import returned 409 on a file that should have imported cleanly | The first design preserved the file's own ids and relied on a full wipe-before-reinsert to avoid collisions — which only holds when importing into the *same* account. Importing into a *different* account could still collide with that account's own existing rows. | Redesigned import to always generate fresh ids and return old-id → new-id maps, so a same-account restore and a cross-account clone are the same code path — see `architecture.md`. |
| Re-import failed with a generic "could not be imported" error, even after the fresh-id redesign | `deleteByUserId` is a Spring Data *derived* delete query — deferred until flush, same as `persist()`. Hibernate's flush order is always inserts before deletes regardless of call order, so a re-imported row sharing a unique key (e.g. account name "Cash") with a row already "deleted" in code — but not yet in the database — collided with it. Only reproduced with an account that already had bootstrap-seeded data; curl-only verification with brand-new accounts never triggered bootstrap and never hit the bug. | Added an explicit `entityManager.flush()` immediately after the delete calls, in both `ledger-service` and `planning-service`'s `importAll`, forcing the deletes to hit the database before any insert is queued. |
| Planning import failed with a raw `NullPointerException` after the ledger half succeeded | The frontend Docker container was serving a build from before the payload redesign — it posted the old, unwrapped planning export instead of the new `{data, ledgerIds}` shape planning-service's import now requires. Backend logs alone made this look like a backend bug; Playwright driving the actual browser was what surfaced the real (stale) request body. | `docker compose up -d --build frontend` — no code change needed, the source was already correct. |
| No confirmation that an export or import actually succeeded | Both actions only ever showed an error banner on failure; success was silent. | Added a `success` tone to the shared `Alert` component (reuses the income/jade semantic colour) and wired it into `DataCard` for both actions. |
| Export/Import rows on the Settings page wrapped awkwardly at tablet and narrow-desktop widths | The label-and-button row used a bare `flex-wrap` with no width constraint on the label block, so once the description text plus button exceeded the available row width, the button dropped to its own line and lost its right-aligned position (a single wrapped flex item falls back to `flex-start`). | Explicit `flex-col sm:flex-row sm:items-center sm:justify-between` — an intentional stacked layout below the breakpoint instead of an accidental one. |

## Verification method used throughout

Every fix in this document — not just the ones with explicit "verify then fix" instructions —
was checked against the actually-running Docker Compose stack, not just by reading the code.
Playwright, driven headlessly through a manually-located Chromium binary
(`%LOCALAPPDATA%\ms-playwright\chromium-1234\chrome-win64\chrome.exe`, worked around a broken
`chromium_headless_shell` download), loaded the app at mobile viewport sizes
(`isMobile`/`hasTouch`/`deviceScaleFactor` set) and took screenshots or ran DOM/computed-style
assertions — e.g. bounding-box comparisons proving amount columns align, or checking a
calendar panel's bounding box stays within the viewport.
