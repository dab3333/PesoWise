# CLAUDE.md

Source of truth for any agent working in this repo. `docs/*.md` is the detailed reference —
this file is the map to it, plus the things that are easy to get wrong or aren't written down
anywhere else. If something here conflicts with the code, the code wins; fix this file, don't
trust it blindly.

## What this is

PesoWise — a personal budgeting web app for the Philippine market (₱, 70-20-10 envelope
method), modelled on [Lista](https://www.lista.com.ph/personal-budgeting-app). Repo:
https://github.com/dab3333/PesoWise.

**The microservices split is a deliberate learning goal, stated explicitly by the developer —
not just a product requirement.** Do not propose collapsing it to a monolith "for simplicity."
If the split is genuinely causing pain, say so plainly and let the user decide; don't quietly
route around it.

Single-user personal finance. Every record is scoped to one `user_id` — no household or
multi-member concept, deliberately deferred (see `docs/requirements.md`).

## Services

Five Spring Boot services + a Vite/React frontend, all in one Compose stack.

| Service | Port | Owns |
| --- | --- | --- |
| `gateway` | 8080 | Only port the browser talks to. JWT verification, `X-User-Id`/`X-User-Role` header injection. No database. |
| `auth-service` | 8081 | Identity: users, roles, email verification, password reset. |
| `ledger-service` | 8082 | Money that has **actually moved**: accounts, categories, transactions. |
| `planning-service` | 8083 | **Intent** about money: budgets, goals, debts (+ interest accrual), recurring bills. Calls ledger-service over Feign. |
| `admin-service` | 8084 | Feedback, audit log, cross-user overview. Calls the other three over Feign, at `/internal/admin/**` (not reachable through the gateway). |

Frontend: React 19 + TanStack Query + Tailwind v4, served by nginx, proxying `/api` to the
gateway so all browser traffic is same-origin and CORS never enters the picture (same trick the
Vite dev proxy uses locally).

Database-per-service: one Postgres container per backend service (4 total — auth, ledger,
planning, admin). No cross-database foreign keys; `user_id` is a plain value everywhere except
auth-service, which owns identity.

**Read `docs/architecture.md` before touching service boundaries, the dual-write pattern (debt
payments / goal contributions write to ledger-service *inside* the local transaction, before
commit — ordering is deliberate, see that doc), or the request-flow diagrams.**

## Non-negotiable conventions

- **Money is `BigDecimal` in Java, `NUMERIC(15,2)` in Postgres. Never `double`, ever, anywhere
  in a money path.** PHP only, `₱1,234.56` via one shared `formatPeso()` helper — never
  hand-rolled per component.
- **User scoping:** repositories expose `findByIdAndUserId`, never bare `findById`. A record
  belonging to another user returns **404, not 403** — 403 would confirm the id exists to
  someone who shouldn't know that.
- **`X-User-Id` and `X-User-Role` are gateway-injected and stripped from every inbound request**
  (gateway filter + nginx, both layers). A service seeing either header missing on an
  otherwise-valid request means the request bypassed the gateway — that's a 401/403, not a bug
  to "fix" by trusting the header anyway.
- **Migrations are Flyway, `ddl-auto: validate`.** Never edit an applied migration — Flyway
  checksums it and refuses to start. Add a new `Vn__description.sql`. A schema/entity mismatch
  fails startup on purpose, so it's caught immediately rather than at the first query.
- **Errors are one shape** across every service: `{timestamp, status, message, fieldErrors}`.
  Stack traces and SQL are logged, never returned to the client.
- **Associations are raw UUIDs**, not `@ManyToOne` — no lazy-loading surprises, aggregates stay
  plain SQL.
- **Dates:** plain date strings parse as local time in the frontend (`parseLocalDate`), never
  `new Date('2026-08-11')` directly — that's UTC midnight and renders as the wrong day in UTC+8.

## Testing

```bash
mvn test                     # unit tests only, in any service — always green
mvn verify -Pintegration     # adds Testcontainers — see the gotcha below
cd frontend && npx tsc -b && npm run lint
```

**Testcontainers cannot reach Docker Desktop on this development machine** (confirmed: every
client strategy gets `HTTP 400` from the named pipe despite `docker compose` working fine).
Container-backed tests carry `@Tag("integration")` and are excluded from `mvn test` for exactly
this reason — **they are correct code, not broken tests; do not delete them.** Run them in CI
(GitHub Actions has a working Docker daemon — see `.github/workflows/ci.yml`) or on Linux. Until
then, verify database-backed behavior by exercising real endpoints against the running
`docker compose` stack — that's the pattern used throughout this project's history (see
`docs/changelog.md` and `docs/build-plan.md` for examples of that verification style).

There is no frontend unit-test framework by choice — verification is Playwright (or manual)
against the live Compose stack, at both desktop and mobile viewports (360×740 / 440×956 have
been the standard mobile test sizes). Screenshot before *and after* claiming a visual fix; a fix
that "should" work per reading the CSS has been wrong more than once in this project's history.

## Running it

```bash
cp .env.example .env         # edit: JWT_SECRET (openssl rand -base64 48), 4 DB passwords
docker compose up -d --build
```

Frontend dev server (`cd frontend && npm run dev`) proxies `/api` to `localhost:8080` — faster
than rebuilding the container per change. `.env` is gitignored; never commit real secrets, only
`.env.example` with placeholders.

Production is the **same stack** with an override layered on:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

This resets every service's `ports` to empty except Caddy's 80/443 (Caddy fronts the frontend
container with automatic Let's Encrypt via `PESOWISE_DOMAIN`). **The override uses the `!reset
[]` YAML merge tag deliberately** — plain `ports: []` gets *concatenated* with the base file's
list by Compose's default merge rules, not replaced, which would silently leave every dev port
published. Requires Compose v2.24+. Full runbook: `docs/deployment.md`.

## Branches

`main` is the mainline and stays deployable — work merges in as vertical, end-to-end slices
(backend + frontend for one feature together), not by layer. `dev` and `staging` exist as
integration/pre-prod branches, both currently tracking `main`'s history at the point they were
cut; no branching workflow (e.g. required PR reviews, protected branches) is enforced yet beyond
that convention.

## Documentation

Docs live in `docs/`, indexed from `docs/README.md`. Keep them current as features land — this
has been true since the first commit and the user has called it out explicitly more than once.

| Doc | What's in it |
| --- | --- |
| `requirements.md` | Scope, what's explicitly deferred and why |
| `architecture.md` | Service boundaries, the dual-write pattern, request-flow diagrams |
| `api.md` | Endpoint reference |
| `design-system.md` | Visual rules — see below, read before any UI work |
| `build-plan.md` | Phase-by-phase status + retrospectives; the record of *why* each decision was made |
| `changelog.md` | Root-caused bug history, mostly from mobile-viewport testing |
| `development.md` | Local setup, the Testcontainers gotcha, hand-verification curl recipes |
| `deployment.md` | Production runbook: provisioning, backups, upgrades, secret rotation |

## Design rules (read before any UI work)

Full detail in `docs/design-system.md`. The parts most likely to get violated by habit:

- **No gradients, anywhere.** Not in CSS, not in chart fills, not in text. Flat solid fills only.
- **One accent color** — jade. Everything else is neutral except semantic colors (income/jade,
  expense/red, warning/amber, info/blue), which signal state only, never decoration.
- No heavy shadows or glassmorphism — separation is a 1px border and whitespace. At most one
  soft shadow, reserved for modals/dropdowns.
- Money and figures use tabular numerals (`font-variant-numeric: tabular-nums`) — non-negotiable
  for a budgeting app; misaligned peso columns are an instant tell.
- Charts: pick the form the data's *job* demands (see the table in `design-system.md`), not the
  first chart type that comes to mind — the doc explains why several "obvious" choices (donut
  for categories, dual-axis for two money series) were deliberately rejected.

## Working style established in this project

- Verify by running the app, not by reading the code — this repo's history is full of fixes
  that looked right on paper and weren't (see `docs/changelog.md`'s root-cause writeups).
  Playwright against the live Docker Compose stack is the standard verification method.
- When a plan has open blockers (a missing credential, an undecided design choice), say so and
  ask rather than picking a default silently — several v1.2 blockers (mail provider, About-page
  contact channels) were resolved this way, not by guessing.
- Retrospectives in `build-plan.md` are written *after* each phase ships, documenting what was
  simplified from the original plan and why, and what bugs were only caught by running the
  service. Keep that habit — it's the project's institutional memory.
- Git: only commit/push when explicitly asked. Commit messages describe the *why*, not a diff
  summary.
