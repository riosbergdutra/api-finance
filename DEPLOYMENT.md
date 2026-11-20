# Backend deployment checklist

## Profiles

- Local development: run with `SPRING_PROFILES_ACTIVE=dev`.
- Automated tests: use the `test` profile; it uses an isolated in-memory H2 database.
- Production: set `SPRING_PROFILES_ACTIVE=prod` and provide every required environment variable below.

The production profile uses Hibernate `validate`; it will not create, update, or drop production tables. Flyway remains disabled until a reviewed baseline migration is added. Provision the schema explicitly before deploying this profile.

## Required production environment

- `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`
- `REDIS_HOST`, `REDIS_PASSWORD` (production Redis uses TLS; `REDIS_PORT` defaults to `6379`; `REDIS_USERNAME` defaults to `default`)
- `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET`, `KEYCLOAK_ISSUER_URI`
- `CORS_ALLOWED_ORIGINS` (comma-separated exact origins; never use `*` with credentials)
- `FRONTEND_URL`, `AUTH_REDIRECT_URI` (must exactly match the Keycloak client's registered callback URL)
- `COOKIE_SECURE=true`
- `STRIPE_API_KEY`, `STRIPE_PRICE_ID`, `STRIPE_WEBHOOK_SECRET`

## Billing safety

Stripe is deliberately locked to Test Mode. The production profile hardcodes `stripe.mode=test`; the application also rejects any key that does not begin with `sk_test_` and rejects webhook events marked as live. Do not replace this restriction until the product owner explicitly approves real billing and the legal, pricing, cancellation, and refund flows have been reviewed.
