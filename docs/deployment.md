# Deployment

A production deploy is the same Compose stack used in development, run on a real VM with
`docker-compose.prod.yml` layered on top: it removes every published service port except
Caddy's 80/443, and Caddy terminates TLS and reverse-proxies to the frontend container. Nothing
else about the app changes shape — same five services, same four Postgres containers, same
nginx `/api` proxy inside the frontend image, so requests stay same-origin and CORS never
enters the picture, exactly as in dev.

## Provisioning

Any VM with Docker and Compose v2.24+ (needed for the `!reset` merge tag used in
`docker-compose.prod.yml`) and at least 4 vCPU / 8GB RAM works — the stack runs five JVMs and
four Postgres instances. The free-tier target this plan was built against is an Oracle Cloud
Always Free ARM (Ampere) instance; every base image in use (`eclipse-temurin`,
`postgres:16-alpine`, `nginx:alpine`, `caddy:2-alpine`) publishes `arm64` variants, so ARM is not
a technical risk. Oracle's ARM capacity is frequently exhausted in popular regions — retry across
regions, or fall back to any other VM with a public IPv4 address.

Before the first boot:

1. A domain or subdomain whose A record points at the VM's public IP. Let's Encrypt (which Caddy
   uses automatically) will not issue a certificate for a bare IP. A free DuckDNS subdomain is
   enough.
2. Ports 80 and 443 open in the VM's firewall/security list — both are required for the ACME
   HTTP-01 challenge, not just 443.
3. Docker + Compose v2.24 or newer installed.

## First boot

```bash
git clone <repo-url> pesowise && cd pesowise
cp .env.example .env
```

Edit `.env`:

- `JWT_SECRET` — a fresh value from `openssl rand -base64 48`. Never reuse the dev value.
- The four `*_DB_PASSWORD` values — fresh, distinct passwords.
- `PESOWISE_ADMIN_EMAILS` — the address(es) that should become ADMIN on first login/registration.
- `MAIL_ENABLED=true`, plus `SMTP_USERNAME`/`SMTP_PASSWORD` from your provider (Brevo's free tier
  is the default host/port). Leaving mail disabled in production means nobody but you can ever
  confirm an email address.
- `PUBLIC_URL=https://your-domain` — becomes both the link target inside verification/reset
  emails and the gateway's `CORS_ALLOWED_ORIGIN` in the prod override.
- `PESOWISE_DOMAIN=your-domain` — the bare hostname Caddy requests a certificate for.

Then:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
docker compose ps          # every container should read (healthy)
docker compose logs -f caddy   # confirm the certificate issued with no ACME errors
```

Register the admin account through the running app once it's reachable at `https://your-domain`.

## Verifying a deploy

- `docker compose ps` — all ten containers healthy (five services, four Postgres, Caddy).
- `curl -I https://your-domain` — valid certificate, no browser warning.
- From outside the VM, confirm nothing else answers: `nmap -p 1-9000 your-domain` should show
  only 80 and 443 open. (`docker-compose.prod.yml`'s `!reset []` on every service's `ports` is
  what makes this true — if a scan shows a Postgres or gateway port open, the override didn't
  take effect; re-check the Compose version.)
- Send a spoofed `X-User-Id` and `X-User-Role: ADMIN` header directly at a request through the
  proxy and confirm both are ignored (the response reflects the real token's identity, not the
  header) — the same check used in dev, now against the real deployment.
- Register a real account and confirm the verification email actually arrives (with
  `MAIL_ENABLED=true`, nothing is written to the logs as a fallback).

## Backups

Four independent databases now (auth, ledger, planning, admin). A daily cron entry per database,
dumping to a directory outside the containers:

```bash
docker exec pesowise-postgres-auth     pg_dump -U "$AUTH_DB_USER"     "$AUTH_DB_NAME"     > backups/auth-$(date +%F).sql
docker exec pesowise-postgres-ledger   pg_dump -U "$LEDGER_DB_USER"   "$LEDGER_DB_NAME"   > backups/ledger-$(date +%F).sql
docker exec pesowise-postgres-planning pg_dump -U "$PLANNING_DB_USER" "$PLANNING_DB_NAME" > backups/planning-$(date +%F).sql
docker exec pesowise-postgres-admin    pg_dump -U "$ADMIN_DB_USER"    "$ADMIN_DB_NAME"    > backups/admin-$(date +%F).sql
```

Restore with `docker exec -i <container> psql -U <user> <db> < backup.sql` against a freshly
started, empty database (Flyway will have already created the schema — restore before the app's
first write, or into a container with `docker compose stop <service>` first).

## Logs

```bash
docker compose logs -f <service>            # tail one service
docker compose logs --since 1h auth-service # e.g. reading a mail-send failure
```

Nothing ships to an external log aggregator — Compose's own log driver is the only log store.
For anything past casual debugging, `docker compose logs > snapshot.log` before container
restarts recycle the ring buffer.

## Upgrading

```bash
git pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

Compose recreates only the containers whose image changed. Flyway migrations run automatically
on each service's startup, in order, and are additive by convention (see `architecture.md` on
migrations) — there is no separate migration step to run by hand.

## Rotating `JWT_SECRET`

Every issued token becomes invalid the instant the secret changes — there is no dual-secret grace
period, so this signs every logged-in user out.

1. Generate a new value: `openssl rand -base64 48`.
2. Update `.env`.
3. `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d auth-service gateway`
   — only these two hold the secret (auth-service signs, gateway verifies); no other service
   needs restarting.
4. Confirm: an old token now gets 401, a fresh login works.

Rotate database passwords the same way, but scoped to the owning service + its Postgres
container, one pair at a time, to avoid a stack-wide outage.
