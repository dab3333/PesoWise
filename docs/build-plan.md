# Build Plan

Work proceeds **vertically** — one feature end to end, backend and frontend, before starting the
next — so the app is runnable and demonstrable at every step rather than only at the finish.

Each step is a feature branch merged to `main` once its slice runs end to end, so `main` stays
deployable and the history reads as the build order.

## Progress

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
