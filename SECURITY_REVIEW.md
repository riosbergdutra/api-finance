# Backend security and production-readiness review

This review separates code changes from external configuration and operational checks. A passing CI build does not establish that the target Keycloak, Redis, Stripe, database, or deployment environment is correctly configured.

## Addressed in this hardening branch

- [x] Credentialed CORS is restricted to explicitly configured origins.
- [x] Session cookies are HttpOnly, have configurable SameSite, and are Secure by default; production forces Secure.
- [x] OAuth authorization state and PKCE verifier are one-time Redis values, compared in constant time, expire after five minutes, and are bound to an HttpOnly flow cookie.
- [x] Browser access/refresh tokens are not returned to Angular; the browser holds only the opaque BFF session cookie.
- [x] JWT signature, issuer, timestamps, and Keycloak `azp` are validated.
- [x] Production requires an explicit API audience and the JWT decoder rejects tokens without that `aud` value.
- [x] Refresh operations use a Redis distributed lock, re-read the rotated refresh token, and release the lock with compare-owner Lua.
- [x] Logout removes the local Redis session before best-effort Keycloak token revocation.
- [x] Account deletion revokes all indexed BFF sessions, closes their WebSockets, cancels recurring Stripe billing before erasure, transactionally removes user-owned rows, writes a hashed identity tombstone, and queues Keycloak identity deletion in a durable retry outbox. It removes records from transactions, budgets, merchant rules, categories, goals, notifications, subscriptions, accounts, and users.
- [x] Account deletion includes a one-time Redis session scan to include sessions created before the per-user index was introduced.
- [x] Keycloak Admin API failures do not undo local erasure or allow identity recreation; a scheduled outbox worker retries provider deletion with exponential backoff.
- [x] WebSocket revocation is broadcast through Redis Pub/Sub and periodically revalidated against Redis; repeated Redis failures close local sockets (fail-closed).
- [x] Stripe webhook signatures are verified before processing; live-mode events are rejected.
- [x] Stripe event IDs are persisted in a PostgreSQL inbox in the same transaction as subscription changes, so duplicates are ignored and failed processing can be retried.
- [x] Recurring subscription updates use pessimistic row locking and persist the latest provider event timestamp/ID (V7); older events are ignored.
- [x] Non-active recurring Stripe states do not grant PRO; a cancellation event cannot be undone by an older event. Events for deleted local users cannot recreate orphan subscription rows.
- [x] Stripe subscription IDs are unique locally (V6); deletion targets the exact matching subscription.
- [x] Production uses Flyway and Hibernate validate; database migrations are versioned V1–V8.
- [x] Bounded HikariCP pool and explicit HTTP connect/read timeouts are configured.
- [x] Financial transfers update both accounts atomically with deterministic locking; historical reversal behavior is tracked by V5.
- [x] FREE-plan quota checks are serialized by a per-user subscription row lock.
- [x] Dashboard queries use a dedicated bounded executor; exports have a configurable maximum row count.
- [x] ArchUnit tests enforce key modular-monolith boundaries.

## External configuration and release checks still required

- [ ] **Keycloak audience mapper:** configure an Audience mapper on the actual BFF access token so its `aud` contains the exact `KEYCLOAK_API_AUDIENCE`. Production now requires this variable; the default documented value is `finance-api`, but the realm must be configured to issue it.
- [ ] **Keycloak admin service account:** create the separate confidential service-account client and grant only `realm-management/manage-users`. Configure its four environment variables and test deletion of a disposable identity. This cannot be verified from repository code alone.
- [ ] **End-to-end BFF validation:** run login, OAuth callback, CSRF-protected writes, refresh after access-token expiry, logout, multi-session account deletion, and WebSocket revocation against the real target Keycloak and Redis. Do not treat unit tests as proof of this deployment integration.
- [ ] **Stripe ordering rehearsal:** replay duplicate, delayed, out-of-order, same-second update/cancel, and provider retry scenarios against Stripe Test Mode. V7 orders events using Stripe's second-resolution `event.created`; if the provider emits multiple conflicting state transitions within the same second, verify the resulting state in the target test account before any live billing.
- [ ] **Migration rehearsal:** apply V1–V8 to a disposable PostgreSQL copy containing representative existing data; verify V5 transfer history, V6 duplicate detection, V7 compatibility, and backup/restore.
- [ ] **Historical transfer reconciliation:** V5 preserves the previous behavior for historical destination balances. Review actual ledger records before any backfill; never credit historical destination accounts without reconciliation.
- [ ] **Redis operations:** configure managed Redis ACLs, TLS, persistence/backup, memory limits, eviction policy, alerting, and recovery drills. Refresh tokens remain in Redis and Redis is security-critical.
- [ ] **Scheduler scale-out:** every application replica currently runs its own scheduled jobs. Before running multiple production replicas, add a distributed scheduler lock or persistent scheduler and test failover.
- [ ] **Open Finance:** the current service is a stub, not a real aggregator integration. Keep it unavailable in product claims until consent, provider integration, secure token storage, synchronization, and revocation exist.
- [ ] **Deployment hardening:** provide a production image and deployment manifest, TLS ingress, private database/cache/monitoring networks, non-default credentials, trusted proxy configuration, backup/restore drills, alerting, and incident runbooks. The checked-in Compose and Nginx files are local/example configurations.
- [ ] **Secrets:** rotate credentials previously exposed during development. Never reuse local Compose defaults in production.

## Release gate

Do not deploy publicly or enable live Stripe billing until the external configuration and release checks above are completed and verified. The repository changes are not a substitute for environment-level security testing. Stripe must remain in Test Mode.
