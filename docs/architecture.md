# Architecture

## Decisions, and why

Each of these was a considered trade-off rather than a default.

| Decision | Chosen | Rejected alternative, and why |
| --- | --- | --- |
| Service count | **5**: gateway, auth, ledger, planning, admin | Started at 4; admin-service was added in v1.2 for feedback, audit, and cross-user overview rather than folding those into an existing service, since none of the four owns "an admin acting on the whole system" as a concept. 6+ (one per remaining domain) is more textbook-correct but means more apps to boot, migrate, and debug solo. |
| Inter-service comms | **Synchronous REST** (OpenFeign) | Kafka or RabbitMQ decouples nicely, but adds broker containers and eventual-consistency debugging for a single-user app whose totals are cheap to compute live. |
| Database topology | **One Postgres container per service** | A shared instance with a schema per service is cheaper on a laptop; the strict form was chosen for fidelity to the pattern. |
| Discovery and config | **None** — Compose DNS + env vars | Eureka and Config Server earn their keep when instances scale dynamically. Here they would be two more containers and a config repo. |
| Authentication | **Own auth-service issuing JWTs** | Keycloak is enterprise-realistic but a heavy container with real configuration overhead. |
| Shared code | **None. No `common` module** | Revisited when admin-service made it five: still declined. Extracting the ~60-line JWT/header plumbing and a handful of DTOs would couple five build lifecycles to save a small amount of duplication, and the gateway already centralises the actual security decision. |
| Budget "spent" totals | **Computed on demand** from ledger aggregates | Denormalising into planning-service is faster but introduces a cache to invalidate. On-demand stays correct for free. |

## Service boundaries

The split follows one rule: **ledger-service owns money that has moved; planning-service owns
intent about money.** That is why budgets, goals, debts, and recurring bills sit together despite
being four features — they are all statements of intent, and none of them is the record of a
transaction.

### gateway — port 8080

The only port the browser talks to. Spring Cloud Gateway, no database.

- Routes by path prefix to the owning service, including `admin-service` since v1.2.
- `JwtAuthenticationFilter` (order −100, ahead of routing) verifies the HS256 signature and
  expiry, then sets `X-User-Id` from the token subject and `X-User-Role` from the token's `role`
  claim.
- Enforces that any path under `/api/admin/**` requires `role == ADMIN`, answering **403** (not
  401 — the token is valid, the authority isn't) otherwise.
- Fails to start if `JWT_SECRET` is shorter than 32 bytes, rather than silently accepting a weak
  key.
- CORS is configured but unused in practice — the frontend proxies `/api` so all traffic is
  same-origin.

**The security invariant.** Downstream services trust `X-User-Id` and `X-User-Role`
unconditionally. Two rules keep that safe:

1. Any client-supplied `X-User-Id` or `X-User-Role` is **stripped before routing** — on every
   request, including public paths and preflights — so either header can only ever originate at
   the gateway. nginx blanks both too, as a second layer.
2. Service ports are never published to the host in production; the gateway is the only
   reachable entrypoint. (`docker-compose.prod.yml` is what actually enforces this — see
   `docs/deployment.md`.)

A request reaching a service *without* `X-User-Id` therefore bypassed the gateway, and services
answer 401. `/internal/admin/**` endpoints (see admin-service, below) go further: the gateway has
no route for `/internal/` at all, so they are unreachable from outside the Compose network
regardless of any header.

### auth-service — port 8081

Owns identity, and nothing financial.

- `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me`.
- BCrypt at strength 12.
- Emails are normalised to lowercase on write, which is what makes the unique index actually
  prevent duplicate accounts.
- Login hashes a throwaway value when the email is unknown, so a missing account and a wrong
  password take comparable time. Without this, response latency reveals which emails are
  registered.
- Both failure modes return the same message, for the same reason.

**Table:** `users(id, email, password_hash, display_name, created_at)`.

### ledger-service — port 8082

The single source of truth for money movement.

- `accounts` — wallets. Balance is derived, never stored: a stored balance is one more thing to
  keep in step, and it drifts the first time a transaction is edited.
- `categories` — kind (income/expense) plus, for expenses, the 70-20-10 bucket. A database CHECK
  enforces that only expenses carry a bucket, so the bucket report can never meet an income row
  with one.
- `transactions` — the ledger. **Amounts are always positive; `kind` carries the direction**, so
  `SUM()` needs no CASE to work out signs. `kind` is copied from the category on write rather
  than accepted from the client — letting the two disagree would corrupt every report.
- `source_type` / `source_id` record what created a row: `MANUAL`, or a planning-service
  `DEBT_PAYMENT`, `GOAL_CONTRIBUTION`, or `RECURRING_BILL`. This is the audit link back, and it is
  what keeps the ledger the only place money is recorded.
- `user_bootstrap` — marks that a user's starter data has been created. Seeding is lazy (this
  service never sees a registration event) and this marker makes it happen exactly once, so a
  user who deletes every category does not get them back.

