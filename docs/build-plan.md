# Build Plan

Work proceeds **vertically** — one feature end to end, backend and frontend, before starting the
next — so the app is runnable and demonstrable at every step rather than only at the finish.

Each step is a feature branch merged to `main` once its slice runs end to end, so `main` stays
deployable and the history reads as the build order.

## Progress

### v1.0 — the MVP

| # | Step | Status |
| --- | --- | --- |
| 1 | Scaffolding, Compose, gateway | ✅ Done |
| 2 | auth-service + gateway JWT filter + login/register | ✅ Done |
| 3 | ledger-service accounts & categories + Settings page | ✅ Done |
| 4 | ledger-service transactions + Transactions page | ✅ Done |
| 5 | Report endpoints + Dashboard page | ✅ Done |
| 6 | planning-service budgets + 70-20-10 suggester | ✅ Done |
| 7 | planning-service debts | ✅ Done |
| 8 | planning-service savings goals | ✅ Done |
| 9 | planning-service recurring bills + scheduler | ✅ Done |
| 10 | Test matrix + README | ✅ Done |

### v1.1 — mobile optimisation

Screenshot-driven fixes across two rounds. See [changelog.md](changelog.md) for the full list
with root causes.

### v1.2 — admin, auth hardening, interest, deployment

| # | Phase | Status |
| --- | --- | --- |
| 1 | Roles, email verification, password reset | ✅ Done |
| 2 | Debt interest accrual | ⬜ Deferred — skipped for now |
| 3 | admin-service (5th service) | ✅ Done |
| 4 | Admin UI, About page, feedback | ✅ Done |
| 5 | Landing and auth page | ✅ Done |
| 6 | Deployment readiness | ⬜ Not started |

---

## 1. Scaffolding ✅

Monorepo skeleton, `.gitignore`, `.env.example`, `.gitattributes`, three Postgres containers, and
the gateway. **`.env` gitignored from the very first commit** — `JWT_SECRET` and database
passwords must never reach a public repo.

*Verified:* all containers healthy; `/api/transactions` returns 401 without a token.

## 2. auth-service + gateway filter + login/register ✅

The first vertical slice, chosen deliberately: it proves the whole request path — browser →
nginx → gateway → service → Postgres — before any feature depends on it.

*Verified against real Postgres:* register 201 with bcrypt `$2a$12$` hash in the table; duplicate
email with different casing 409; wrong password 401; validation 400 with field errors; protected
route without a token 401; **spoofed `X-User-Id` 401, not 200**; Flyway history row present.

*Tests:* gateway 9/9, auth-service 8/8.

## 3–5. ledger-service ✅

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

## 6. Budgets ✅

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

## 7. Debts ✅

Both directions (owed by me, owed to me), with payments, undo, settle, and the Debts page.

