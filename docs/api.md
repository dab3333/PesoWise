# API Reference

Base URL is the gateway: `http://localhost:8080`. The frontend calls the same paths on its own
origin (`http://localhost:3000/api/...`), which nginx proxies through.

All endpoints except register and login require `Authorization: Bearer <token>`.

**Every error uses one shape**, so the frontend can render `message` without branching:

```json
{
  "timestamp": "2026-08-11T07:57:50Z",
  "status": 400,
  "message": "Please check the highlighted fields.",
  "fieldErrors": { "color": "must be a hex colour like #0f8a6c" }
}
```

`fieldErrors` is populated only for bean-validation failures; it is `{}` otherwise.

Status codes used throughout: `200` ok · `201` created · `204` deleted · `400` validation ·
`401` missing or invalid token · `404` not found *or not yours* · `409` duplicate name or blocked
delete.

---

## auth-service

### `POST /api/auth/register` → 201

```json
{ "email": "maria@example.com", "password": "sikreto123", "displayName": "Maria Santos" }
```

Password must be 8–72 characters. Email is normalised to lowercase, so `Maria@Example.COM` and
`maria@example.com` are the same account — a second attempt returns **409**.

```json
{
  "token": "eyJhbGci...",
  "expiresInSeconds": 86400,
  "user": { "id": "2aef…", "email": "maria@example.com", "displayName": "Maria Santos",
            "createdAt": "2026-08-11T07:30:01Z" }
}
```

### `POST /api/auth/login` → 200

`{ "email": "…", "password": "…" }` → the same body as register. Wrong password and unknown email
both return **401** with the identical message, by design.

### `GET /api/auth/me` → 200

Resolves the user from the gateway-injected `X-User-Id`. Returns **404** if the token is valid but
the account no longer exists.

---

## ledger-service

### Accounts

| Method | Path | Notes |
| --- | --- | --- |
| `GET` | `/api/accounts` | Seeds a Cash account on a user's first call |
| `POST` | `/api/accounts` | 201 |
| `PUT` | `/api/accounts/{id}` | |
| `DELETE` | `/api/accounts/{id}` | 204. **Archives** instead of deleting once transactions reference it |

```json
{ "name": "GCash", "type": "EWALLET", "openingBalance": "500.00" }
```

`type` is `CASH` | `BANK` | `EWALLET` | `CREDIT_CARD`. Names are unique per user,
case-insensitively — a duplicate returns **409**.

```json
{ "id": "5f47…", "name": "GCash", "type": "EWALLET",
  "openingBalance": 500.00, "balance": 54500.00 }
```

`balance` is derived (`opening + income − expense`) across **all time**, not the selected month.

### Categories

| Method | Path | Notes |
| --- | --- | --- |
| `GET` | `/api/categories` | Seeds 16 defaults on a user's first call |
| `POST` | `/api/categories` | 201 |
| `PUT` | `/api/categories/{id}` | `kind` cannot be changed → **400** |
| `DELETE` | `/api/categories/{id}` | 204. Built-ins → **409**. Archives if referenced |

```json
{ "name": "Pasalubong", "kind": "EXPENSE", "bucket": "WANTS", "color": "#db2777" }
```

- `kind`: `INCOME` | `EXPENSE`
- `bucket`: `NEEDS` | `WANTS` | `SAVINGS` — **required for expenses, rejected for income.**
  Omitting it on an expense returns **400**.
- `color`: a `#rrggbb` hex string.

### Transactions

#### `GET /api/transactions`

| Param | Default | |
| --- | --- | --- |
| `from`, `to` | the current month | An unbounded default would scan every row |
| `categoryId`, `accountId` | — | Optional filters |
| `page` | `0` | |
| `size` | `25` | Capped at 200 |

Sorted newest first, with id as a tiebreaker so paging is stable when rows share a date.

```json
{ "content": [ … ], "page": 0, "size": 25, "totalElements": 9, "totalPages": 1 }
```

#### `POST /api/transactions` → 201

```json
{ "accountId": "5f47…", "categoryId": "8c1a…", "amount": "4000.00",
  "txnDate": "2026-08-08", "note": "SM Supermarket" }
```

**The client never sends `kind`** — it is copied from the category, so the two can never disagree.
`amount` must be positive; direction comes from the category.

