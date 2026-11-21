# Backend security and production-readiness review

This file tracks verified code-level risks that remain before a public production launch. The production profile is intentionally conservative, but it does not by itself make the application production-ready.

## Addressed in this hardening branch

- [x] Restrict credentialed CORS to explicitly configured origins.
- [x] Make the session cookie's `Secure` attribute configurable and default it to secure.
- [x] Separate local development, test, and production configuration.
- [x] Use Hibernate `validate` in the production profile; no automatic schema creation or update.
- [x] Require production database, Keycloak, Redis, Stripe test-mode, callback, and CORS configuration through environment variables.
- [x] Require TLS and credentials for production Redis.
- [x] Keep Stripe in Test Mode and reject live API keys and live webhook events.
- [x] Add isolated H2 configuration for application-context tests and PostgreSQL Testcontainers migration/query tests.
- [x] Correct service unit-test fixtures and missing mocks without disabling Mockito strictness.
- [x] Bind OAuth `state` and PKCE verifier to a one-time Redis login attempt; compare state in constant time, expire attempts after five minutes, and bind the opaque flow ID to an HttpOnly cookie.
- [x] Process Stripe webhooks synchronously; persist event IDs in a PostgreSQL inbox in the same transaction as subscription changes so duplicate deliveries are ignored and failures can be retried.
- [x] Apply Stripe subscription deletion to the exact local subscription matching the provider subscription ID.
- [x] Exempt only the signature-verified Stripe webhook endpoint from browser CSRF protection; browser-authenticated write endpoints remain under CSRF.
- [x] Configure explicit RestClient connect/read timeouts and a bounded HikariCP pool.
- [x] Serialize concurrent balance changes on the same account with a PostgreSQL pessimistic row lock.
- [x] Apply confirmed transfers to both source and destination accounts in one database transaction; lock both accounts in deterministic order, reject self/cross-currency/inactive destinations, and reverse both sides on update/delete. A V5 flag preserves the behavior of historical transfers during reversal.
- [x] Enforce uniqueness of non-null Stripe subscription IDs in PostgreSQL so a deletion webhook cannot ambiguously target multiple local users (V6).
- [x] Bound Excel/PDF exports by a configurable maximum row count and return HTTP 413 instead of loading an unbounded number of transactions.
- [x] Add composite indexes for transaction date-range queries, goal deadlines, subscription expiration, active accounts, and monthly budgets.
- [x] Enforce one general budget per user/month/year with a partial unique index; migration fails safely if legacy duplicates need manual reconciliation.
- [x] Add Nginx WebSocket upgrade forwarding and apply rate limiting to root-level API paths as well as `/api/`.

## Must be resolved before public production launch

- [ ] **Subscription data reconciliation:** V6 fails safely if a Stripe subscription ID is duplicated in legacy rows. Inspect and reconcile those records before deployment if the migration reports duplicates.
- [ ] **Historical transfer reconciliation:** V5 deliberately marks old transfers as not having credited their destination, because that is what the previous code did. Review existing transfer records and account balances before any backfill; do not automatically credit historical destinations without reconciling the ledger and confirming the intended accounting policy.
- [ ] **Account deletion and privacy:** verify foreign-key cascades and permanently remove all user-owned data according to the retention policy. The current flow deletes the local user row and logs out only the supplied session; it does not invalidate every Redis session or delete/disable the Keycloak identity.
- [ ] **JWT authorization:** review issuer, audience, authorized-party, role/scope, and token-expiry validation. Confirm every business endpoint is protected and that no endpoint accidentally trusts a client-supplied user ID.
- [ ] **Redis session protection:** confirm managed Redis access controls, backups/retention, and operational invalidation of all sessions on password reset/account deletion. Refresh tokens are stored in Redis session records. Redis Pub/Sub revocation is best-effort; a lost Pub/Sub message can leave a remote WebSocket connected until another validation occurs.
- [ ] **Production schema rehearsal:** V1–V4 exist and Flyway is enabled in the production profile, but the existing production database (if any) must be inspected and migration rehearsal/backup-restore must be completed before deployment.
- [ ] **Open Finance:** `OpenFinanceService` is intentionally a stub and does not connect to a real aggregator. Keep this feature unavailable in product claims until a provider integration, consent lifecycle, secure token storage, synchronization, and revocation are implemented and tested.
- [ ] **Operational readiness:** verify rate limits with the actual ingress topology, add controller/security tests for all sensitive routes, test Stripe delivery with valid signatures and transient database failures, configure backup-and-restore drills, and add alerting for authentication/webhook/database failures.
- [ ] **Deployment hardening:** `compose.yaml` and `nginx/nginx.conf` are local/example configurations. Provide a production image/deployment manifest, TLS ingress, non-default credentials, private database/cache/monitoring networks, and a reviewed secrets-management process.
- [ ] **Secrets:** rotate any credentials previously exposed during development. Never reuse the local credentials in `compose.yaml`; replace default Keycloak, PostgreSQL, and Grafana credentials and restrict monitoring endpoints before deployment.

## Release gate

Do not deploy publicly or enable live Stripe billing until the unchecked items above have been reviewed, tested, and explicitly approved. Billing must remain in Stripe Test Mode for the current demonstrative product.