**The dual write is the substance of this step.** A payment reduces the balance here *and* posts a
transaction to ledger-service, so a debt payment appears in spending reports rather than living in a
silo. The ordering, the failure modes, and the one window that stays open are documented in
[architecture.md](architecture.md#the-dual-write) — the short version is that the ledger is called
before the local commit, so a failure means "nothing happened".

Decisions worth recording:

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
- Interest rate is stored and displayed but **not accrued** — compounding is out of scope.

*Verified against real Postgres across both services:* both directions created; net position;
payment dropping the balance to ₱7,500 **and** August ledger expense rising 29,500 → 32,000 with the
transaction tagged `DEBT_PAYMENT` and pointing back at the debt; a repayment on money owed *to* the
user recording INCOME; overpayment 400 with nothing written; undo restoring the balance **and** the
ledger transaction returning 404; settling in full flipping to SETTLED and dropping out of the
totals; paying a settled debt 409.

*Tests:* 16 more in planning-service (42 total) — the dual write's captured payload, ledger-failure
propagation, undo reversing both sides and reopening a settled debt, overpayment, cross-debt payment
isolation, user scoping, and the overdue rule.

## 8. Savings goals ✅

Goals with contributions, undo, archiving, and the Goals page.

**The dual-write path is now shared.** Rather than copying the ordering rule into a second service
class, it was extracted into `LedgerWriter` and both `DebtService` and `GoalService` call it. The
rule is subtle enough — call the ledger *before* the local commit so a failure means "nothing
happened" — that having it stated in one place matters more than the handful of lines saved.
`DebtServiceTest` was updated for the refactor and stayed green, which is the point of having had it.

Where goals deliberately differ from debts:

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

## 9. Recurring bills ✅

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

## 10. Test matrix and README ✅

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

**Testcontainers integration tests were not added.** They cannot run on this machine — see
[development.md](development.md#testcontainers-cannot-reach-docker-on-some-machines) — so every
guarantee that would normally come from an integration suite (Flyway applying cleanly, the
aggregate SQL, cross-user isolation) was instead verified by exercising the real endpoints against
the running Compose stack, over all nine steps. That is weaker than a suite that runs in CI on
every push, and is recorded here as a known gap rather than papered over.

### Final count

**138 tests**, all passing, run immediately before this commit:

| Service | Tests | Covers |
| --- | --- | --- |
| gateway | 9 | JWT verification, expiry, forged signatures, **header-spoofing stripped on every path including public ones and preflight** |
| auth-service | 8 | registration, bcrypt hashing, timing-safe login, email normalisation |
| ledger-service | 20 | 70-20-10 bucket maths, zero-income division, leap February, malformed input, **5 new cross-user isolation tests** |
| planning-service | 101 | budget progress and upsert; the suggester's proportional split, rounding, history window; both debt-payment dual-write guards; goal derivation and over-saving; recurring cursor advancement (13 cases including the 31st, leap years, year boundaries); both idempotency guards; the repository-declaration regression guard; **cross-user isolation across every entity** |

### Verification checklist

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

---

# v1.2 — Admin, Auth Hardening, Interest, Deployment

v1.0 delivered the six MVP features; v1.1 was a mobile pass. v1.2 turns PesoWise from a locally
run personal app into something operable and publicly deployable: an administrative layer (which
needs a role concept that did not exist anywhere), the one MVP feature left deliberately
half-built (debt interest), the authentication gaps that make a public deployment irresponsible,
and a real deployment path.

## Decisions taken up front

| Decision | Choice | Why not the alternative |
| --- | --- | --- |
| Admin backend | A 5th service, `admin-service`, owning feedback and audit, composing cross-user stats over Feign | Endpoints bolted onto the three existing services would be cheaper, but scatter admin logic and leave feedback with no owner |
| Debt interest | Stored accrual with a scheduled monthly job | A read-time projection cannot split a payment between interest and principal, because the accrued figure is never materialised at payment time |
| Deployment | One free VM running the existing Compose stack behind Caddy | A Vercel + PaaS split loses the same-origin nginx proxy and wakes several chained JVMs on every cold request |
| About + feedback | A dedicated `/about` page, linked from Settings | Settings is for configuration; this is informational, and `SettingsPage.tsx` is already 514 lines |

Adding a fifth service deliberately triggers the "revisit at five" note in
[architecture.md](architecture.md) about a shared `common` module. Resolved in Phase 3: still no
shared module.

## Known blockers

| # | Blocker | Blocks | Mitigation |
| --- | --- | --- | --- |
| 1 | No SMTP provider account | Real delivery in Phase 1 | `MAIL_ENABLED=false` logs links and self-verifies registrations, so every flow is buildable and testable without credentials |
| 2 | Which contact details to publish | Phase 4 | Decide deliberately — a public address invites scraping; the feedback form is an alternative |
| 3 | No domain name | HTTPS in Phase 6 | Let's Encrypt will not issue for a bare IP. A DuckDNS subdomain, or a cheap `.com` |
| 4 | Oracle Cloud ARM capacity | Phase 6 | Free ARM instances are often unavailable in popular regions; all base images have arm64 variants, so ARM itself is not the risk |

Also accepted: the stack grows from 8 containers to 10, and the frontend still has no test
framework — verification stays Playwright-against-the-live-stack, as in v1.1.

## Phase 1. Roles, email verification, password reset ✅

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

### Two failures worth recording

Both were caught by running the stack, not by reading the code, and neither would have surfaced
in a unit test:

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

## Phase 2. Debt interest ⬜

Independent of every other phase; can run in parallel.

`V2__debts.sql` carries `CHECK (balance <= principal)`, payments are rejected above `balance`, and
both `paidAmount` and `percentPaid` derive from `principal − balance`. Interest touches all four.

**The design avoids fighting the constraint rather than working around it:** interest never enters
`balance`. `balance` keeps meaning *outstanding principal* and accrued interest is a separate
column. The check is dropped regardless — it stops being a meaningful invariant once interest
exists, and `balance >= 0` is the real guarantee.

`debts` gains `start_date`, `interest_method` (SIMPLE/COMPOUND), `compounding`,
`accrued_interest`, `interest_paid_total` and `last_accrued_on`. `debt_payments` gains
`principal_part` and `interest_part` — stored rather than recomputed, because reversing a payment
has to restore both columns exactly as they were.

Payments apply to interest first, then principal. Settled means both are zero. `percentPaid` is
redefined as percent of *principal* repaid and documented as such; interest is reported
separately.

A monthly job accrues, reusing the `RecurringOccurrences` idempotency pattern — a claim table
with a unique `(debt_id, period)` index — which is already proven in this codebase. **No ledger
write:** accrued interest is owed, not paid, and writing it would violate the boundary that
ledger-service owns money which has actually moved.

`DebtServiceTest` currently has **zero** interest tests; the one debt created with a rate never
asserts on it.

## Phase 3. admin-service ✅

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

### Two failures worth recording

Both were caught by running the stack, not by reading the code:

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

## Phase 4. Admin UI, About page, feedback ✅

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

### Three things found only by using the deployed app, not by reading the code

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

## Phase 5. Landing and auth page ✅

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

### Scope grew mid-phase: auth hardening and profile fields for future personalization

Requested alongside the redesign, landing in the same phase since they touch the same page:

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

## Phase 6. Deployment readiness ⬜

`docker-compose.prod.yml` as an override: stop publishing the four Postgres ports and the gateway
port, leaving only the frontend reachable behind Caddy. `architecture.md` already states this
invariant; the current Compose file violates it as a dev affordance.

Because the frontend container stays on the VM behind Caddy, the nginx `/api` proxy still
applies — requests remain same-origin, `VITE_API_URL` stays empty, and **CORS stays irrelevant**.
That is the main practical advantage over the Vercel split.

`.github/workflows/ci.yml` does not exist yet. Beyond the obvious value, it unlocks something
specific: **GitHub Actions has a working Docker daemon, so `mvn verify -Pintegration` can run the
Testcontainers tests there** — the ones that cannot run on this machine and were recorded as a
known gap at the end of v1.0. That alone justifies adding CI.

Hardening: regenerate `JWT_SECRET` and every database password, set `CORS_ALLOWED_ORIGIN` and
`PUBLIC_URL` to the real hostname, confirm both trusted headers are rejected when spoofed, and
confirm no service port answers from outside the VM.

## Explicitly deferred to v1.3

| Deferred | Why |
| --- | --- |
| PDF report export | CSV covers the need; a PDF library is a real dependency for an unasked-for format |
| A separate marketing landing page | The auth-page hero delivers most of the value |
| Refresh tokens and rotation | Still the first hardening item, but independent — and v1.2 is already large |
| Full amortisation schedules | Considered and set aside in favour of stored accrual |
| A shared `common` Maven module | Reconsidered at five services and declined |
| Rate limiting on auth endpoints | The next security item after v1.2, before any real traffic |