```json
{ "id": "…", "accountId": "…", "accountName": "GCash",
  "categoryId": "…", "categoryName": "Groceries", "categoryColor": "#0f8a6c",
  "kind": "EXPENSE", "amount": 4000.00, "txnDate": "2026-08-08",
  "note": "SM Supermarket", "sourceType": "MANUAL", "sourceId": null }
```

#### `POST /api/transactions/sourced` → 201

Used by planning-service, not the browser. Same body plus `sourceType`
(`RECURRING_BILL` | `DEBT_PAYMENT` | `GOAL_CONTRIBUTION`) and `sourceId` pointing at the
originating record.

#### `PUT /api/transactions/{id}` · `DELETE /api/transactions/{id}`

Update re-derives `kind` from the new category. Delete is a real delete (204).

### Reports

All accept `month=YYYY-MM`, defaulting to the current month. A malformed month returns **400**.

#### `GET /api/reports/summary`

```json
{ "month": "2026-08", "income": 45000.00, "expense": 29500.00, "net": 15500.00 }
```

#### `GET /api/reports/by-category?from=&to=`

Takes a date range rather than a month, because planning-service needs arbitrary windows.
**Includes categories with no activity, at ₱0** — the budgets page needs those rows.

```json
[ { "categoryId": "…", "categoryName": "Rent", "color": "#2563eb",
    "kind": "EXPENSE", "total": 15000.00 } ]
```

#### `GET /api/reports/by-bucket`

The 70-20-10 split. **Always returns all three buckets in method order**, even with no activity.

```json
[ { "bucket": "NEEDS", "targetPercent": 70, "targetAmount": 31500.00,
    "actualAmount": 21000.00, "actualPercent": 46.7 } ]
```

`targetAmount` is that share of the month's **income**. With zero income, targets and percentages
are `0` rather than a divide-by-zero.

#### `GET /api/reports/daily`

One entry per day of the month, **including days with no activity**, so the trend line has a
continuous axis.

```json
[ { "date": "2026-08-01", "income": 0, "expense": 0 } ]
```

---

## planning-service

### Savings goals

Follows the same contribution-to-ledger pattern as debt payments, with two deliberate differences:
**the target is editable**, and **over-saving is allowed**.

#### `GET /api/goals`

```json
{
  "totalTarget": 90000.00,
  "totalSaved": 12500.00,
  "activeCount": 1,
  "achievedCount": 0,
  "goals": [
    { "id": "…", "name": "Bagong laptop", "targetAmount": 50000.00,
      "savedAmount": 12500.00, "remaining": 37500.00, "percentComplete": 25.0,
      "targetDate": "2026-12-31", "daysUntilTarget": 142,
      "monthlyNeeded": 7500.00, "achieved": false, "behindSchedule": false,
      "archived": false, "note": "para sa work", "contributionCount": 1 }
  ]
}
```

- **`savedAmount` is derived**, never stored — it is `SUM(contributions)`, read in one grouped query.
  A goal has no invariant to protect (unlike a debt, where you cannot pay more than you owe), so a
  stored total would be duplication that can drift.
- **`achieved` is not a column either** — it is simply `saved >= target`.
- `remaining` is **zero once the target is met, never negative.** Over-saving is a good outcome, not
  a shortfall.
- `monthlyNeeded` is what to set aside each remaining month to land on the target date — the number
  that turns a goal into a plan. Counts calendar months **inclusive of the current one**, so a target
  inside this month asks for the whole shortfall rather than dividing by zero, and **rounds up**, so
  following it always reaches the target. Null when there is no target date or the goal is achieved.
- `behindSchedule` is only true for an unmet goal whose target date has passed.
- **Archived goals are excluded from the totals** but still returned in the list.

#### `POST /api/goals` → 201 · `PUT /api/goals/{id}`

```json
{ "name": "Bagong laptop", "targetAmount": "50000.00",
  "targetDate": "2026-12-31", "note": "para sa work", "archived": false }
```

`targetDate`, `note` and `archived` are optional. **The target amount is editable**, unlike a debt's
principal: revising what you are saving for is normal and invalidates nothing — every contribution
stays exactly as recorded, and the percentage simply recalculates.

`archived` hides a goal while keeping its history — the alternative to deleting it.

#### `DELETE /api/goals/{id}` → 204

Deletes the goal and its contribution records. **The ledger transactions are kept** — the money
really did move. Archive instead to keep the history.

#### `GET /api/goals/{id}/contributions`

