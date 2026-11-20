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
- [x] Add isolated H2 configuration for the application-context test.
- [x] Correct service unit-test fixtures and missing mocks without disabling Mockito strictness.

## Must be resolved before public production launch

- [ ] **OAuth2 login CSRF and PKCE:** the current manual authorization-code flow does not generate/validate `state` or use PKCE. Bind state and PKCE verifier to the initiating browser, validate them on callback, and coordinate the request contract with the frontend. Do not rely on the unused Java-serialized cookie repository as a substitute.
- [ ] **Stripe webhook reliability:** processing is currently asynchronous and exceptions are logged/swallowed. The controller can acknowledge an event before durable processing succeeds. Add durable event receipt, unique event-ID idempotency, and retry-safe processing; return a retryable failure when processing cannot be completed.
- [ ] **Stripe subscription lifecycle:** verify cancellation events update the exact subscription by Stripe subscription ID. Current deletion handling invokes the general expiration scheduler rather than targeting the event's subscription.
- [ ] **Account deletion and privacy:** verify foreign-key cascades and permanently remove all user-owned data according to the retention policy. The current flow deletes the local user row and logs out only the supplied session; it does not invalidate every Redis session or delete/disable the Keycloak identity.
- [ ] **Database migrations:** create and review a baseline Flyway migration before enabling Flyway. The production profile currently disables Flyway and uses Hibernate `validate`, so the schema must be provisioned separately before startup.
- [ ] **JWT authorization:** review issuer, audience, authorized-party, role/scope, and token-expiry validation. Confirm every business endpoint is protected and that no endpoint accidentally trusts a client-supplied user ID.
- [ ] **Redis session protection:** confirm managed Redis access controls, TLS, backups/retention, and operational invalidation of all sessions on password reset/account deletion. Refresh tokens are stored in Redis session records.
- [ ] **Secrets and infrastructure:** rotate any credentials previously exposed during development. Never reuse the local credentials in `compose.yaml`; replace default Keycloak, PostgreSQL, and Grafana credentials and restrict monitoring endpoints before deployment.
- [ ] **Operational readiness:** add controller/security integration tests, webhook signature/idempotency tests, backup-and-restore verification, rate limiting for authentication endpoints, and alerting for auth/webhook failures.

## Release gate

Do not deploy publicly or enable live Stripe billing until the unchecked items above have been reviewed, tested, and explicitly approved. Billing must remain in Stripe Test Mode for the current demonstrative product.
