<h1>PesoWise</h1>

Personal budgeting for the Philippines — envelope-style monthly budgets using the **70-20-10
method**, debt (utang) payoff tracking, savings goals, recurring bills, and spending insights.

Built as Spring Boot microservices with a React frontend, running on Docker Compose.

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
| Backend | Java 21, Spring Boot 3.4, Spring Cloud Gateway, Spring Data JPA, Flyway |
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

## Documentation

Full docs live in **[docs/](docs/)**:

- **[requirements.md](docs/requirements.md)** — what the MVP does, and what is deliberately out of scope
- **[architecture.md](docs/architecture.md)** — service boundaries, the reasoning, and the data model
- **[api.md](docs/api.md)** — every endpoint with request and response shapes
- **[build-plan.md](docs/build-plan.md)** — the ten build steps and current progress
- **[design-system.md](docs/design-system.md)** — palette, typography, and component rules
- **[development.md](docs/development.md)** — running, testing, and the known environment gotchas

## Tests

```bash
mvn test                      # unit tests, in any service directory
mvn verify -Pintegration      # adds the Testcontainers suite
```

Container-backed tests are tagged `integration` and excluded from `mvn test`, because
Testcontainers cannot reach the Docker daemon on every machine — see
[development.md](docs/development.md#testcontainers-cannot-reach-docker-on-some-machines).

## Status

Steps 1–5 of 10 in [build-plan.md](docs/build-plan.md): auth and the full ledger backend are done
and verified; the ledger's three pages and all of `planning-service` are in progress.
