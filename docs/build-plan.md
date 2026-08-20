# Build Plan

Work proceeds **vertically** — one feature end to end, backend and frontend, before starting the
next — so the app is runnable and demonstrable at every step rather than only at the finish.

Each step is a feature branch merged to `main` once its slice runs end to end, so `main` stays
deployable and the history reads as the build order.

Every version below follows the same shape: **Checklist** (what shipped, at a glance) → **Plan**
(how it was built, phase by phase, with verification and test counts) → **Blockers & Decisions**
(what was decided, why, and what was left open).

---

# v1.0 — The MVP

## Checklist

- [x] Scaffolding, Compose, gateway
- [x] auth-service + gateway JWT filter + login/register
- [x] ledger-service accounts & categories + Settings page
- [x] ledger-service transactions + Transactions page
- [x] Report endpoints + Dashboard page
- [x] planning-service budgets + 70-20-10 suggester
- [x] planning-service debts
- [x] planning-service savings goals
- [x] planning-service recurring bills + scheduler
- [x] Test matrix + README

## Plan

### 1. Scaffolding ✅

Monorepo skeleton, `.gitignore`, `.env.example`, `.gitattributes`, three Postgres containers, and
the gateway. **`.env` gitignored from the very first commit** — `JWT_SECRET` and database
passwords must never reach a public repo.

*Verified:* all containers healthy; `/api/transactions` returns 401 without a token.

### 2. auth-service + gateway filter + login/register ✅

The first vertical slice, chosen deliberately: it proves the whole request path — browser →
nginx → gateway → service → Postgres — before any feature depends on it.

*Verified against real Postgres:* register 201 with bcrypt `$2a$12$` hash in the table; duplicate
email with different casing 409; wrong password 401; validation 400 with field errors; protected
route without a token 401; **spoofed `X-User-Id` 401, not 200**; Flyway history row present.

*Tests:* gateway 9/9, auth-service 8/8.

### 3–5. ledger-service ✅

Built as one service, exposed as three UI steps.

**Backend complete.** Accounts, categories with lazy one-time seeding, transactions with a paged
filtered list, and the four report aggregates. See [architecture.md](architecture.md) for the
design decisions and [api.md](api.md) for the endpoints.

*Verified against real Postgres:* 16 categories and a Cash account seeded on first call, seeding
idempotent on the second; duplicate account name 409; expense without a bucket 400; deleting a
built-in category 409; bad hex colour 400 with a field error; summary correct and isolated per
month across two months of data; 70-20-10 breakdown correct; by-category totals correct; daily
series returns all 31 days with 7 active; derived account balance correct.

*Tests:* ledger-service 10/10 covering the 70-20-10 maths, zero-income division, month-length
edge cases including leap February, and malformed input.

*Frontend:* Dashboard (stat tiles, spend-by-category bars, daily trend, 70-20-10 meters, budget
progress), Transactions (paged, filtered, full CRUD), and Settings (accounts, categories,
appearance). Recharts is code-split, so the initial bundle is 318 kB rather than 685 kB.

### 6. Budgets ✅

The `budgets` table, the Feign-backed progress calculation, the 70-20-10 suggester, and the Budgets
page. This is the first step where two services talk to each other.

**Progress is computed live**, never cached: each read fetches totals from ledger-service and joins
them against the stored limits in memory. That is what the synchronous-REST decision buys — there is
no cache to invalidate, so a bar cannot show a stale figure after a transaction is edited.

