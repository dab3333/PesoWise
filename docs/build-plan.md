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
| 7 | planning-service debts | ⬜ Not started |
| 8 | planning-service savings goals | ⬜ Not started |
| 9 | planning-service recurring bills + scheduler | ⬜ Not started |
| 10 | Test matrix + README | ⬜ Not started |

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

## 7. Debts

Both directions (owed by me, owed to me). A payment reduces the balance **and** posts a
transaction to ledger-service via Feign, storing the returned `ledger_txn_id`.

## 8. Savings goals

Same contribution-to-ledger pattern as debts, so the two share one approach.

## 9. Recurring bills

The `@Scheduled` job, the `recurring_runs` idempotency guard, and the upcoming-bills widget. The
guard matters because container restarts re-trigger the job — without it, a bill double-charges.

## 10. Test matrix and README

- **Unit:** budget progress maths, the 70-20-10 allocator, debt balance reduction,
  `next_run_date` advancement per frequency — especially month-end (a bill on day 31 in February).
- **Integration (Testcontainers):** the report aggregate SQL across month boundaries, Flyway
  applying cleanly, and **an explicit per-service test that user A cannot read or mutate user B's
  rows**.
- Feign mocked with WireMock in planning-service tests, so ledger-service need not be running.

## Verification checklist

End-to-end, in the browser, once all steps land:

1. `docker compose ps` — all healthy.
2. Register; confirm redirect to the dashboard.
3. Confirm seeded categories; add a GCash account.
4. Add income and expenses across two different months.
5. Set a budget; confirm spent/remaining matches, and that the bar updates immediately after
   adding another transaction — this proves both query invalidation and the Feign round trip.
6. Apply "suggest budget"; confirm the 70-20-10 split matches the entered income.
7. Record a debt payment; confirm the balance drops **and** a matching ledger transaction appears.
8. Add a goal contribution; same dual-write check.
9. Create an auto-posting recurring bill dated today, trigger the scheduler, confirm exactly one
   transaction. **Run it twice** to prove idempotency.
10. Confirm the charts render and month navigation works.
11. `curl` without a token → 401; with a token, only that user's rows.
12. `mvn test` green in all four services.
