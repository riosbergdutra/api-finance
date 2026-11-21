# BFF session authentication

FinanceFlow uses a Backend-for-Frontend (BFF) session model. The browser never receives Keycloak access or refresh tokens.

## Authentication flow

1. The browser navigates to `GET /auth/login`.
2. The API creates a short-lived OAuth flow record in Redis and sets the `OAUTH_FLOW` HttpOnly cookie.
3. Keycloak returns the authorization code to `GET /auth/callback`. The API validates one-time state, completes the PKCE exchange, validates the signed access token (issuer, time claims, `azp`, and the configured API audience), and stores access/refresh tokens in the Redis-backed `AuthSession`.
4. The API sets an opaque `SESSION_ID` HttpOnly cookie and redirects to the frontend callback route.
5. The frontend calls `GET /auth/session`; protected API requests authenticate from the session cookie. Access-token renewal happens only on the backend.
6. Unsafe browser requests obtain a CSRF token from `GET /auth/csrf` and send it in the `X-XSRF-TOKEN` header.
7. `POST /auth/logout` deletes the local Redis session first, publishes revocation, closes sockets on the current replica and broadcasts the revocation to other replicas. Keycloak refresh-token revocation is best-effort; local BFF revocation is authoritative.

## Session lifecycle and WebSockets

- Every new Redis session is indexed by Keycloak subject in a Redis set and by Spring Data Redis secondary index.
- Account deletion enumerates all indexed sessions, includes a scan for pre-index legacy sessions, deletes them, and broadcasts each revocation before deleting the provider identity.
- WebSocket sockets retain only the opaque BFF session ID in server-side attributes. The browser sends the HttpOnly cookie in the handshake; OAuth tokens never appear in the URL.
- Redis Pub/Sub is best-effort, so each replica periodically checks its active WebSocket session IDs against Redis. A missing session closes its sockets. After three consecutive Redis revalidation failures, the replica closes its local WebSockets (fail-closed).
- The revalidation interval defaults to 60 seconds and can be tuned with `APP_BFF_WEBSOCKET_REVALIDATION_INTERVAL` only if the property is exposed through deployment configuration.

## Account deletion

`DELETE /auth/me` performs the following sequence:

1. Resolve the Keycloak subject from the authenticated JWT.
2. Revoke all known BFF Redis sessions and publish WebSocket revocation events.
3. Cancel any recurring Stripe subscription; if Stripe cannot confirm cancellation, account deletion stops so billing is not silently left active.
4. Delete the Keycloak identity through a separate confidential service-account client using the Admin REST API.
5. Delete all local user-owned records and write a hashed identity tombstone plus a durable Keycloak deletion outbox record in one database transaction. The provider identity is deleted immediately when possible; otherwise a scheduled worker retries with backoff.

The deletion endpoint fails closed when Redis or Stripe cannot complete session revocation/billing cancellation. Local financial data erasure, the hashed identity tombstone, and the provider-deletion outbox are committed atomically. The Keycloak Admin API deletion is attempted immediately and retried with exponential backoff after restarts until it succeeds. The tombstone prevents OAuth callback from silently recreating the erased local account while provider deletion is pending. The service account must be configured with only the realm-management `manage-users` role. Do not reuse the browser login client secret.

## Deployment settings

- `CORS_ALLOWED_ORIGINS`: exact frontend origins, comma-separated. Wildcard origins must not be used with credentialed requests.
- `COOKIE_SECURE=true` in HTTPS deployments.
- `COOKIE_SAME_SITE`: normally `Lax` when frontend and API are same-site. Use `None` only when they are on different sites; this requires Secure and HTTPS.
- `FRONTEND_URL`: frontend origin used after the Keycloak callback.
- `AUTH_REDIRECT_URI`: backend callback URL registered in Keycloak.
- `KEYCLOAK_API_AUDIENCE`: exact audience that the Keycloak access token must contain. Configure an Audience mapper in the realm/client.
- `KEYCLOAK_ADMIN_BASE_URL`, `KEYCLOAK_REALM`, `KEYCLOAK_ADMIN_CLIENT_ID`, `KEYCLOAK_ADMIN_CLIENT_SECRET`: separate Keycloak service-account configuration for account deletion.

The CSRF cookie is intentionally readable by frontend JavaScript; it is not an authentication credential. The authentication cookie remains HttpOnly.

## Operational requirements

- Keep Redis private, authenticated, TLS-enabled, monitored, and backed up in production.
- Never log session IDs, access tokens, refresh tokens, authorization codes, or CSRF tokens. OAuth tokens are excluded from the Redis entity's generated `toString()`.
- Keep all Keycloak secrets exclusively in backend secret storage.
- Verify the actual access token contains the configured audience and `azp` before enabling production traffic.
- Test login, callback, refresh, CSRF, logout, multi-session deletion, and cross-replica WebSocket revocation against the deployed Keycloak and Redis.
- Use HTTPS in production. Do not set `SameSite=None` without Secure.