**The suggester weights by history.** The 70-20-10 method only defines the three pools; splitting a
pool across the categories in it is what decides whether the suggestion is usable, so each category
gets a share proportional to its own spending over the previous three months. Details and the
rounding rule are in [api.md](api.md#post-apibudgetssuggestion).

Two things worth recording from building it:

- **Feign renders `LocalDate` with default locale formatting** unless told otherwise — it sent
  `8/1/26` instead of `2026-08-01`, and ledger-service rejected it with a 400. Both Java signatures
  looked perfectly correct, so this only appeared at runtime. Fixed globally with a
  `FeignFormatterRegistrar`, so date parameters added in later steps are right by default.
- **`bucket` was added to ledger-service's `/by-category` response**, so the budgets page and the
  suggester get the 70-20-10 grouping without a second call.

*Verified against real Postgres, across both services:* live progress with an overspent category
reporting negative remaining; unbudgeted spend surfaced separately; upsert idempotent (three rows
after repeated writes, not six); the suggester's per-bucket lines summing exactly to their pools;
income estimated from last month when omitted; copy-previous-month; zero limit 400; malformed month
400; no token 401.

*Tests:* planning-service 26/26 — the suggester's proportional split, even-split fallback, rounding
drift, history window, income estimation, and the progress maths including overspend.

### 7. Debts ✅

Both directions (owed by me, owed to me), with payments, undo, settle, and the Debts page.

**The dual write is the substance of this step.** A payment reduces the balance here *and* posts a
transaction to ledger-service, so a debt payment appears in spending reports rather than living in a
silo. The ordering, the failure modes, and the one window that stays open are documented in
[architecture.md](architecture.md#the-dual-write) — the short version is that the ledger is called
before the local commit, so a failure means "nothing happened".

*Verified against real Postgres across both services:* both directions created; net position;
payment dropping the balance to ₱7,500 **and** August ledger expense rising 29,500 → 32,000 with the
transaction tagged `DEBT_PAYMENT` and pointing back at the debt; a repayment on money owed *to* the
user recording INCOME; overpayment 400 with nothing written; undo restoring the balance **and** the
ledger transaction returning 404; settling in full flipping to SETTLED and dropping out of the
totals; paying a settled debt 409.

*Tests:* 16 more in planning-service (42 total) — the dual write's captured payload, ledger-failure
propagation, undo reversing both sides and reopening a settled debt, overpayment, cross-debt payment
isolation, user scoping, and the overdue rule.

### 8. Savings goals ✅

Goals with contributions, undo, archiving, and the Goals page.

**The dual-write path is now shared.** Rather than copying the ordering rule into a second service
class, it was extracted into `LedgerWriter` and both `DebtService` and `GoalService` call it. The
rule is subtle enough — call the ledger *before* the local commit so a failure means "nothing
happened" — that having it stated in one place matters more than the handful of lines saved.
`DebtServiceTest` was updated for the refactor and stayed green, which is the point of having had it.

`monthlyNeeded` is the feature that turns a goal into a plan: the shortfall spread over the remaining
calendar months, counted **inclusive of the current one** so a target inside this month asks for the
whole amount rather than dividing by zero, and **rounded up** so following it always reaches the
target.

*Verified against real Postgres across both services:* a ₱12,500 contribution raising `savedAmount`
**and** the 70-20-10 SAVINGS bucket 14,000 → 26,500, with the transaction tagged
`GOAL_CONTRIBUTION`; `monthlyNeeded` recomputing 10,000 → 7,500 after it; over-saving accepted with
`remaining` at 0 and 145% complete; editing the target from 50,000 to 90,000 leaving contributions
untouched; undo restoring the total **and** the ledger transaction returning 404; zero amount 400;
another user's goal 404.

*Tests:* 20 more in planning-service (62 total) — derived totals, the achieved threshold, over-saving,
all four `monthlyNeeded` cases including round-up and the current-month edge, behind-schedule only
applying to unmet goals, the dual write's captured payload, ledger-failure propagation, undo, and
archived goals leaving the totals.

### 9. Recurring bills ✅

A bill is a transaction template plus a cursor (`nextRunDate`); a daily `@Scheduled` pass walks it
forward. The Recurring page, an upcoming-bills widget on the Dashboard, and manual confirm/skip for
bills whose amount varies.

**Not charging anyone twice is the whole difficulty here**, and it gets two independent guards:

1. **planning-service claims the occurrence before calling the ledger** — a `recurring_runs` row,
   unique on `(bill_id, due_date)`, inserted first. A duplicate fails on the insert, before any
   money is written.
2. **ledger-service refuses a second transaction for the same bill and date** (a new unique index,
   scoped to `RECURRING_BILL` rows only — two debt payments on the same day are legitimate, so the
   same rule would be wrong for those). This covers the one gap guard 1 leaves: if the ledger write
   succeeds and planning-service's commit then fails, the claim rolls back, and without guard 2 the
   next pass would post again. The ledger's 409 is treated as "already recorded", not an error.

The `REQUIRES_NEW` transaction that settles one occurrence lives in its own bean
(`RecurringOccurrences`), not a method on the looping service — a same-class call would bypass the
proxy and the annotation would silently do nothing. Each bill therefore commits independently, so
one bill pointing at an archived category cannot block the others in the pass; the failure is
logged and reported in the run summary instead.

**Missed occurrences are caught up, not dropped** — rent that was due really was due — capped at 12
per bill per pass, with the truncation reported rather than silent.

**Month-end handling was the other trap.** A bill anchored on the 31st advances from its stored
anchor day, clamped to the target month's length, not from the previous (already-clamped) date:
`31 Jan → 28 Feb → 31 Mar → 30 Apr → 31 May`. Advancing from the clamped date would leave it on the
28th forever.

**A real deployment bug, caught only by starting the app:** the two repository interfaces were
first nested inside a holder class. That compiles cleanly and every unit test still passes — they
construct services directly with mocks and never start a Spring context — but Spring Data only
detects repositories declared at the top level, so the app failed at boot with "no qualifying bean".
Fixed by moving both to top-level files, and closed with `RepositoryDeclarationTest`, a classpath
scan that fails if a repository interface is ever nested again.

*Verified against real Postgres, including an actual container restart:* an auto-post bill recorded
by the pass while a confirm-first bill was only flagged; the monthly total normalising a weekly bill
via 52÷12 rather than ×4; **running the pass four times in a row posted exactly once**; manually
confirming the second bill, then confirming again returning 409 with the transaction count
unchanged; **restarting the planning-service container and re-running the pass created zero new
transactions**; deleting a bill removing its history while its ledger transactions stayed; the
month-end anchor holding on a real bill.

*Tests:* 99 total (gateway 9, auth 8, ledger 10, planning 72) — cursor advancement including leap
February and the 31st, both idempotency guards individually, the catch-up cap, one-bill-failure
isolation, monthly normalisation, and the repository-declaration regression guard.

### 10. Test matrix and README ✅

MVP feature work finished at step 9. This step closes the gap the earlier plan flagged
explicitly: **an actual per-service test proving user A cannot read or mutate user B's rows**,
not just an inference from "the repository method takes a userId".

An audit (grepping every service's test suite for a cross-user pattern) found the gap was real:
`DebtServiceTest.scopesByUser` and `GoalServiceTest.scopesByUser` existed; nothing else did.
ledger-service in particular had **zero** service-level tests for `AccountService`,
`CategoryService`, or `TransactionService` — only `ReportServiceTest`, which is read-aggregation
and proves nothing about ownership.

Added, each following the same shape — the record exists, just not for the caller, so the
repository correctly returns empty and the service must turn that into 404:

| Service | Class | New tests |
| --- | --- | --- |
| ledger-service | `AccountServiceTest` (new) | update/delete blocked cross-user, owner's own succeeds |
| ledger-service | `CategoryServiceTest` (new) | `require`/delete blocked cross-user, owner's own succeeds |
| ledger-service | `TransactionServiceTest` (new) | create against another user's account refused *before* the category is even looked up; update/delete blocked cross-user |
| planning-service | `BudgetServiceTest` | delete blocked cross-user for the same category+month |
| planning-service | `RecurringServiceTest` | `postNow` blocked cross-user, scheduler never touched |

Every case asserts **404, never 403** — a 403 would confirm the id exists, which is exactly the
information a caller should not get. `Debt` and `Goal` already had this; the audit is what proves
the other five entities now do too, rather than assuming the pattern held everywhere because it
held somewhere.

**138 tests**, all passing, run immediately before this commit:

| Service | Tests | Covers |
| --- | --- | --- |
| gateway | 9 | JWT verification, expiry, forged signatures, **header-spoofing stripped on every path including public ones and preflight** |
| auth-service | 8 | registration, bcrypt hashing, timing-safe login, email normalisation |
| ledger-service | 20 | 70-20-10 bucket maths, zero-income division, leap February, malformed input, **5 new cross-user isolation tests** |
| planning-service | 101 | budget progress and upsert; the suggester's proportional split, rounding, history window; both debt-payment dual-write guards; goal derivation and over-saving; recurring cursor advancement (13 cases including the 31st, leap years, year boundaries); both idempotency guards; the repository-declaration regression guard; **cross-user isolation across every entity** |

### Final verification checklist

Performed end to end against the deployed stack at every step, not only at the end:

1. `docker compose ps` — all 8 containers healthy.
2. Register; confirm redirect to the dashboard; seeded categories and Cash account present.
3. Add income and expenses across two different months; confirm month isolation in reports.
4. Set a budget; confirm spent/remaining updates immediately after adding a transaction, and
   again after deleting it — proving there is no cache to go stale.
5. Apply "suggest budget"; confirm the 70-20-10 split sums exactly to each bucket's pool.
6. Record a debt payment; confirm the balance drops **and** a matching ledger transaction
   appears, tagged `DEBT_PAYMENT`; confirm undo reverses both sides.
7. Add a goal contribution; same dual-write check, plus over-saving accepted where overpaying a
   debt is rejected.
8. Create an auto-posting recurring bill dated in the past, run the pass **four times in a row**:
   posts exactly once. **Restart the planning-service container** and run it again: still zero
   new transactions.
9. Confirm every chart renders, month navigation works, and dark mode has no flash on load.
10. `curl` without a token → 401 at the gateway; a spoofed `X-User-Id` header → 401, not 200.
11. `mvn test` green in all four services — 138/138.

## Blockers & Decisions

**Debts (Step 7):**

- **Undo deletes the ledger transaction too.** Reversing a payment while leaving the cash movement
  behind would make the two records disagree, which is worse than either outcome alone.
- **Deleting a debt keeps its transactions.** The money really did move; erasing it would rewrite
  the user's spending history.
- **`accountId` and `categoryId` are required, not inferred.** Which wallet the money came from and
  how it should appear in reports are the user's decisions. The category also carries the direction,
  so paying records an expense and being repaid records income — they cannot disagree.
- **Overpayment is rejected, not clamped** — it usually means a typo, and absorbing it hides the
  mistake.
- **Principal and direction are immutable** once created; both would invalidate existing payments.
- Interest rate is stored and displayed but **not accrued** — compounding is out of scope for v1.0
  (delivered in v1.2 Phase 2).

**Savings goals (Step 8) — deliberately different from debts:**

- **No stored total.** `savedAmount` is `SUM(contributions)`, read in one grouped query. A debt keeps
  a balance column because it has an invariant to protect — you cannot pay more than you owe — and a
  CHECK constraint enforcing it. A goal has no such bound, so a stored total would be duplication
  with nothing to guard. "Achieved" is likewise computed, not a column.
- **Over-saving is allowed.** Contributing past the target returns 201, where overpaying a debt
  returns 400. Saving more than planned is not a mistake to prevent. `remaining` clamps at zero
  rather than going negative, because "₱0 to go" is the useful reading.
- **The target amount is editable**, unlike a debt's principal. Revising what you are saving for is
  normal and invalidates nothing; contributions stay exactly as recorded.
- **Archiving exists** as the alternative to deleting — it hides a goal but keeps its history.

**Known gap, accepted rather than fixed:** Testcontainers integration tests were not added for
v1.0. They cannot run on this machine — see
[development.md](development.md#testcontainers-cannot-reach-docker-on-some-machines) — so every
guarantee that would normally come from an integration suite (Flyway applying cleanly, the
aggregate SQL, cross-user isolation) was instead verified by exercising the real endpoints against
the running Compose stack, over all nine steps. That is weaker than a suite that runs in CI on
every push, and is recorded here as a known limitation rather than papered over.

---

# v1.1 — Mobile Optimisation

## Checklist

- [x] Screenshot-driven fixes, two rounds

## Plan

Every fix, with its root cause, is documented in [changelog.md](changelog.md) rather than
duplicated here — that file is organized by root cause, not by commit, since several fixes were
corrected more than once as real-device/DevTools testing surfaced deeper issues.

## Blockers & Decisions

None recorded separately from the fixes themselves — see changelog.md.

---

# v1.2 — Admin, Auth Hardening, Interest, Deployment

v1.0 delivered the six MVP features; v1.1 was a mobile pass. v1.2 turns PesoWise from a locally
run personal app into something operable and publicly deployable: an administrative layer (which
needs a role concept that did not exist anywhere), the one MVP feature left deliberately
half-built (debt interest), the authentication gaps that make a public deployment irresponsible,
and a real deployment path.

## Checklist

- [x] Phase 1 — Roles, email verification, password reset
- [x] Phase 2 — Debt interest accrual
- [x] Phase 3 — admin-service (5th service)
- [x] Phase 4 — Admin UI, About page, feedback
- [x] Phase 5 — Landing and auth page
- [x] Phase 6 — Deployment readiness

## Plan

### Phase 1. Roles, email verification, password reset ✅

The foundation everything else depends on: no admin panel is possible without a role.

**Schema** (`V2__roles_and_verification.sql`): `users` gains `role`, `email_verified` and
`disabled`. Two token tables store a **SHA-256 hash of the token, never the raw value** — a
leaked backup must not be a working set of account-takeover links. Existing users are
grandfathered to `email_verified = true`, or the first deploy of this release locks out every
account including the developer's.

**Registration no longer signs anyone in.** It returns a status, not a token: issuing a session
for an address nobody has proven they can read is the exact thing verification exists to prevent.
Login answers **403 with a distinguishable code** when unverified — not 401, which would make the
frontend claim the credentials were wrong and send the user off to reset a password that works.

Both checks run *after* the password comparison, so neither tells an attacker anything they could
not already learn. `forgot-password`, `resend-verification` and `reset-password` always answer
204 whether or not the address exists.

**Mail** is plain SMTP via `spring-boot-starter-mail` — no vendor SDK, so the provider stays a
config change. Composition lives in one class and only *delivery* swaps, which means the link a
developer reads in the log is character-for-character the one a user receives.

**Authorisation** moved into the gateway: a `role` claim in the JWT, injected downstream as
`X-User-Role`, with `/api/admin/**` requiring ADMIN. Two changes matter more than the feature:

- The new header is **stripped from every inbound request** exactly as `X-User-Id` already was,
  in both the gateway and nginx. Downstream services trust it, so a gap here would be privilege
  escalation that looks like a missing line rather than a bug.
- `isPublic` changed from `startsWith` to **exact match**. The prefix match meant
  `/api/auth/loginXYZ` was public, and this phase adds four more public endpoints under that same
  prefix — sloppy became dangerous. Exact matching is only sufficient because no public endpoint
  takes a path variable, which is why the tokens travel in the request body.

**A pre-existing hole fixed:** `POST /api/recurring/run` accepted `X-User-Id`, ignored it, and ran
the bill pass for *every user*, posting real transactions to other people's ledgers. Any
signed-in account could call it. Now admin-only.

**Admin bootstrap** is `PESOWISE_ADMIN_EMAILS`, applied both at startup and on registration, so
the order of deploying and signing up does not matter. Promotion only — an address disappearing
from an environment variable must not silently strip someone's access.

Two failures worth recording, both caught by running the stack, not by reading the code, and
neither would have surfaced in a unit test:

1. **`CHAR(64)` broke startup.** Postgres reports `CHAR` as `bpchar`, Hibernate maps a String to
   `varchar`, and `ddl-auto: validate` refused to start. Corrected forward in
   `V3__token_hash_varchar.sql` rather than by editing an already-applied V2 — and `VARCHAR(64)`
   is the better choice regardless, since Postgres stores both identically.
2. **`spring-boot-starter-mail` silently broke the health endpoint.** It auto-registers a health
   indicator that opens an SMTP connection, so `/actuator/health` returned 503 with no
   credentials configured — which Compose and the gateway both key off. A mail-provider outage
   would have taken authentication down with it. Disabled via `management.health.mail.enabled`:
   sign-in, token validation and `/me` all work without SMTP, so being able to send mail is not a
   condition of the service being alive.

*Verified against the running stack:* register returns no token; login carries `role` in the JWT;
`/me` exposes `role` and `emailVerified`; the closed prefix hole returns 401 on
`/api/auth/loginXYZ`; a bad verification token 400; `forgot-password` for an unknown address 204;
a spoofed `X-User-Id` still 401. **The critical case — a USER token plus a spoofed
`X-User-Role: ADMIN` header on an admin-gated endpoint — returns 403, both headers spoofed
together also 403, and a genuine ADMIN gets 200.**

One caveat recorded rather than glossed over: the gateway's `/api/admin/**` rule could not be
exercised live yet, because Spring Cloud Gateway resolves routes during handler mapping and
returns 404 for an unrouted path *before* any global filter runs. There is no `/api/admin` route
until Phase 3. The rule is covered by unit tests now and gets its live check then.

*Tests:* auth-service 26, gateway 17, planning-service 101 — all green.

### Phase 2. Debt interest ✅

Built last, after being deliberately deferred through Phases 3–5 — independent of every other
phase, so nothing else needed to wait for it.

`V2__debts.sql` carried `CHECK (balance <= principal)`, payments were rejected above `balance`,
and both `paidAmount` and `percentPaid` derived from `principal − balance`. Interest touched all
four, which is why the design avoids fighting the constraint rather than working around it:
interest never enters `balance`. `balance` keeps meaning *outstanding principal*, and accrued
interest lives in its own column. The check dropped to `balance >= 0` regardless — it stopped
being a meaningful invariant once interest existed.

Simplified by one axis from the original plan: `interest_method` is `SIMPLE` or `COMPOUND`, full
stop — no separate `MONTHLY`/`ANNUAL` compounding choice. The accrual job only ever runs
monthly, so an annual-compounding option would have needed its own partial-period bookkeeping for
a distinction nothing was asking for.

`debts` gained `start_date`, `interest_method`, `accrued_interest`, `interest_paid_total`, and
`last_accrued_on` (`V5__debt_interest.sql`). `debt_payments` gained `principal_part` and
`interest_part` — stored rather than recomputed, since reversing a payment has to restore both
columns exactly as they were. Payments apply to interest first, then principal
(`Debt.allocate`); a debt settles only once both reach zero. `percentPaid` needed no change at
all — it was already `(principal − balance) / principal`, which never touched interest to begin
with.

A monthly job accrues (`DebtInterestScheduler` → `DebtInterestService` → `DebtInterestAccruals`),
reusing the `RecurringOccurrences` idempotency pattern exactly: a claim table
(`debt_interest_accruals`) with a unique `(debt_id, period)` index, claimed *before* the debt is
touched, so a restarted scheduler or a retried catch-up loop cannot double-accrue. Catch-up is
capped at 12 months, same reasoning and same limit as recurring bills. **No ledger write** —
accrued interest is owed, not paid, and writing it would cross the boundary that ledger-service
owns only money that has actually moved. A manual `POST /api/debts/accrue` exists for testing
without waiting for the 1st, admin-gated the same way `POST /api/recurring/run` is, for the same
reason: it accrues interest for every user's debts, not just the caller's.

Outstanding totals — the debt list, the admin overview's `totalOwedByUsers`/`totalOwedToUsers` —
now include accrued interest, not just principal. Excluding it would have understated what's
genuinely owed the moment interest existed.

*Verified against the running stack:* created a debt with a 12% rate back-dated three months,
ran the accrual pass, and got exactly ₱300 (₱100/month simple, matching a hand-computed `rate /
100 / 12 × balance`). **Ran it again immediately — a clean no-op**, the same idempotency proof
the recurring bills got. Paid ₱500 against it: the split came back as ₱300 interest + ₱200
principal, interest-first as designed. Deleted that payment: both columns returned to their
exact pre-payment values. Confirmed the overpayment boundary is `balance + accruedInterest`, not
`balance` alone — a payment one peso over that combined figure was rejected, one peso under (i.e.
exactly matching it) settled the debt. A non-admin got 403 from `/api/debts/accrue`; an admin got
a real summary.

*Tests:* planning-service 129 (up from 101) — `DebtTest` (new, 8: the accrual math in isolation —
SIMPLE not compounding, COMPOUND folding in unpaid interest, the cursor, interest-first
allocation), `DebtInterestAccrualsTest` (new, 6: the idempotency guard, mirroring
`RecurringOccurrencesTest`), `DebtInterestServiceTest` (new, 5: the catch-up loop and one-failure
isolation, mirroring `RecurringServiceTest`), and `DebtServiceTest` grew from 15 to 25 (payment
splitting, the new overpayment boundary, settling only when both columns are zero, reversal
restoring both). All 207 tests across the five services stayed green throughout.

### Phase 3. admin-service ✅

A fifth Maven module at `services/admin-service` (:8084) with its own database, owning `feedback`
and `admin_audit`. Phase 2 (debt interest) was deliberately skipped to reach this phase directly —
nothing here depends on it.

**Still no shared `common` module**, confirmed rather than assumed: the actual duplication turned
out to be one header constant class and a handful of DTOs per service, which is cheaper to keep
as copies than to couple five build lifecycles over.

Nothing could query across users before this phase — every repository method had
`WHERE user_id = :userId` baked in, with no exceptions — so the cross-user aggregates
(`InternalAdminService` in auth-service, and the equivalent stats controllers in ledger-service
and planning-service) are genuinely new queries, living in the service that owns the data. They
are exposed under **`/internal/admin/**`, not `/api/**`**: the gateway has no route for
`/internal/`, so they are unreachable from the internet and callable only over the Compose
network — the same trust model planning-service already uses to reach ledger-service.

`POST /api/feedback` is the one non-admin endpoint and got its own gateway route *outside* the
admin-gated prefix. Reports are streamed CSV — one representative export (`users.csv`) rather than
a generic report framework, since nothing has asked for a second one yet. The
`/api/admin/overview` fan-out degrades per panel when a service is down rather than failing the
whole request.

Two failures worth recording, both caught by running the stack, not by reading the code:

1. **A null search parameter crashed the user list with `function lower(bytea) does not exist`.**
   `WHERE :q IS NULL OR LOWER(u.email) LIKE ...` — when `:q` is null and feeds straight into
   `LOWER(...)`, Postgres cannot infer the placeholder's type from context and defaults to
   `bytea`. Fixed with an explicit `CAST(:q AS string)`, which pins the type regardless of the
   value.
2. **Feign could not send `PATCH` at all.** Its default client wraps
   `java.net.HttpURLConnection`, which refuses the method outright with "Invalid HTTP method:
   PATCH" — a JDK limitation, not a Feign bug. admin-service is the first service in this codebase
   to send `PATCH` between services (updating a user's role), so nothing had hit this before.
   Fixed by switching Feign to OkHttp (`feign-okhttp` + `spring.cloud.openfeign.okhttp.enabled`).

*Verified against the running stack:* `/api/admin/overview` returns real cross-service numbers —
user counts, ledger transaction volume, planning totals, feedback counts — composed from three
live Feign calls. Stopping ledger-service mid-request confirmed the fan-out degrades correctly:
the response stayed `200`, the ledger panel reported `available: false` with a message, and the
other three panels were unaffected. A `PATCH` to disable a user, then re-enable it, both wrote a
correctly-labelled row to `admin_audit`. `/internal/admin/users` is unreachable through the
gateway (404, no route). A non-admin token is refused on `/api/admin/**` with 403, and a spoofed
`X-User-Role: ADMIN` header on that same token is also refused — the header-stripping guarantee
from Phase 1 holds for a route that did not exist when that guarantee was first tested.
`POST /api/feedback` succeeds for an ordinary signed-in user, confirming it sits outside the
admin-gated prefix as intended.

*Tests:* admin-service 15 (new — feedback status transitions, the audit trail on every user
mutation, and the fan-out degradation logic under all four combinations of dependency health),
plus the existing 164 unaffected. **179 total, all green.**

### Phase 4. Admin UI, About page, feedback ✅

Prerequisites: a **`TextArea`** in `ui.tsx` (there is no `<textarea>` anywhere in the codebase),
and extracting `Th`/`IconButton`/`StatTile` out of the pages they are currently trapped in.

`navItems` is a module-level `const` evaluated once, so role-aware navigation means computing it
inside the component. Admin links go in a **separate sidebar group under a divider** — the mobile
bar is already full at 3 tabs plus More. That group is enough to overflow the sidebar's own height
on a shorter viewport, so the nav list scrolls internally (`min-h-0 overflow-y-auto` on the `<nav>`,
not the `<aside>`) while the logo header and the account/sign-out footer stay pinned.

`/about` carries the app description, developer credit, contact, version and the feedback form,
linked from a new row in Settings. The version comes from `package.json` via a Vite `define`, so
there is exactly one source of truth.

The overview page's `signupsLast30Days` and `dailyLast30Days` series (added on the backend in
Phase 3 but never rendered) got two Recharts panels — a new `SignupsChart` and a reuse of the
Dashboard's existing `DailyTrendChart`, since `DailyPoint` and `DailyTotal` are structurally
identical. Both are lazy-loaded the same way the Dashboard's charts are, so the admin panel does
not tax the bundle any account without the ADMIN role ever pays for.

[design-system.md](design-system.md) applies unchanged: no gradients, jade as the only accent.
The admin panel must not become a second visual language.

Three things found only by using the deployed app, not by reading the code:

1. **A same-origin `localStorage` collision, unrelated to admin but surfaced while testing it.**
   Two accounts signed in in separate tabs of the same browser stomped on each other's session —
   not a backend restriction (login is fully stateless; nothing tracks one-token-per-user), but
   both tabs sharing one `pesowise.token` key. Fixed with a `storage` event listener in
   `AuthContext` — the one DOM event that fires in the *other* tab when the key changes — which
   re-verifies against `/me` and adopts the new session, or drops to signed-out if the token was
   cleared elsewhere.
2. **Tablet widths were never a real test target and it showed.** The Users table needs a
   `min-w-[40rem]` (640px) to lay out; once the 15rem sidebar and page padding are subtracted from
   a tablet's actual width, that no longer fits, and the four-tile stat grids (`sm:grid-cols-4`,
   one column more than every other stat grid in the app) hit the same problem — both were
   designed against phone and desktop widths with nothing in between assumed. Fixed by holding the
   card-list layout through `lg` instead of switching at `md`, and delaying the 4-column stat
   grids to `lg` as well, so a tablet gets the same breathing room a phone already had rather than
   a squeezed, overflowing desktop layout.
3. **The mobile transaction row's note was truncated into an unreachable ellipsis.** The date,
   account and note were one `truncate`d line, and the row itself is not tappable — only the edit
   icon opens anything — so a cut-off note with no way to see the rest was a dead end. Moved the
   note to its own wrapping line instead of trimming it, which also meant switching the row from
   `items-center` to `items-start` so the category dot, amount and action icons stay aligned to
   the top line instead of drifting toward the middle of a now-taller card.

### Phase 5. Landing and auth page ✅

`/login` and `/register` stay separate routes sharing one component. A two-column layout at `md`
and up: a flat jade brand panel with the tagline **"Make Every Peso Count."** and three one-line
feature statements, form card on the right. It stacks on mobile — the panel disappears below
`md` and the tagline moves above the card instead, so the v1.1 mobile work is preserved rather
than redone. The shared `AuthShell` added in Phase 1 meant this changed one file
(`components/AuthShell.tsx`), plus a small `inverted` variant on `Logo` for sitting on the jade
fill directly.

A separate public marketing page is deferred — `/` is the authenticated dashboard and signed-out
visitors already redirect, so a hero here delivers most of the value for none of the routing
change.

Scope grew mid-phase — auth hardening and profile fields for future personalization, requested
alongside the redesign and landing in the same phase since they touch the same page:

- **Show/hide password.** Neither `/login` nor `/register` had one. New `PasswordField` in
  `ui.tsx` — a `Field` with its own local visibility toggle, so no caller has to thread that
  state through. Applied to both the password and the new confirm-password inputs.
- **Confirm password** on `/register`, checked client-side only — nothing about a repeated
  password is meaningful to validate server-side, so it never reaches the API. A mismatch sets a
  `confirmPassword` field error the same way a real backend field error would, so it renders
  through the existing error-display path with no special case.
- **Registration collects first name, last name (two fields, one row), age, gender, and
  occupation** (with a free-text field that only appears when occupation is "Other") — all for
  the account-personalization features that will read them later, not anything today's app uses.
  `displayName` (still what every existing page reads — greeting, avatar initials, admin users
  list, the JWT `name` claim) is now derived as `firstName + " " + lastName` at registration
  rather than being its own form field, so nothing downstream had to change.
- **Backend:** `V4__profile_fields.sql` / `V5__age_integer.sql` in auth-service add
  `first_name`, `last_name`, `age`, `gender`, `occupation`, `occupation_other` to `users` — all
  nullable, no backfill, since accounts that predate this simply have none of it and nothing
  reads it yet. `gender` and `occupation` are `CHECK`-constrained enums, same pattern as `role`.
  Confirm-password has no backend counterpart at all — see above.

**One bug caught by actually starting the service, not by reading the code:** `age` was
migrated as `SMALLINT`, but the entity's `Integer` field maps to Hibernate's default `INTEGER`,
and `ddl-auto: validate` rejected the mismatch outright at startup. Fixed with a follow-up
migration (`V5`) rather than editing `V4`, since Flyway had already recorded and applied it.

### Phase 6. Deployment readiness ✅

`docker-compose.prod.yml` as an override, layered on with
`docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build`: it resets the
`ports` list to empty on all four Postgres containers, the gateway, and the frontend, and adds a
`caddy` service publishing only 80/443. `architecture.md`'s invariant ("service ports are never
published to the host in production") was true in intent but not in the Compose file itself —
the dev file publishes everything as a local-access affordance, and only the override actually
enforces the invariant.

**One real gotcha, caught by running `docker compose config` rather than assuming the merge
worked:** Compose's default list-merge behaviour *concatenates* `ports` across `-f` files rather
than replacing it — an override that just re-declares `ports: []` would leave every dev port
published underneath it. The fix is the `!reset []` YAML merge-control tag (Compose v2.24+),
which explicitly clears a list instead of appending to it. Confirmed by generating the merged
config and checking that `gateway`, `frontend`, and all four Postgres services have no `ports:`
key at all, and that `caddy` is the only service that does.

Caddy fronts the frontend container and gets free, cron-free Let's Encrypt renewal from a single
`{$PESOWISE_DOMAIN} { reverse_proxy frontend:80 }` block — the domain substitutes via Caddy's own
`{$VAR}` environment expansion. Because the frontend container stays behind Caddy on the same VM,
the nginx `/api` proxy inside it still applies: requests remain same-origin, `VITE_API_URL` stays
empty, and CORS stays irrelevant — the same property that already holds in dev via the Vite
proxy, and the main practical advantage over splitting the frontend onto a separate host. The one
place CORS gets configured at all — the gateway's `CORS_ALLOWED_ORIGIN` — is switched from the
dev localhost origin to `PUBLIC_URL` in the prod override, so a client hitting the gateway
directly (bypassing the same-origin proxy) isn't trusted from an arbitrary origin either.

`.github/workflows/ci.yml` did not exist before this phase. Two jobs beyond the obvious
build/test value: a `backend-test` matrix (`mvn test`, one entry per service, unit tests only) and
a `backend-integration` matrix (`mvn verify -Pintegration`, the four services that carry
Testcontainers suites — gateway has none). **GitHub Actions' runners have a working Docker
daemon**, which is exactly what this machine does not — see the Testcontainers note in
`development.md`. CI is therefore the first place these integration tests will actually run
green, not just compile. A third job typechecks and lints the frontend (`tsc -b`, `oxlint`).

`docs/deployment.md` is new: provisioning (a domain is mandatory — Let's Encrypt refuses to
certify a bare IP), first boot, the exact `.env` values that must change from their dev defaults,
a post-deploy verification checklist (certificate valid, `nmap` shows only 80/443, spoofed
`X-User-Id`/`X-User-Role` still rejected), per-database `pg_dump` backups (four now, not one),
log access, the upgrade command, and rotating `JWT_SECRET` (which signs every session out
instantly, with no dual-secret grace period — worth calling out explicitly rather than
discovering it during an actual rotation).

**Not done, and deliberately so:** no VM was actually provisioned or DNS configured in this
phase — that requires the domain and Oracle Cloud account described as open blockers below, which
are the reader's to resolve, not something to fabricate here. What's shippable now is everything
the repo can prove without those: the override merges cleanly, the invariant holds in the
generated config, and CI is wired up to actually run on the next push.

## Blockers & Decisions

**Decisions taken up front:**

| Decision | Choice | Why not the alternative |
| --- | --- | --- |
| Admin backend | A 5th service, `admin-service`, owning feedback and audit, composing cross-user stats over Feign | Endpoints bolted onto the three existing services would be cheaper, but scatter admin logic and leave feedback with no owner |
| Debt interest | Stored accrual with a scheduled monthly job | A read-time projection cannot split a payment between interest and principal, because the accrued figure is never materialised at payment time |
| Deployment | One free VM running the existing Compose stack behind Caddy | A Vercel + PaaS split loses the same-origin nginx proxy and wakes several chained JVMs on every cold request |
| About + feedback | A dedicated `/about` page, linked from Settings | Settings is for configuration; this is informational, and `SettingsPage.tsx` is already 514 lines |

Adding a fifth service deliberately triggers the "revisit at five" note in
[architecture.md](architecture.md) about a shared `common` module. Resolved in Phase 3: still no
shared module — see that phase's writeup above for why.

**Known blockers at the start of this version:**

| # | Blocker | Blocks | Mitigation |
| --- | --- | --- | --- |
| 1 | No SMTP provider account | Real delivery in Phase 1 | `MAIL_ENABLED=false` logs links and self-verifies registrations, so every flow is buildable and testable without credentials |
| 2 | Which contact details to publish | Phase 4 | Decide deliberately — a public address invites scraping; the feedback form is an alternative |
| 3 | No domain name | HTTPS in Phase 6 | Let's Encrypt will not issue for a bare IP. A DuckDNS subdomain, or a cheap `.com` |
| 4 | Oracle Cloud ARM capacity | Phase 6 | Free ARM instances are often unavailable in popular regions; all base images have arm64 variants, so ARM itself is not the risk |

**Also accepted:** the stack grows from 8 containers to 10, and the frontend still has no test
framework — verification stays Playwright-against-the-live-stack, as in v1.1.

---

# v1.2.1 — Data Export/Import

Deployment (v1.2 Phase 6) shipped the deployment *mechanics*, but actually standing up a shared
server hit walls outside the repo's control — see Blockers & Decisions below. Rather than keep
chasing a shared server, the developer pivoted to a feature: export everything to one file,
import it back into any install. Shipped as a patch on top of v1.2 since it is one self-contained
feature, not a phase with its own sub-steps.

## Checklist

- [x] Data export/import (ledger-service + planning-service + Settings page)

## Plan

### Phase 7. Data export/import ✅

**Design.** One `GET .../export` / `POST .../import` endpoint pair per owning service
(ledger-service, planning-service) rather than a new aggregator — nothing in the codebase does a
cross-service transaction today, and the frontend already fetches from both services separately
for other pages. Import always **replaces**, never merges (matches "move to a new device" and
avoids duplicate-record accumulation), and — after a design change mid-build, see Blockers &
Decisions — always **generates fresh ids** rather than reusing the file's own. See
`architecture.md`'s ledger-service and planning-service sections for the full id-remapping
mechanics.

Two bugs, both found only by testing against the live Docker Compose stack — reading the code
said both versions were correct:

1. **Hibernate flushes inserts before deletes, regardless of call order.** `deleteByUserId` is a
   Spring Data *derived* delete query — it loads matching entities and calls
   `EntityManager.remove()`, deferred until flush exactly like `persist()`. Hibernate's flush
   always runs inserts before deletes, so a re-imported row sharing a unique key (e.g. the
   account name "Cash") with a row the code had *already called delete on* collided with it,
   because the delete hadn't hit the database yet. Only surfaced with an account that already had
   real data (a bootstrap-seeded "Cash" account from having logged in before) — the first several
   rounds of curl-based verification used fresh accounts created without ever hitting the
   dashboard, so bootstrap never ran and the collision never had anything to collide with. Fixed
   with an explicit `entityManager.flush()` right after the deletes, in both services.
2. **A stale frontend container.** After redesigning the import payload shape (planning's import
   now needs the ledger id maps wrapped alongside its own export), the ledger-service and
   planning-service *containers* were rebuilt and reverified, but the frontend container was not
   — it kept serving the previous build, which posted the old, unwrapped payload. Backend logs
   showed a `NullPointerException` on a field that should have been impossible to be null;
   Playwright driving the actual browser (not curl) was what surfaced the real request body and
   made the mismatch obvious. `docker compose up -d --build frontend` was the entire fix — no code
   change needed.

Both were only caught because verification ran against the running app end-to-end (register two
accounts, populate one, export, import into the other, confirm both sides), per this project's
standing practice — reading the diff would have called both versions correct.

**Frontend.** `DataCard` on the Settings page: an Export button, an Import button behind a hidden
file input, and a `ConfirmDialog` extended with `requireTypedConfirmation="REPLACE"` — the first
action in the app more destructive than a single click should gate. Two follow-up polish passes,
both caught by the developer using the feature rather than by reading the code: no success
feedback after a completed export or import (fixed with a new `success` tone on the shared
`Alert` component, reusing the income/jade semantic colour), and the Export/Import rows wrapping
awkwardly at tablet and narrow-desktop widths because the label block had no width constraint
against the button (fixed with an explicit `flex-col sm:flex-row` breakpoint instead of a bare
`flex-wrap`).

`docs/data-migration.md` — the manual `pg_dump`/`psql` walkthrough this feature replaces — is
removed; it was never more than a stopgap for a workflow the app now does itself.

## Blockers & Decisions

**Why this version exists at all — deployment was blocked outside the app's control:**
Oracle Cloud's card verification flow rejected a virtual card (with a real ₱66.44 deducted
anyway), Google Cloud carried the same card-verification risk, and self-hosting from the
developer's own PC via Tailscale Funnel got as far as a public URL (`https://….ts.net`) before
Tailscale's own certificate-issuance backend turned out to have a genuine ongoing outage,
confirmed via their public status page rather than assumed. The decision: **each device keeps
its own local install**, and moving data between them is a deliberate export/import action in
the app, not a live sync. This is the reality every later version (v1.3.0 onward) plans against.

**Design decision reversed mid-build:** the first pass preserved the file's original ids, on the
reasoning that a full wipe-before-reinsert removes any collision risk — which is true for
restoring your *own* backup, but breaks the moment two different accounts' data has to coexist
anywhere in either database's history (unique indexes on account/category names, for instance,
aren't scoped away by the wipe of a *different* user's row). Concretely: importing one account's
export into a *different*, unrelated account correctly returned 409 on this first design — which
is what the wipe-and-preserve-ids logic was supposed to do — but the developer's actual want,
confirmed via direct question, was "I want cross-account import to actually work," i.e. clone one
account's data into another. That's a wider capability than restore alone, so the fix was
structural: generate fresh ids on every import and return the old-id → new-id maps so the
importing side can remap every cross-reference. This makes "restore my own backup" and "load
someone else's export into a different account" the exact same code path, with no special-casing
on whose file it is.

---

# v1.3.0 — Deferred-Feature Audit + Selected Features (Planned, not started)

v1.2.1 settled the app into a **local-only, per-device** reality — no domain, no public server,
each install moved between devices via export/import rather than a live sync. This round
re-audited every previously-deferred feature against that reality (some were deferred *because*
they needed hosting and are still blocked; others were deferred for unrelated reasons and are
just as buildable on localhost). A Gmail/email-receipt auto-import idea was also considered this
round and explicitly declined — see Blockers & Decisions below for why.

## Checklist

- [ ] Phase 8 — Foundations: rate limiting + Playwright scaffold
- [ ] Phase 9 — Transfers between accounts
- [ ] Phase 10 — Full amortisation schedule calculator
- [ ] Phase 11 — Due-bill / budget-overrun notification center
- [ ] Phase 12 — User-facing PDF monthly report
- [ ] Phase 13 — Receipt/photo attachments (MinIO)

## Plan

### Phase 8. Foundations: rate limiting + Playwright scaffold 📋 Planned

Two small, self-contained pieces, done first and together so every phase after this one can add
its own real end-to-end test as part of its own verification, instead of testing being an
afterthought — the exact "frontend has no test framework" blocker left open since v1.2.

**Rate limiting (gateway).** `bucket4j` in in-memory mode — no Redis, since the gateway is a
single instance and this is anti-brute-force, not anti-DDoS, so resetting on a restart is
acceptable. A new filter applies only to `pesowise.auth.public-paths` (login, register,
forgot-password, resend-verification, reset-password, verify-email — the exact list already in
`application.yml`), keyed on client IP + exact path, configurable via
`pesowise.rate-limit.capacity`/`refill-per-minute`. Exceeding the limit returns **429** in the
app's one error shape, mirroring however `JwtAuthenticationFilter` already short-circuits the
reactive (WebFlux) filter chain for its 401s.

**Playwright scaffold (frontend).** New `frontend/e2e/`, `@playwright/test` as a devDependency,
`playwright.config.ts` pointed at `http://localhost:3000` — testing against the already-running
Compose stack, the standing practice, not a spun-up dev server. First specs: register → verify
(dev-mode log link) → login, add a transaction end-to-end, and the Settings export/import round
trip — the three flows this project's own history has manually re-verified the most. New `npm run
test:e2e`, and a CI job that brings the full stack up (`docker compose up -d --build`), waits for
health, runs the suite, tears down.

### Phase 9. Transfers between accounts 📋 Planned

**Model**, validated against the real schema before committing to it (`V1__init.sql`,
`Enums.java`, `AccountRepository.java`, `TransactionRepository.java`):

- `Enums.Kind` — one enum, currently `INCOME`/`EXPENSE`, shared verbatim by both `Category.kind`
  and `Transaction.kind` — gains one new value: `TRANSFER`.
- `Transaction` gains two nullable columns: `transfer_direction` (a new small enum, `IN`/`OUT`,
  null for ordinary rows) and `transfer_id` (UUID, shared by a transfer's two rows so they can be
  displayed/edited/deleted together).
- `transactions.category_id` is `NOT NULL` with a real FK — confirmed, so transfer rows are not
  given a null category. They instead point at a hidden system category "Transfer" (`kind =
  TRANSFER`, `system = true`), created **lazily per user on first transfer** — confirmed that
  adding it to `BootstrapService`'s seed list would never backfill an already-bootstrapped
  existing user, since `ensureSeeded` short-circuits on the `user_bootstrap` marker before it
  would ever reach the seed insert. A new `CategoryService.getOrCreateTransferCategory(userId)`
  mirrors `ensureSeeded`'s own find-or-create-and-catch-the-race idempotency pattern.
- Two Flyway `CHECK` constraints need a **coordinated** change: `ck_transactions_kind` and
  `ck_categories_kind` (add `TRANSFER` to both), plus a real trap confirmed by reading the actual
  constraint — `ck_categories_bucket_expense_only` is an **exhaustive two-branch OR** with no
  fallback (`(kind='EXPENSE' AND bucket NOT NULL) OR (kind='INCOME' AND bucket IS NULL)`). A
  `TRANSFER` category satisfies neither branch and would be rejected outright unless a third
  branch (`OR (kind='TRANSFER' AND bucket IS NULL)`) is added in the same migration.
  `Category.create`'s bucket-resolution logic already defaults `bucket` to `null` for any
  non-`EXPENSE` kind, so only the constraint needs to change, not the application code.

**Report/balance query audit** — confirmed file-by-file, fewer changes needed than first assumed:

- `AccountRepository.findBalancesByUserId` **must change**. Its CASE is `WHEN kind='INCOME' THEN
  amount ELSE -amount END` — an `ELSE` catch-all, not explicit per-kind branches — so a
  `TRANSFER`+`IN` row would wrongly fall into `ELSE` and get subtracted. New CASE adds `OR
  (kind='TRANSFER' AND transfer_direction='IN')` to the `THEN amount` branch.
- `TransactionRepository.findTotalsByCategory` (`/api/reports/by-category`) **must change** — it
  sums per category with no kind filter at all, so the hidden Transfer category would surface as
  a spurious line item. Needs an explicit exclusion.
- `findTotals` (`/summary`), `findDailyTotals` (`/daily`), `findExpenseTotalsByBucket`
  (`/by-bucket`), and the admin-only `findSystemTotals`/`findSystemDailyTotals` are **confirmed
  safe as-is** — each already uses an explicit `WHEN kind = 'INCOME'`/`'EXPENSE'` with no `ELSE`,
  so a `TRANSFER` row contributes nothing to any of them by construction, no code change needed.

**New endpoint**: `POST /api/transactions/transfer` (`fromAccountId`, `toAccountId`, `amount`,
`txnDate`, `note`) creates both rows in one transaction, sharing a new `transfer_id`. Editing or
deleting a transfer always acts on both rows together, enforced in `TransactionService`.
**Frontend**: the transaction form gains a "Transfer" mode (two account pickers, no category);
the transaction list renders transfer rows distinctly (a swap icon, "Cash → GCash").

### Phase 10. Full amortisation schedule calculator 📋 Planned

On-demand only — no new table, no stored schedule, matching how budget progress is already
computed live rather than cached. `GET /api/debts/{id}/amortization?monthlyPayment=X` in
planning-service runs the same SIMPLE/COMPOUND interest math already implemented for the real
monthly accrual job forward against a hypothetical payment, producing a month-by-month table.
Capped at a sane iteration limit; a payment too small to ever cover accruing interest returns
`neverPaysOff: true` instead of looping forever. Frontend: a new "Amortisation" section on the
Debt detail view with a hypothetical-payment input and the resulting table/payoff summary.

### Phase 11. Due-bill / budget-overrun notification center 📋 Planned

In-app only, deliberately — no email, no push, no new persisted table. A new `GET
/api/notifications` in planning-service merges three things the app can already query: recurring
bills due soon, budgets at/over their limit this month, and debts with an upcoming/overdue due
date. "Dismiss" is local-only (localStorage per notification id) rather than a persisted "seen"
table — an item naturally reappears if its underlying condition is still true after a day, which
is correct behaviour for something genuinely still overdue, and keeps this phase backend-light.
Frontend: a bell icon + badge count in `AppShell.tsx`'s header opening a dismissible panel.

### Phase 12. User-facing PDF monthly report 📋 Planned

Client-side generation, no new backend endpoint — reuses the exact `/api/reports/*` data the
Dashboard already fetches (one source of truth for the numbers), avoiding a server-side PDF
library for the same reason CSV was picked over PDF for admin reports originally. Content: the
month's income/expense/net, budget progress bars, 70-20-10 actual-vs-target, and a category
breakdown table — the same figures already on screen. Sequenced after Phase 9 (transfers) so it
never ships even a day of transfers polluting its numbers.

### Phase 13. Receipt/photo attachments 📋 Planned

A new self-hosted `minio` Compose service (S3-compatible object storage), no host port
published. Uploads are **proxied through ledger-service** (`POST
/api/transactions/{id}/receipt`, multipart) rather than presigned browser uploads straight to
MinIO — receipts are small, ledger-service already authenticates the request via the
gateway-injected header, and this avoids exposing MinIO to the browser at all; presigned uploads
are a revisit-if-file-sizes-become-a-problem item, not a starting point. `transactions` gains a
nullable `receipt_key`; `GET /api/transactions/{id}/receipt` generates a short-lived presigned GET
URL per request so the object store itself is never public. Frontend: an optional photo picker on
the transaction form (`capture="environment"` for mobile camera capture) and a thumbnail/"View
receipt" link.

## Blockers & Decisions

**Every previously-deferred feature was re-audited against the local-only reality.** Some were
deferred *because* they needed a domain/hosting and are still blocked by that (Kubernetes
manifests, a marketing landing page, Eureka/Spring Cloud Config); others were deferred for
unrelated reasons and turned out to be just as buildable on localhost as anywhere else — those
became Phases 9–13 above.

**Considered and explicitly declined this round:**

| Deferred | Why |
| --- | --- |
| Gmail/email receipt auto-import | Reading email content needs Google's `gmail.readonly` scope, which Google classifies as *restricted* — an app in "Testing" publishing status gets refresh tokens that expire after 7 days unless it completes Google's app-verification process, which in turn wants a live, hosted privacy-policy page. That circles back to needing the exact domain/hosting this project walked away from in v1.2.1. A lower-friction email-*forwarding* alternative (no OAuth at all) was also discussed and not pursued this round. |
| Refresh token rotation | Reconsidered, not selected — a 24h JWT remains adequate for local-only personal use |
| Kubernetes manifests, Eureka/Spring Cloud Config | No cloud target; Compose remains the only runtime |
| A separate marketing landing page | No domain to point one at |
| Message broker with denormalised rollups | Report queries are still fast; still premature |
| A shared `common` Maven module | Reconsidered and declined at five services (v1.2 Phase 3); unchanged by this release |
| Multi-currency, shared/household budgets | Real scope, no signal either is needed |
| CSV or bank-statement import (parsing) | Large surface area, no bank API access — distinct from this app's own JSON export/import |
