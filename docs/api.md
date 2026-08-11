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

Budgets, debts, goals, and recurring bills — documented as each build step lands
(steps 6 through 9 in [build-plan.md](build-plan.md)).
