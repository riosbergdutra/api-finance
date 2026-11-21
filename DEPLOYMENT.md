# Backend deployment checklist

## Profiles

- Local development: run with `SPRING_PROFILES_ACTIVE=dev`.
- Automated tests: use the `test` profile; it uses an isolated in-memory H2 database, with a PostgreSQL Testcontainers test for database-specific migrations and SQL.
- Production: set `SPRING_PROFILES_ACTIVE=prod` and provide every required environment variable below.

The production profile uses Hibernate `validate` and enables Flyway. Flyway migrations currently included are:

- `V1`: initial PostgreSQL schema.
- `V2`: unique transaction fingerprint index.
- `V3`: composite query indexes and uniqueness for general budgets.
- `V4`: durable Stripe webhook inbox with event-ID uniqueness.
- `V5`: tracks whether a transfer's destination account balance was credited, preserving safe reversal of historical transfers.

For a new production database, Flyway applies the migrations in order. For an existing database, first inspect `flyway_schema_history` and compare the real schema with the migration history. Do not baseline an existing database or mark migrations as applied without reviewing the actual schema and data. Take and verify a backup before the first production migration.

## Required production environment

- `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`
- `REDIS_HOST`, `REDIS_PASSWORD` (production Redis uses TLS; `REDIS_PORT` defaults to `6379`; `REDIS_USERNAME` defaults to `default`)
- `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET`, `KEYCLOAK_ISSUER_URI`
- `CORS_ALLOWED_ORIGINS` (comma-separated exact origins; never use `*` with credentials)
- `FRONTEND_URL`, `AUTH_REDIRECT_URI` (must exactly match the Keycloak client's registered callback URL)
- `COOKIE_SECURE=true`
- `STRIPE_API_KEY`, `STRIPE_PRICE_ID`, `STRIPE_WEBHOOK_SECRET`

Optional capacity controls:

- `DB_POOL_MAX_SIZE` (default 10 connections per application instance)
- `DB_POOL_MIN_IDLE` (default 2)
- `SCHEDULER_POOL_SIZE` (default 3)
- `EXPORT_MAX_RECORDS` (default 10,000 records per export)

Choose database pool sizes based on PostgreSQL's total connection budget across all application instances and other clients. Do not increase the pool based only on the number of virtual threads.

## Billing safety

Stripe is deliberately locked to Test Mode. The production profile hardcodes `stripe.mode=test`; the application also rejects any key that does not begin with `sk_test_` and rejects webhook events marked as live. Do not replace this restriction until the product owner explicitly approves real billing and the legal, pricing, cancellation, and refund flows have been reviewed.

## Reverse proxy and infrastructure

- The Nginx configuration in this repository is a local/example HTTP configuration, not a complete public TLS ingress. Terminate TLS at a trusted ingress/load balancer, forward the original scheme/host, and restrict access to the backend.
- Nginx now forwards WebSocket upgrades for `/ws/` and rate-limits both authentication and root-level API routes. If a load balancer sits in front of Nginx, configure trusted real-client IP handling before relying on IP-based rate limits.
- `compose.yaml` is for local development only. It contains default passwords, exposes database/cache/monitoring ports, runs Keycloak in `start-dev`, and does not build the backend image. Never deploy it unchanged to the public internet.
- Configure monitoring endpoints, backups, restore drills, log retention, alerting, and secrets management in the target hosting environment before public launch.
