# Development

## Prerequisites

Java 21, Maven 3.9+, Node 20+, Docker with Compose v2+.

## Running the stack

```bash
cp .env.example .env      # then edit: set JWT_SECRET and the three DB passwords
docker compose up --build
```

Generate a strong signing key with `openssl rand -base64 48`. `JWT_SECRET` must be at least
32 bytes — the gateway and auth-service both refuse to start otherwise, rather than silently
accepting a weak key.

| URL | |
| --- | --- |
| http://localhost:3000 | The app |
| http://localhost:8080 | Gateway (the only API entrypoint) |
| http://localhost:8080/actuator/health | Gateway health |

`.env` is gitignored and must stay that way — only `.env.example`, with placeholders, is
committed.

Check everything came up:

```bash
docker compose ps          # all containers should read (healthy)
docker compose logs -f ledger-service
```

## Frontend dev server

Faster than rebuilding the container for every change:

```bash
cd frontend
npm install
npm run dev                # http://localhost:3000, proxies /api to the gateway
```

Vite proxies `/api` to `localhost:8080`, and nginx does the same inside the container, so requests
are always same-origin and CORS never enters the picture.

## Tests

```bash
mvn test                            # in any service — unit tests
mvn verify -Pintegration            # adds the Testcontainers suite
cd frontend && npx tsc -b           # typecheck
```

### Testcontainers cannot reach Docker on some machines

Container-backed tests carry JUnit `@Tag("integration")` and surefire excludes that group, so
`mvn test` stays green everywhere and the container suite is opt-in.

This is not tidiness. On the development machine (Docker Desktop 29.6.2, Server API 1.55), every
Testcontainers client strategy reaches the named pipe and gets `HTTP 400` back from an almost-empty
`/info` response, despite `docker compose` working perfectly. None of these helped:

- `DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine`
- `DOCKER_API_VERSION=1.44`
- forcing `EnvironmentAndSystemPropertyClientProviderStrategy`
- Testcontainers 1.20.x and 1.21.3

A contributing factor is `~/.testcontainers.properties`, which may pin
`docker.client.strategy=…NpipeSocketClientProviderStrategy` — hard-coded to `docker_engine`, the
wrong pipe. Deleting that file is worth trying, but was not sufficient on its own.

**The integration tests are correct code**; do not delete them as broken. Run them where Docker
cooperates (CI, Linux), and in the meantime verify database-backed behaviour by exercising the real
endpoints against the running Compose stack — see below.

## Verifying by hand

Get a token, then call anything:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"maria@example.com","password":"sikreto123"}' \
  | python -c "import sys,json; print(json.load(sys.stdin)['token'])")

curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/categories
curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/reports/by-bucket?month=2026-08"
```

Two checks worth repeating after any change to auth or routing:

```bash
# No token must be 401, not 200
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/transactions

# A spoofed identity header must be stripped — 401, never 200
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/auth/me \
  -H "X-User-Id: 00000000-0000-4000-8000-000000000000"
```

## Database access

```bash
docker exec -it pesowise-postgres-ledger psql -U pesowise -d pesowise_ledger

\dt                                        -- tables
SELECT version, description, success FROM flyway_schema_history;
```

Ports are also published to the host for GUI clients: auth `5433`, ledger `5434`,
planning `5435`.

## Adding a migration

Never edit an applied migration — Flyway validates checksums and will refuse to start. Add
`V2__describe_change.sql` alongside `V1__init.sql` in the service's
`src/main/resources/db/migration/`.

To reset a service's database entirely during development:

```bash
docker compose down
docker volume rm pesowise_postgres-ledger-data
docker compose up -d
```

## Gotchas worth knowing

- **`ddl-auto: validate`** means a mismatch between an entity and the migrated schema fails
  startup. That is intentional — it catches drift immediately rather than at the first query.
- **Money is `BigDecimal`.** Never introduce `double` into an amount path.
- **Plain dates are parsed as local time** in the frontend. `new Date('2026-08-11')` is UTC
  midnight, which renders as the 10th in UTC+8; use the `parseLocalDate` helper.
- **`X-User-Id` is stripped at the gateway** on every request. If a service sees it missing, the
  request bypassed the gateway and gets a 401.