**Aggregates are `GROUP BY` queries in Postgres**, never sums in Java — the alternative is
transferring a year of transactions to render one number. Endpoints: `/summary`, `/by-category`,
`/by-bucket`, `/daily`.

Two details worth knowing:

- `/by-category` LEFT JOINs *from* categories, so a category with no activity returns ₱0 rather
  than being absent — the budgets page needs the zero row.
- `/daily` returns every day of the month, zero-filling quiet days, because a line chart with
  gaps draws misleading straight segments across them.

**Deletes archive rather than remove** once a record is referenced. Deleting a category or account
with transactions attached would orphan them and silently rewrite historical reports.

### planning-service — port 8083

Intent, targets, and schedules. Talks to ledger-service over Feign.

- `budgets` — a limit per category per month, unique on `(user_id, category_id, period_month)`.
- `goals` + `goal_contributions`, `debts` + `debt_payments`, each contribution or payment storing
  the `ledger_txn_id` of the transaction it created.
- `recurring_bills` + `recurring_runs`, the latter carrying a unique constraint
  (`ux_recurring_runs_occurrence`) on `(bill_id, due_date)` — the scheduler must be idempotent
  because container restarts re-trigger it. The debt-interest accrual job added in v1.2 Phase 2
  mirrors this exact pattern with its own `debt_interest_accruals` table, unique on
  `(debt_id, period)`.

**Budget progress** is computed live: fetch `/api/reports/by-category` for the month, join against
the stored limits in memory, return `{limit, spent, remaining, percentUsed}`. Nothing cached.

**Dual-write, deliberately.** A debt payment or goal contribution writes a transaction to
ledger-service *and* records the returned id locally. Money exists in exactly one place — the
ledger — and planning-service holds only a pointer to it.

#### The dual-write

A debt payment has to land in two databases: the balance here, the cash movement in the ledger.
There is no distributed transaction, so the ordering is a deliberate choice.

The ledger is called **inside** the local transaction, before it commits:

```
@Transactional
  load debt, validate the amount fits
  ── Feign ──▶ ledger: POST /api/transactions/sourced
  save payment row with the returned ledger_txn_id
  reduce the balance
commit
```

If the Feign call fails, the exception rolls the local transaction back, so the failure mode is
**"nothing happened"** — recoverable by retrying. The reverse order would risk a reduced balance
with no matching transaction, which reads to the user as money that vanished.

One window remains: if the ledger write succeeds and the local commit then fails, the ledger keeps a
transaction with no payment behind it. That orphan is discoverable precisely because the ledger
stores `source_type` and `source_id` — which is what those columns are for. A single-user app does
not warrant a saga to close a window this narrow, but it is a real limitation rather than an
oversight.

Two consequences worth stating:

