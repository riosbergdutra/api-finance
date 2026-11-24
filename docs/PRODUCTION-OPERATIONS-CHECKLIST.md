# Production operations readiness

This checklist is an execution record, not a declaration that production is ready. Never run migration, restore, deletion, or load-test commands against the production database without an approved change window and a verified recovery point.

## 1. Keycloak — required before production

### API audience
1. In the target realm, create an Audience protocol mapper on the access-token client scope (or a dedicated scope assigned to the backend client).
2. Configure the mapper to add the exact audience expected by `KEYCLOAK_API_AUDIENCE` to the **access token**.
3. Confirm the resulting access token contains that audience in `aud`; do not infer success from the ID token.
4. Set `KEYCLOAK_API_AUDIENCE` to the exact value and test a valid token, a token with the wrong audience, an expired token, and an untrusted issuer.
5. Do not paste tokens into tickets, chat, CI logs, or shell history.

### Administrative client for account deletion
1. Create a separate confidential client in the same realm; enable its service account.
2. Grant only the minimum required `realm-management/manage-users` role. Do not grant realm-admin.
3. Configure `KEYCLOAK_ADMIN_BASE_URL`, `KEYCLOAK_REALM`, `KEYCLOAK_ADMIN_CLIENT_ID`, and `KEYCLOAK_ADMIN_CLIENT_SECRET` in the production secrets manager.
4. Verify the service account can delete a disposable test user and cannot administer unrelated realm resources.
5. Exercise retry/backoff and outbox recovery with Keycloak temporarily unavailable.
6. Rotate the client secret after the test and store it only in the secrets manager.

## 2. PostgreSQL backup and restore

Perform this only against an isolated recovery environment. Use a dedicated least-privilege backup identity and a secure secret mechanism such as `PGPASSFILE`; never put passwords in command arguments.

Example custom-format backup:

```bash
umask 077
# Configure PGHOST, PGPORT, PGDATABASE and PGUSER in the environment.
# Supply the password only through a protected PGPASSFILE (chmod 600).
pg_dump --format=custom --no-owner --no-acl \
  --file="finance-$(date +%Y%m%d-%H%M%S).dump"
pg_restore --list finance-YYYYMMDD-HHMMSS.dump
sha256sum finance-YYYYMMDD-HHMMSS.dump
```

Restore rehearsal (target must be a newly created, isolated database; never use the production database name):

```bash
createdb finance_restore_drill
pg_restore --exit-on-error --no-owner --no-acl \
  --dbname=finance_restore_drill finance-YYYYMMDD-HHMMSS.dump
```

After restore, verify schema objects, row counts for critical tables, account balances and transfer invariants, Flyway history/checksums, application startup in validation mode, and a representative read-only workflow. Record backup timestamp, checksum, restore duration, data-loss window, and any errors. A successful `pg_restore` alone is not proof of application-level recovery.

## 3. Flyway V1–V9 rehearsal

- Take a fresh backup and restore it into an isolated PostgreSQL instance/database.
- Preserve a copy of `flyway_schema_history` before changing anything.
- Run Flyway validation first; stop on checksum mismatch or failed migration.
- Apply migrations using the exact release artifact and production configuration, but point only to the isolated database.
- Check V5 transfer/balance invariants and V6 duplicate-data assumptions before/after migration.
- Confirm V1–V9 are successful and checksums match the release.
- Run application smoke tests and inspect query plans on representative, sanitized data.
- Do not repair Flyway checksums or edit already-applied migrations as a shortcut. Resolve discrepancies with a reviewed forward migration or a documented database-specific recovery plan.

## 4. Keycloak end-to-end acceptance

Use disposable accounts in a non-production realm with production-equivalent configuration. Verify:
- authorization redirect, state and PKCE validation, callback and cookie attributes;
- CSRF rejection for unsafe requests without a valid token;
- refresh success, refresh-token rotation, concurrent refresh, expired/invalid grant;
- logout revokes the server-side session and rejects subsequent requests;
- multiple sessions are revoked on account deletion;
- deletion outbox retries when Keycloak is unavailable and eventually removes the disposable identity;
- WebSocket closes after logout/deletion, including across two backend instances.

Capture outcomes and correlation IDs only; redact cookies, authorization headers, tokens, secrets, email addresses and personal data.

## 5. Stripe Test Mode

- Keep `stripe.mode=test`; do not configure live keys or enable live billing during this rehearsal.
- Use a Stripe test account and a disposable customer/subscription.
- Deliver signed test webhook events through the official Stripe tooling.
- Verify duplicate delivery, delayed/out-of-order events, same-timestamp conflicts, invalid signatures, cancellation, retry and deleted-account behavior.
- Confirm idempotency and database state after each event.
- Never store webhook signing secrets or full payloads containing personal data in logs.

## 6. Production network, TLS and secrets

- Terminate TLS at a managed ingress/reverse proxy; redirect HTTP to HTTPS and enable HSTS only after HTTPS is verified end-to-end.
- Keep PostgreSQL, Redis, Keycloak administration, Actuator, Prometheus and Grafana on private networks. Do not publish database/cache/management ports to the internet.
- Restrict direct access to the backend port; Nginx path restrictions do not protect a backend port that is independently reachable.
- Use a secrets manager for database, Redis, OAuth, Keycloak admin, Stripe test, SMTP and cookie-signing/session secrets. No defaults, placeholders or secrets in Compose files, source control, images or CI output.
- Use separate database/Keycloak identities and databases/schemas according to the deployment design; the development Compose sharing of the `finance` database is not a production template.
- Pin container images to reviewed versions/digests, scan images/dependencies, configure resource limits and non-root execution.
- Confirm cookie `Secure`, HttpOnly, SameSite and trusted proxy/forwarded-header behavior behind TLS.

## 7. Monitoring and incident readiness

- Verify Prometheus scrapes backend, Redis and Nginx exporters; alert rules are loaded and alerts reach an actively monitored destination.
- Monitor request rate, 5xx rate, p95 latency, JVM heap/GC, CPU/memory, Hikari active/idle/pending, Redis availability/latency/memory, PostgreSQL connections/locks/slow queries, WebSocket connections and scheduler/outbox backlog.
- Establish service-specific SLOs from measured baseline; alert thresholds in the repository are starting signals, not validated capacity guarantees.
- Test alert delivery and document on-call ownership, escalation, rollback, backup restoration and credential rotation.
- Keep dashboards and logs free of credentials, tokens, session IDs and financial/identity payloads.

## Release gate

Do not call the service production-ready until all critical external checks above have evidence, restore and migration rehearsals pass, the real integration tests pass, load results meet agreed SLOs, and operational alerts/rollback are exercised. Open Finance remains a stub unless separately implemented and certified.
