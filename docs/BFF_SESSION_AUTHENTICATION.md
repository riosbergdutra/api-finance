# BFF session authentication

FinanceFlow uses a Backend-for-Frontend (BFF) session model. The browser never receives
Keycloak access or refresh tokens.

## Authentication flow

1. The browser navigates to `GET /auth/login`.
2. The API creates a short-lived OAuth flow record in Redis and sets the
   `OAUTH_FLOW` HttpOnly cookie.
3. Keycloak returns the authorization code to `GET /auth/callback`.
   The API validates state, completes the PKCE exchange, and stores the access
   and refresh tokens in the Redis-backed `AuthSession`.
4. The API sets an opaque `SESSION_ID` HttpOnly cookie and redirects to the
   frontend callback route.
5. The frontend calls `GET /auth/session`; protected API requests authenticate
   from the session cookie. Access-token renewal happens only on the backend.
6. Unsafe browser requests first obtain a CSRF token from `GET /auth/csrf` and
   send it in the `X-XSRF-TOKEN` header.
7. `POST /auth/logout` revokes the refresh token when possible, deletes the
   Redis session, and clears the browser cookie.

WebSocket notifications use the same `SESSION_ID` cookie during the handshake.
OAuth tokens must never be placed in query strings, browser storage, or frontend
JavaScript state.

## Deployment settings

- `CORS_ALLOWED_ORIGINS`: exact frontend origins, comma-separated. Wildcard
  origins must not be used with credentialed requests.
- `COOKIE_SECURE`: `true` in HTTPS deployments.
- `COOKIE_SAME_SITE`: normally `Lax` when frontend and API are same-site.
  Use `None` only when they are on different sites; this requires
  `COOKIE_SECURE=true` and HTTPS.
- `FRONTEND_URL`: frontend origin used after the Keycloak callback.
- `AUTH_REDIRECT_URI`: backend callback URL registered in Keycloak.

The CSRF cookie is intentionally readable by frontend JavaScript; it is not an
authentication credential. The authentication cookie remains HttpOnly.

## Operational requirements

- Keep Redis private and TLS-enabled in production.
- Never log session IDs, access tokens, refresh tokens, authorization codes, or
  CSRF tokens.
- Keep the Keycloak client secret exclusively in backend secret storage.
- Verify CORS origins, cookie settings, and the Keycloak redirect URI in each
  deployment environment.
- Use HTTPS in production. Do not set `SameSite=None` without `Secure`.