- The remote call happens while a database transaction is open, which holds row locks for the
  duration. Acceptable here because writes are single-user and rare; it would not be at scale, and
  is the first thing to revisit if a broker is introduced.
- **Undo deletes the ledger transaction too.** Reversing a payment while leaving the cash movement
  behind would make the two records disagree, which is worse than either outcome alone.

Deleting a *debt*, by contrast, keeps its ledger transactions. The money really did move; erasing
that would rewrite the user's spending history.

### admin-service — port 8084

Added in v1.2. Owns feedback, the audit trail, and cross-user overview — none of which fit the
"ledger owns money moved / planning owns intent" split, since an admin acting on *another user's*
data is a different kind of event from a user acting on their own.

- `feedback` — submitted by any authenticated user via `POST /api/feedback` (not admin-gated;
  the gateway route for it sits outside the `/api/admin/**` prefix). Admins list and resolve it.
- `admin_audit` — one row per admin mutation (promote/demote a user, resolve feedback, trigger an
  interest accrual pass, …), written by admin-service itself.
- **Cross-user queries live on the owning service, not here.** auth-service, ledger-service, and
  planning-service each expose their own `/internal/admin/**` endpoints (user list/stats,
  transaction volume, budget/debt/recurring counts) that only admin-service's Feign client can
  reach — the gateway has no route for `/internal/`, so they're unreachable from the internet.
  `GET /api/admin/overview` fans out to all three over Feign and degrades per-section (null +
  error marker) rather than 500ing the whole dashboard if one is down.
- Reports are streamed CSV, no PDF — see `build-plan.md`'s Phase 3 retrospective for why.

## Request flow

A dashboard load:

```
browser → nginx (/api/reports/summary?month=2026-08)
        → gateway: verify JWT, strip inbound X-User-Id, set X-User-Id=<sub>
        → ledger-service: SELECT SUM(...) GROUP BY ... WHERE user_id = ?
        ← { income, expense, net }
```

A budget progress load, showing the only cross-service hop:

```
browser → gateway → planning-service: GET /api/budgets?month=2026-08
                    ├─ SELECT limits FROM budgets WHERE user_id = ? AND period_month = ?
                    └─ Feign → ledger-service: GET /api/reports/by-category?from&to
                       joined in memory → [{ categoryId, limit, spent, remaining, percentUsed }]
```

## Data model

```
auth db                ledger db                                planning db
───────                ─────────                                ───────────
users                  accounts ──────┐                         budgets
                       categories ────┤                         goals ── goal_contributions
                                      ├──▶ transactions         debts ── debt_payments
                       user_bootstrap                           recurring_bills ── recurring_runs

admin db
────────
feedback
admin_audit
```

There are **no foreign keys across databases**. `user_id` appears in all four but is only a
value; auth-service owns the identity. Likewise planning-service stores `category_id` and
`ledger_txn_id`, and admin-service stores `user_id`/`target_id`, as plain UUIDs — a dangling
reference is possible in principle and handled by the application, which is the cost of
database-per-service.

## Conventions every service follows

- **Money:** `NUMERIC(15,2)` and `BigDecimal`. A `@DecimalMax` guard rejects absurd amounts so a
  stray digit cannot turn ₱500 into ₱5,000,000,000.
- **User scoping:** repositories expose `findByIdAndUserId`, never bare `findById`. A missing or
  foreign record is a 404.
- **Errors:** one shape — `{timestamp, status, message, fieldErrors}` — so the frontend renders
  `message` without branching. Stack traces and SQL are logged, never returned.
- **Migrations:** Flyway owns the schema; `ddl-auto: validate` fails startup on entity drift.
- **Health:** every service exposes `/actuator/health`, wired as its Compose healthcheck.
- **Associations:** stored as raw UUIDs rather than `@ManyToOne`. Nothing needs to navigate the
  object graph, and ids keep aggregates as plain SQL with no lazy-loading surprises.
