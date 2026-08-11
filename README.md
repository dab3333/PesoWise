<h1>PesoWise</h1>

Personal budgeting for the Philippines — envelope-style monthly budgets using the **70-20-10
method**, debt (utang) payoff tracking, savings goals, recurring bills, and spending insights.

Built as Spring Boot microservices with a React frontend, running on Docker Compose.

**Status: v1 complete.** All ten build steps are done, verified against the running stack, and
covered by 138 passing tests. See [Status](#status) below.

## Quick start

```bash
cp .env.example .env      # then set JWT_SECRET and the three DB passwords
docker compose up --build
```

Open **http://localhost:3000** and register an account. New users are seeded with a Cash account
and 16 Philippine-flavoured categories, each pre-tagged with its 70-20-10 bucket, so the app is
usable immediately.

Generate a signing key with `openssl rand -base64 48` — `JWT_SECRET` must be at least 32 bytes or
the services refuse to start.

## Stack

| Layer | |
| --- | --- |
| Backend | Java 21, Spring Boot 3.4, Spring Cloud Gateway, Spring Data JPA, Flyway, OpenFeign |
| Frontend | React 19, TypeScript, Vite, Tailwind v4, TanStack Query, Recharts |
| Data | PostgreSQL 16 — one instance per service |
| Runtime | Docker Compose, nginx |

## Services

| Service | Port | Owns |
| --- | --- | --- |
| `gateway` | 8080 | Routing, JWT verification, the only entrypoint |
| `auth-service` | 8081 | Registration, login, JWT issuing |
| `ledger-service` | 8082 | Accounts, categories, transactions, report aggregates |
| `planning-service` | 8083 | Budgets, debts, goals, recurring bills |
| `frontend` | 3000 | React SPA, proxies `/api` to the gateway |

Services communicate over synchronous REST. No message broker, no service discovery — Compose DNS
and environment variables.

## Features

- **Transactions & categories** — full CRUD, paged and filtered, with a lazily-seeded starter set
  of 16 Philippine-flavoured categories pre-tagged with their 70-20-10 bucket.
- **Budgets** — live progress computed from real ledger totals (never cached), plus a suggester
  that splits the 70-20-10 method's three pools across categories in proportion to spending
  history.
- **Debts (utang)** — both directions, with a dual write to the ledger on every payment so debt
  activity shows up in spending reports, and full undo.
- **Savings goals** — the same dual-write pattern as debts, but with a derived (never stored)
  saved total and over-saving allowed rather than rejected.
- **Recurring bills** — a daily scheduler with two independent idempotency guards, so neither a
  re-run nor a container restart can charge a bill twice.
- **Dashboard** — spend-by-category, a daily trend line, the 70-20-10 split, and budget progress,
  all chosen and validated per the [design system](docs/design-system.md)'s chart rules.

## Documentation

Full docs live in **[docs/](docs/)**:

- **[requirements.md](docs/requirements.md)** — what the MVP does, and what is deliberately out of scope
- **[architecture.md](docs/architecture.md)** — service boundaries, the reasoning, and the data model
- **[api.md](docs/api.md)** — every endpoint with request and response shapes
- **[build-plan.md](docs/build-plan.md)** — the ten build steps, what was verified at each, and the final test matrix
- **[design-system.md](docs/design-system.md)** — palette, typography, and component rules
- **[development.md](docs/development.md)** — running, testing, and the known environment gotchas

## Tests

```bash
mvn test                      # unit tests, in any service directory
mvn verify -Pintegration      # adds the Testcontainers suite
```

**138 tests passing**: gateway 9, auth-service 8, ledger-service 20, planning-service 101.
Includes an explicit cross-user isolation test for every owned entity across both ledger-service
and planning-service — a lookup against another user's record returns 404, never 403, so a
guessed id cannot even confirm a record exists.

Container-backed tests are tagged `integration` and excluded from `mvn test`, because
Testcontainers cannot reach the Docker daemon on every machine — see
[development.md](docs/development.md#testcontainers-cannot-reach-docker-on-some-machines). In its
place, everything an integration suite would normally guarantee (migrations applying cleanly, the
report aggregate SQL, cross-user isolation, the recurring-bill idempotency guards) was verified by
exercising the real endpoints against the running Compose stack at every build step — including an
actual container restart to prove the recurring-bill guard holds, not just a mocked one.

## Status

All ten steps in [build-plan.md](docs/build-plan.md) are done: auth, the full ledger, budgets with
the 70-20-10 suggester, debts in both directions, savings goals, recurring bills with an
idempotent scheduler, and the closing test-matrix pass — across all seven pages
(Dashboard, Transactions, Budgets, Debts, Goals, Recurring, Settings).

**Known gap:** Testcontainers integration tests could not be run on this development machine (see
above), so isolation and migration guarantees rest on manual verification against the deployed
stack rather than a suite that runs in CI on every push. Documented rather than hidden — this is
the first thing to close if the project moves to a CI pipeline.

Deferred to v2, listed with reasoning in [requirements.md](docs/requirements.md): refresh tokens,
multi-currency, receipt attachments, bank import, shared/household budgets, notifications, a
message broker, and Kubernetes manifests.
