# PesoWise Documentation

Personal budgeting web app for the Philippine market — envelope-style monthly budgeting using
the 70-20-10 method, debt (utang) payoff tracking, savings goals, recurring bills, and spending
insights. Built as Spring Boot microservices with a React frontend, on Docker Compose.

Reference point for scope and tone: [Lista](https://www.lista.com.ph/personal-budgeting-app).

## Start here

| Document | What it covers |
| --- | --- |
| [requirements.md](requirements.md) | What the MVP does and does not include, feature by feature, and the decisions behind the scope |
| [architecture.md](architecture.md) | The four services, why the boundaries fall where they do, the data model, and how a request flows |
| [api.md](api.md) | Every endpoint, with request and response shapes |
| [build-plan.md](build-plan.md) | The ten build steps, in order, with current progress |
| [design-system.md](design-system.md) | Visual direction: palette, typography, components, and the rules that keep it coherent |
| [development.md](development.md) | Running the stack, running tests, and the known environment gotchas |

## The short version

Four services, each owning its own Postgres database:

```
                    ┌──────────────┐
   browser  ───────▶│   frontend   │  nginx: serves the SPA, proxies /api
                    │   (React)    │
                    └──────┬───────┘
                           │  /api/**
                    ┌──────▼───────┐
                    │   gateway    │  verifies the JWT, injects X-User-Id
                    └──┬────┬──────┘
              ┌────────┘    └────────┬──────────────┐
              ▼                      ▼              │
      ┌──────────────┐      ┌────────────────┐      │ OpenFeign
      │ auth-service │      │ ledger-service │◀─────┤ (reads spend totals,
      └──────┬───────┘      └───────┬────────┘      │  posts transactions)
             │                      │               │
       ┌─────▼─────┐          ┌─────▼─────┐   ┌─────┴──────────┐
       │ postgres  │          │ postgres  │   │ planning-      │
       │  (auth)   │          │ (ledger)  │   │ service        │
       └───────────┘          └───────────┘   └─────┬──────────┘
                                                    │
                                              ┌─────▼─────┐
                                              │ postgres  │
                                              │(planning) │
                                              └───────────┘
```

- **ledger-service** is the single source of truth for money that has actually moved.
- **planning-service** holds intent — targets, schedules, and balances owed — and asks
  ledger-service for actuals rather than keeping its own copy.
- No message broker. Every cross-service call is synchronous REST.