```json
[ { "id": "…", "goalId": "…", "amount": 12500.00, "contributedOn": "2026-08-15",
    "note": "13th month", "ledgerTxnId": "af99a45e-…" } ]
```

#### `POST /api/goals/{id}/contributions` → 201

```json
{ "amount": "12500.00", "contributedOn": "2026-08-15",
  "accountId": "…", "categoryId": "…", "note": "13th month" }
```

A dual write, exactly as for debt payments: the contribution is stored here *and* posted to the
ledger tagged `sourceType: GOAL_CONTRIBUTION`. Choosing a `SAVINGS`-bucket category means the money
counts toward the savings share of the 70-20-10 split.

**Contributing beyond the target is accepted** (201), unlike overpaying a debt (400) — saving more
than planned is not a mistake to be prevented.

#### `DELETE /api/goals/{id}/contributions/{contributionId}` → 204

Undo. Removes the contribution **and the ledger transaction it created**.

### Debts

Tracks utang in **both directions**: `OWED_BY_ME` and `OWED_TO_ME`.

#### `GET /api/debts`

```json
{
  "totalOwedByMe": 10000.00,
  "totalOwedToMe": 3000.00,
  "netPosition": -7000.00,
  "debts": [
    { "id": "…", "name": "Utang kay Kuya Ben", "direction": "OWED_BY_ME",
      "counterparty": "Ben Reyes", "principal": 10000.00, "balance": 7500.00,
      "paidAmount": 2500.00, "percentPaid": 25.0, "interestRate": 2.500,
      "dueDate": "2026-12-31", "daysUntilDue": 142, "overdue": false,
      "status": "ACTIVE", "paymentCount": 1 }
  ]
}
```

- `netPosition` is `owedToMe − owedByMe`. Negative means you owe more than you are owed.
- **Settled debts are excluded from the totals** but still returned in the list.
- `overdue` is only ever true for an `ACTIVE` debt — a settled one is never overdue, however long
  ago its due date was.
- `interestRate` is **recorded and displayed only.** The MVP does not accrue interest; doing that
  properly needs a compounding schedule, which is out of scope.

#### `POST /api/debts` → 201

```json
{ "name": "Utang kay Kuya Ben", "direction": "OWED_BY_ME", "counterparty": "Ben Reyes",
  "principal": "10000.00", "interestRate": "2.500", "dueDate": "2026-12-31" }
```

`counterparty`, `interestRate` and `dueDate` are optional — "Pag-IBIG loan" needs no name. A new
debt starts wholly outstanding (`balance == principal`).

#### `PUT /api/debts/{id}`

Takes `name`, `counterparty`, `interestRate`, `dueDate` only. **`principal` and `direction` are
fixed after creation** — changing either would silently invalidate every payment already recorded.
The balance moves through payments alone.

#### `DELETE /api/debts/{id}` → 204

Removes the debt and its payment records. **The ledger transactions those payments created are
kept** — the money really did move, and deleting the record of it would silently rewrite the user's
spending history.

#### `GET /api/debts/{id}/payments`

```json
[ { "id": "…", "debtId": "…", "amount": 2500.00, "paidOn": "2026-08-25",
    "note": "partial", "ledgerTxnId": "ccfafffe-…" } ]
```

#### `POST /api/debts/{id}/payments` → 201

```json
{ "amount": "2500.00", "paidOn": "2026-08-25",
  "accountId": "…", "categoryId": "…", "note": "partial" }
```

**This is a dual write.** It reduces the balance here *and* posts a transaction to ledger-service,
tagged `sourceType: DEBT_PAYMENT` with `sourceId` set to the debt — so a debt payment appears in
spending reports and budget progress instead of living in a silo. The returned `ledgerTxnId` is
stored on the payment.

`accountId` and `categoryId` are **required, not inferred**: which wallet the money moved through
and how it should appear in reports are the user's decisions. The category also carries the
direction, so paying a debt records an expense and being repaid records income — the two can never
disagree.

- Overpaying returns **400**, rather than clamping. An overpayment usually means a typo, and
  silently absorbing it would hide the mistake.
