# Backend deployment checklist

## Profiles

- Local development: run with `SPRING_PROFILES_ACTIVE=dev`.
- Automated tests: use the `test` profile; it uses an isolated in-memory H2 database, with PostgreSQL Testcontainers tests for database-specific migrations and SQL.
- Production: set `SPRING_PROFILES_ACTIVE=prod` and provide every required environment variable below.

The production profile uses Hibernate `validate` and enables Flyway. Migrations included:

- `V1`: initial PostgreSQL schema.
- `V2`: unique transaction fingerprint index.
- `V3`: composite query indexes and general-budget uniqueness.
- `V4`: durable Stripe webhook inbox with event-ID uniqueness.
- `V5`: tracks whether historical transfer destination balances were credited.
- `V6`: enforces one local owner per non-null Stripe subscription ID; stops if legacy duplicates require reconciliation.
- `V7`: stores the latest applied Stripe subscription event timestamp and ID to prevent older webhook deliveries from overwriting newer subscription state.
- `V8`: adds a durable Keycloak account-deletion outbox and hashed identity tombstones to prevent erased users from being silently recreated.

For an existing database, inspect `flyway_schema_history` and compare the actual schema before deployment. Take and verify a backup before applying migrations. Do not baseline or mark migrations as applied without reviewing real data.

## Required production environment

- PostgreSQL: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`
- Redis: `REDIS_HOST`, `REDIS_PASSWORD` (TLS is enabled in production; `REDIS_PORT` defaults to 6379; `REDIS_USERNAME` defaults to `default`)
- Keycloak browser login: `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET`, `KEYCLOAK_ISSUER_URI`
- Keycloak API audience: `KEYCLOAK_API_AUDIENCE`
- Keycloak account deletion service account: `KEYCLOAK_ADMIN_BASE_URL`, `KEYCLOAK_REALM`, `KEYCLOAK_ADMIN_CLIENT_ID`, `KEYCLOAK_ADMIN_CLIENT_SECRET`
- Frontend and OAuth callback: `FRONTEND_URL`, `AUTH_REDIRECT_URI` (must exactly match the Keycloak client's registered callback URL)
- CORS: `CORS_ALLOWED_ORIGINS` (comma-separated exact origins; never use `*` with credentials)
- Cookies: `COOKIE_SECURE=true`
- Stripe Test Mode: `STRIPE_API_KEY`, `STRIPE_PRICE_ID`, `STRIPE_WEBHOOK_SECRET`

### Keycloak configuration required

1. Add an Audience mapper to the BFF access token so the configured `KEYCLOAK_API_AUDIENCE` value is present in the token's `aud` claim. The default documented audience is `finance-api`; set the environment variable to the exact configured value.
2. Create a separate confidential client for administrative account deletion and enable its service account.
3. Grant only the realm-management `manage-users` role to that service account. Do not reuse the browser-login client secret or grant full realm administration.
4. Set `KEYCLOAK_ADMIN_BASE_URL` to the Keycloak origin (scheme and host, without `/realms/... `), and set `KEYCLOAK_REALM` to the realm name.
5. Verify the service account can delete a disposable test user and that deleting the user terminates all Keycloak sessions before enabling the endpoint for real users.

The backend fails JWT validation when the configured audience is absent. Account deletion fails closed if the admin service account is not configured or Keycloak cannot confirm deletion.

## BFF session lifecycle

- Browser sessions use an opaque HttpOnly `SESSION_ID` cookie; OAuth access and refresh tokens remain in Redis.
- Redis sessions are indexed by Keycloak identity. Account deletion revokes all known sessions, including legacy sessions found during the one-time deletion scan.
- Redis Pub/Sub closes WebSockets across replicas. A periodic Redis revalidation also closes stale sockets if a Pub/Sub message is lost; after three consecutive Redis revalidation failures, the node closes its local sockets.
- Redis must be private, authenticated, TLS-enabled, backed up according to the recovery policy, and unavailable to public clients.

## Billing safety

Stripe is deliberately locked to Test Mode. The production profile hardcodes `stripe.mode=test`; the application rejects keys not beginning with `sk_test_` and rejects events marked as live. Recurring subscription webhook updates are serialized by a database row lock and older `event.created` values are ignored. Non-active provider states do not grant PRO. Do not enable live billing until provider-side event replay/reconciliation tests, pricing, cancellation, refund, and legal flows have been reviewed.

## Optional capacity controls

- `DB_POOL_MAX_SIZE` (default 10 connections per application instance)
- `DB_POOL_MIN_IDLE` (default 2)
- `SCHEDULER_POOL_SIZE` (default 3)
- `EXPORT_MAX_RECORDS` (default 10,000 records per export)

Choose pool sizes based on PostgreSQL's total connection budget across all instances and other clients.

## Reverse proxy and infrastructure

- The Nginx configuration in this repository is a local/example HTTP configuration, not a complete public TLS ingress. Terminate TLS at a trusted ingress/load balancer, forward the original scheme/host, and restrict access to the backend.
- If a load balancer sits in front of Nginx, configure trusted real-client IP handling before relying on IP-based rate limits.
- `compose.yaml` is for local development only. It contains default passwords, exposes database/cache/monitoring ports, runs Keycloak in `start-dev`, and does not build the backend image. Never deploy it unchanged to the public internet.
- Configure production image/deployment manifests, private database/cache/monitoring networks, secrets management, backups, restore drills, log retention, alerting, and operational runbooks before public launch.
