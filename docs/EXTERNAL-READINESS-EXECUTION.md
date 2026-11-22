# External production-readiness execution

This runbook adds executable, guarded checks. It does not claim that external services have been tested until a run produces evidence.

## 1. Keycloak staging

Configure a protected GitHub Environment named `financeflow-staging` with required reviewers and environment-scoped secrets. Do not use production credentials.

Required secrets:
- `STAGING_BASE_URL`: public HTTPS origin of the staging backend.
- `KEYCLOAK_ISSUER_URI`: exact HTTPS realm issuer URL.
- `KEYCLOAK_ADMIN_BASE_URL`: HTTPS Keycloak base URL.
- `KEYCLOAK_REALM`.
- `KEYCLOAK_ADMIN_CLIENT_ID` and `KEYCLOAK_ADMIN_CLIENT_SECRET`: confidential service account with only the required user-management role.
- `KEYCLOAK_SMOKE_CLIENT_ID` and `KEYCLOAK_SMOKE_CLIENT_SECRET`: dedicated client whose access token receives the backend audience.
- `KEYCLOAK_API_AUDIENCE`: exact audience configured in the backend.

Run the manual GitHub Actions workflow **Staging external readiness**. It verifies TLS, OIDC discovery, JWKS, backend readiness, service-account access and the smoke client's issuer/audience claims. JWT claim decoding is not signature verification; the backend's resource-server validation remains the authoritative signature/issuer/audience check.

For an explicitly disposable user, set `KEYCLOAK_TEST_USER_ID` and optionally `KEYCLOAK_DELETE_TEST_USER=true`. The script refuses deletion unless the username starts with `financeflow-it-`. Keep deletion disabled by default.

The smoke workflow does not automate browser authorization-code login, PKCE/state, CSRF, refresh-token rotation, concurrent refresh, logout, multi-session revocation or WebSocket closure. Those require a browser E2E suite with disposable users and must be recorded separately before release.

## 2. Isolated PostgreSQL restore and V1–V9 rehearsal

Create a **sanitized custom-format backup from a disposable/staging copy at Flyway V2**. Never pass a production backup or point this script at a running application database.

Run:
```bash
SANITIZED_BACKUP_FILE=/absolute/path/finance-sanitized-v2.dump \
  bash scripts/ops/restore-and-migrate-drill.sh
```

The script creates one uniquely named disposable PostgreSQL 16 container, uses an ephemeral anonymous data volume, restores into `finance_restore_drill`, requires the latest successful numeric Flyway version to be exactly 2, validates and applies migrations from the repository, and requires V9 at the end. It removes only the container it created. It does not run `docker compose down`, prune volumes, or connect to the user's existing Postgres container.

This rehearses the schema transition on a sanitized representative copy. It does not replace application-level checks of V5 transfer/balance invariants, V6 duplicate-data assumptions, row counts and financial reconciliation. Capture those results before declaring migration readiness. If the backup is not a V2 snapshot, the script stops before migration.

## 3. Stripe Test Mode webhooks

Configure the staging backend with its Stripe **test** API key and the signing secret belonging to the registered Test Mode webhook endpoint. Register the exact staging webhook URL in the Stripe test account. Configure GitHub Environment secrets:
- `STRIPE_API_KEY` (must start with `sk_test_`);
- `STRIPE_WEBHOOK_ENDPOINT_ID`;
- `STRIPE_WEBHOOK_ENDPOINT_URL` (exact HTTPS URL).

The manual workflow can trigger `payment_intent.succeeded` and `customer.subscription.deleted` through Stripe CLI. The script first retrieves the endpoint and refuses to run unless it is enabled, Test Mode and matches the exact URL. After triggering, verify successful delivery (HTTP 2xx) in Stripe Workbench and verify application idempotency/state. CLI trigger acceptance alone is not proof that the backend processed the webhook.

Never add live keys or production signing secrets to GitHub Actions.

## 4. Staging load test

The workflow has a separate opt-in input `run_load_test`, default false. It runs only when manually selected, uses the protected `financeflow-staging` environment, and starts at 5 VUs for 2 minutes against `STAGING_BASE_URL`. Keep the target on staging; do not use production. Stop if error rate, p95, database/Redis saturation or memory/GC crosses the agreed stop thresholds. Increase concurrency only after reviewing the first run and available headroom.

## 5. Alertmanager notification

Create a file outside the repository containing exactly one HTTPS webhook URL for the chosen incident receiver. Restrict file permissions:
```bash
install -m 600 /dev/null /secure/path/financeflow-alertmanager-webhook-url
# Populate the file through your secrets manager; do not paste the URL in shell history.
export ALERTMANAGER_WEBHOOK_URL_FILE=/secure/path/financeflow-alertmanager-webhook-url
```

Validate and start the opt-in Alertmanager profile:
```bash
docker compose -f compose.yaml -f compose.alertmanager.yaml config --quiet
docker compose -f compose.yaml -f compose.alertmanager.yaml --profile alerting up -d alertmanager
```

Prometheus is configured to send firing/resolved alerts to `alertmanager:9093`. The receiver URL is mounted as a Docker secret and is not stored in Git. Confirm Alertmanager shows the receiver, trigger a controlled test alert in staging, and confirm delivery at the destination. The destination must accept Prometheus Alertmanager's generic webhook JSON payload.

## 6. TLS and secrets management

`ops/production/nginx-tls.conf.example` is a reference, not a deployable certificate bundle. Replace the example hostname, mount certificate/key through the platform's secret facility, verify the complete external-to-backend trust chain, and test renewal/rotation before enabling HSTS. Keep PostgreSQL, Redis, Keycloak administration, Actuator, Prometheus and Grafana on private networks. Never expose backend management ports directly.

Use a managed secrets manager (cloud KMS/Secrets Manager/Vault or equivalent) for database, Redis, OAuth, Keycloak admin, Stripe, SMTP and session secrets. GitHub Environment secrets are for the gated staging workflow, not a substitute for production runtime secret delivery. Set required reviewers on `financeflow-staging`, restrict deployment branches, and use short-lived credentials where supported.

## 7. Release evidence

Attach redacted evidence for:
- Keycloak OIDC/JWKS, audience, admin least privilege and browser E2E;
- restore + Flyway V2-to-V9 result and V5/V6 data invariants;
- Stripe Test Mode endpoint delivery and idempotency;
- staging k6 results with host sizing and p50/p95/p99;
- Alertmanager test notification receipt;
- TLS scan, secret rotation and network exposure review.

Do not mark production ready until all critical evidence exists. No workflow in this repository deploys to production or changes a production account.