- Paying an already-settled debt returns **409**.
- Paying the exact balance sets `status: SETTLED`.
- If ledger-service is unreachable the whole operation fails with **503** and the balance is
  unchanged — see the ordering note in [architecture.md](architecture.md#the-dual-write).

#### `DELETE /api/debts/{id}/payments/{paymentId}` → 204

Undo. Restores the balance, reopens the debt if it had been settled, **and deletes the ledger
transaction the payment created** — otherwise the cash movement would linger and the two records
would disagree.

### Budgets

Every endpoint takes `month=YYYY-MM`, defaulting to the current month.

**Nothing here stores a "spent" figure.** Every read fetches live totals from ledger-service over
Feign and joins them against the stored limits in memory, so a budget bar cannot show a stale
number after a transaction is edited or deleted.

If ledger-service is unreachable, these endpoints return **503** with a message saying so —
returning zeroes would tell the user they had spent nothing.

#### `GET /api/budgets`

```json
{
  "month": "2026-08",
  "income": 45000.00,
  "totalLimit": 26500.00,
  "totalSpent": 25500.00,
  "totalRemaining": 1000.00,
  "unbudgetedSpend": 4000.00,
  "budgeted": [
    { "categoryId": "…", "categoryName": "Groceries", "color": "#0f8a6c", "bucket": "NEEDS",
      "limitAmount": 5500.00, "spent": 6000.00, "remaining": -500.00,
      "percentUsed": 109.1, "overBudget": true }
  ],
  "unbudgeted": [
    { "categoryId": "…", "categoryName": "Savings", "bucket": "SAVINGS",
      "limitAmount": null, "spent": 4000.00, "remaining": null,
      "percentUsed": 0, "overBudget": false }
  ]
}
```

- `budgeted` is sorted **worst standing first** — the categories needing attention lead.
- `remaining` goes **negative when overspent**. That is deliberate: `−₱500` is the useful number,
  and clamping it to zero hides the overspend.
- `unbudgeted` lists categories with spending but no limit, and only those actually used —
  `unbudgetedSpend` is the number that quietly breaks a budget when it is not surfaced.
- Income categories never appear: the method divides spending, not earnings.

#### `PUT /api/budgets` → 204

```json
{ "categoryId": "…", "limitAmount": "5500.00" }
```

An **upsert** — "set the budget for Groceries this month" is one intention, so there is no separate
create and update. `limitAmount` must be greater than zero.

#### `PUT /api/budgets/bulk` → 204

`{ "budgets": [ { "categoryId": "…", "limitAmount": "…" }, … ] }` — up to 200 entries, applied in
**one transaction** so a suggestion saves all-or-nothing.

#### `DELETE /api/budgets/{categoryId}` → 204

Removes the limit. Transactions are untouched; the spending simply moves to `unbudgeted`.

#### `POST /api/budgets/suggestion`

```json
{ "expectedIncome": "45000.00" }
```

**A preview — nothing is saved.** The client shows the proposed limits, lets the user adjust, then
applies via the bulk endpoint.

Omit `expectedIncome` (or send `{}`) to estimate from **last month's actual income**;
`incomeWasEstimated` reports which happened. With no income recorded to estimate from, returns
**400** asking for a figure.

```json
{
  "month": "2026-09",
  "expectedIncome": 45000.00,
  "incomeWasEstimated": false,
  "buckets": [
    { "bucket": "NEEDS", "targetPercent": 70, "amount": 31500.00 },
    { "bucket": "WANTS", "targetPercent": 20, "amount": 9000.00 },
    { "bucket": "SAVINGS", "targetPercent": 10, "amount": 4500.00 }
  ],
  "lines": [
    { "categoryId": "…", "categoryName": "Rent", "bucket": "NEEDS",
      "limitAmount": 19285.71, "fromHistory": true }
  ]
}
```

How the split works, since the method itself only defines the three pools:

- Each bucket's pool is divided across its categories **in proportion to what was actually spent
  over the previous three months**. An even split would give Rent and Load & Internet the same
  limit, which nobody would accept.
- The history window **ends the month before** the one being budgeted, so a part-finished month
  cannot drag every limit down.
- A bucket with no history at all falls back to an even split, so a new user still gets a complete
  budget. `fromHistory: false` marks those lines, and the UI labels them "even split".
- Each bucket's lines **sum exactly to its pool** — the rounding remainder is given to the largest
  line, where a few centavos do not show.
- Lines that would round to zero are dropped rather than saved as invalid budgets.

#### `POST /api/budgets/copy-previous`

Copies every limit from the previous month → `{ "copied": 3 }`. Returns **404** if that month has
no budget. "Same as last month" is the common case, and retyping fifteen numbers is how people stop
budgeting.

### Recurring bills

A bill is a **transaction template plus a cursor** (`nextRunDate`). A daily pass walks the cursor
forward, recording each occurrence it satisfies.

#### `GET /api/recurring`

```json
{
  "monthlyTotal": 20300.00,
  "dueNow": [ … ],
  "bills": [
    { "id": "…", "name": "Rent", "categoryId": "…", "accountId": "…",
      "amount": 15000.00, "frequency": "MONTHLY", "dayOfPeriod": 5,
      "nextRunDate": "2026-09-05", "daysUntilDue": 25, "dueNow": false,
      "autoPost": true, "active": true, "note": null, "postedCount": 1 }
  ]
}
```

- `monthlyTotal` normalises frequencies so they can be summed: weekly bills use **52 weeks ÷ 12
  months**, not 4 per month, which would overstate them by about 8%. Inactive bills are excluded.
- `dueNow` is the same bills filtered, so the UI does not have to.
- `dayOfPeriod` is the anchor day for monthly bills — see the month-end note below.

#### `POST /api/recurring` → 201 · `PUT /api/recurring/{id}`

```json
{ "name": "Rent", "categoryId": "…", "accountId": "…", "amount": "15000.00",
  "frequency": "MONTHLY", "nextRunDate": "2026-08-05",
  "autoPost": true, "active": true, "note": "apartment" }
```

`frequency` is `WEEKLY` | `MONTHLY` | `YEARLY`. `nextRunDate` is the first occurrence, and for
monthly bills it also sets the anchor day.

**`autoPost` defaults to false**, which is the right default: a bill whose amount varies (Meralco,
water) should never post itself. Fixed amounts can opt in.

`active: false` pauses a bill without deleting it.

#### `DELETE /api/recurring/{id}` → 204

Stops the bill and deletes its run history. **The transactions it already created are kept.** Pause
it instead to stop it temporarily.

#### `GET /api/recurring/{id}/runs`

One entry per occurrence already dealt with. `skipped: true` means it was marked done without
recording anything.

#### `POST /api/recurring/{id}/post` → 201

Confirms the current occurrence — the path for `autoPost: false` bills. **409** if the bill is not
due yet, or if that occurrence was already recorded.

#### `POST /api/recurring/{id}/skip` → 201

Marks the current occurrence dealt with **without** recording a transaction — "I did not pay this
one". Advances the cursor.

#### `POST /api/recurring/run`

Runs the daily pass immediately, rather than waiting until after midnight.

```json
{ "posted": 2, "flagged": 1, "skipped": 0, "notes": [] }
```

`flagged` counts bills left for the user to confirm; `skipped` counts occurrences that were already
recorded — the idempotency guard doing its job, not an error. `notes` carries anything truncated or
failed, so a partial result is never silent.

Operates on **every** user's due bills, since the scheduler has no notion of a current user — hence
counts rather than data. **Safe to call twice**, which is the whole point of the design below.

#### Month-end behaviour

A monthly bill anchored on the 31st does not drift:

```
31 Jan → 28 Feb → 31 Mar → 30 Apr → 31 May
```

The cursor advances from the stored **anchor day**, clamped to the target month's length, rather than
from the previous (already clamped) date. Advancing from the clamped date would leave the bill on the
28th for the rest of its life. In a leap year it correctly reaches 29 February.

#### Why it cannot charge twice

The pass runs on a timer and a container restart re-triggers it, so two guards protect each
occurrence:

1. **planning-service claims first.** A `recurring_runs` row is inserted *before* the ledger is
   called, with a unique index on `(bill_id, due_date)`. A duplicate fails on the insert, before any
   money is written. Posting first would charge twice and only then discover the clash.
2. **ledger-service refuses the duplicate.** A unique index on `(user_id, source_id, txn_date)` for
   `RECURRING_BILL` rows covers the case guard 1 cannot: if the ledger write succeeds and
   planning-service then fails to commit, the claim rolls back and guard 1 is gone. The ledger
   answers 409, which is treated as "already recorded" rather than an error, because a retry could
   never succeed.

   Scoped to `RECURRING_BILL` deliberately — two debt payments or goal contributions on the same day
   are perfectly legitimate.

Missed occurrences are **caught up, not dropped** (rent that was due really was due), capped at 12
per bill per pass with the truncation reported in `notes`. Each occurrence commits in its own
transaction, and a bill that fails is logged, reported, and skipped so it cannot hold up the queue.
