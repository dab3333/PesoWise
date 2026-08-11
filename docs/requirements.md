# Requirements

## Goal

A personal budgeting app that answers three questions honestly: where did my money go, am I
inside my budget, and am I making progress on debt and savings. Modelled on
[Lista](https://www.lista.com.ph/personal-budgeting-app) in scope and tone, with PesoWise's own
visual identity.

Secondary goal, stated explicitly by the developer: **learn Spring Boot microservices**. The
architecture is therefore chosen for what it teaches as much as for what it delivers, and should
not be collapsed into a monolith on grounds of simplicity.

## Users

Single-user personal finance. Every record is scoped to one `user_id`; there is no household,
sharing, or multi-member concept. Adding one later means introducing a membership layer in every
service, which is why it was deliberately left out of the MVP.

## In scope for the MVP

All six features below are v1, not phased.

### 1. Transactions and categories

- Log income and expenses with amount, date, category, account, and an optional note.
- Full create, read, update, delete.
- Filter the list by date range, category, and account, paged.
- Categories carry a **kind** (income or expense) and, for expenses, a **70-20-10 bucket**
  (needs, wants, or savings). Each has a stored colour so charts stay consistent.
- A new user is seeded with 16 Philippine-flavoured categories (Salary, Groceries, Rent,
  Utilities, Load & Internet, Dining Out, Padala, Savings, Debt Payment, …) and one Cash account,
  so the app is usable immediately rather than presenting an empty shell.
- **Accounts** are wallets: Cash, bank, e-wallet (GCash, Maya), credit card. A balance is always
  derived as `opening balance + income − expense`, never stored.

### 2. Monthly budgets per category

- Set a peso limit per category per month.
- See spent, remaining, and percent used, updating as soon as a transaction is added.
- **Suggested budget** using the 70-20-10 method: given expected monthly income, allocate 70% to
  needs, 20% to wants, 10% to savings, distributed across the categories in each bucket. The user
  can accept it wholesale or adjust any line.

### 3. Dashboard and insights

For the selected month, with month-to-month navigation:

- Total in, total out, and net.
- Spend by category (donut).
- Daily spend trend (line), covering every day of the month including quiet ones.
- Budget progress bars, coloured by how close to the cap.
- 70-20-10 actual versus target.
- Upcoming bills due.

### 4. Debt tracker (utang)

- Track both directions: money the user owes, and money owed to them.
- Name, principal, current balance, optional interest rate, due date.
- Record a payment: it reduces the balance **and** writes a matching transaction to the ledger,
  so debt payments appear in spending reports rather than living in a silo.

### 5. Savings goals

- Named goal with a target amount and target date.
- Contributions roll up into a progress figure, and each contribution also writes a ledger
  transaction, exactly as debt payments do.

### 6. Recurring bills

- Rent, utilities, subscriptions: a template with an amount, category, account, and frequency
  (monthly, weekly, or yearly).
- A daily scheduler either auto-posts the transaction or flags the bill as due, depending on the
  bill's `autoPost` setting.
- Posting is **idempotent** — a container restart must not double-charge a bill.

## Non-functional requirements

- **Currency:** PHP only. Money is `NUMERIC(15,2)` in Postgres and `BigDecimal` in Java. Never
  `double`, never floating point.
- **Money display:** `₱1,234.56` from a single shared formatter, with tabular numerals so columns
  of figures align.
- **Isolation:** every query filters by `user_id`. A record belonging to another user must return
  404, not 403 — a 403 would confirm the id exists.
- **Dates:** a transaction dated the 11th must display as the 11th in UTC+8. Plain dates are
  parsed as local, never as UTC midnight.
- **Auth:** JWT, verified once at the gateway. Services trust the injected `X-User-Id` header,
  which the gateway strips from every inbound request so it can only originate there.

## Explicitly out of scope for the MVP

Deferred deliberately, each with the reason:

| Deferred | Why |
| --- | --- |
| Refresh tokens and rotation | A 24-hour token is adequate for a local-first personal app; noted as the first v2 hardening item |
| Transfers between accounts | Not among the six agreed features; needs a two-sided transaction model |
| Multi-currency | PHP-only keeps the money model and every aggregate simple |
| Receipt or photo attachments | Requires object storage, which the Compose stack does not have |
| CSV or bank import | Large surface area, and no bank API access |
| Shared or household budgets | Would add a membership and permission layer to every service |
| Push or email notifications | Needs an external provider and delivery handling |
| Message broker with denormalised rollups | The natural next step once report queries get slow, but premature while they are fast |
| Eureka and Spring Cloud Config | Only earn their keep in dynamically scaled deployments |
| Kubernetes manifests | Compose is the target runtime for now |
| Playwright end-to-end tests | Deferred to keep build time down; API-level verification covers the flows |
