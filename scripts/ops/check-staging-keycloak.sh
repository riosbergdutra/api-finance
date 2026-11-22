#!/usr/bin/env bash
set -Eeuo pipefail
set +x

required=(STAGING_BASE_URL KEYCLOAK_ISSUER_URI KEYCLOAK_ADMIN_BASE_URL KEYCLOAK_REALM KEYCLOAK_ADMIN_CLIENT_ID KEYCLOAK_ADMIN_CLIENT_SECRET KEYCLOAK_SMOKE_CLIENT_ID KEYCLOAK_SMOKE_CLIENT_SECRET KEYCLOAK_API_AUDIENCE)
for name in "${required[@]}"; do
  [[ -n "${!name:-}" ]] || { echo "Missing required environment variable: $name" >&2; exit 2; }
done

for url_name in STAGING_BASE_URL KEYCLOAK_ISSUER_URI KEYCLOAK_ADMIN_BASE_URL; do
  [[ "${!url_name}" == https://* ]] || { echo "$url_name must use HTTPS" >&2; exit 3; }
done

tmp="$(mktemp -d)"
chmod 700 "$tmp"
trap 'rm -rf "$tmp"' EXIT

issuer="${KEYCLOAK_ISSUER_URI%/}"
base="${STAGING_BASE_URL%/}"
admin_base="${KEYCLOAK_ADMIN_BASE_URL%/}"
discovery="$tmp/discovery.json"

curl --fail --silent --show-error --proto '=https' --tlsv1.2 \
  "$issuer/.well-known/openid-configuration" -o "$discovery"
jq -e --arg issuer "$issuer" '.issuer == $issuer and (.jwks_uri | startswith("https://"))' "$discovery" >/dev/null
jwks="$(jq -r '.jwks_uri' "$discovery")"
curl --fail --silent --show-error --proto '=https' --tlsv1.2 "$jwks" | jq -e '.keys | length > 0' >/dev/null
echo "PASS: Keycloak OIDC discovery and JWKS are reachable over verified HTTPS."

curl --fail --silent --show-error --proto '=https' --tlsv1.2 \
  "$base/actuator/health/readiness" | jq -e '.status == "UP"' >/dev/null
echo "PASS: FinanceFlow staging readiness endpoint is healthy over verified HTTPS."

host="$(python3 -c 'import sys,urllib.parse; print(urllib.parse.urlparse(sys.argv[1]).hostname)' "$issuer")"
fetch_client_token() {
  local client_id="$1" client_secret="$2" prefix="$3"
  local netrc="$tmp/$prefix.netrc" response="$tmp/$prefix-token.json"
  printf 'machine %s login %s password %s\n' "$host" "$client_id" "$client_secret" > "$netrc"
  chmod 600 "$netrc"
  curl --fail --silent --show-error --proto '=https' --tlsv1.2 \
    --basic --netrc-file "$netrc" \
    --data-urlencode 'grant_type=client_credentials' \
    "$issuer/protocol/openid-connect/token" -o "$response"
  jq -er '.access_token' "$response"
}

admin_token="$(fetch_client_token "$KEYCLOAK_ADMIN_CLIENT_ID" "$KEYCLOAK_ADMIN_CLIENT_SECRET" admin)"
smoke_token="$(fetch_client_token "$KEYCLOAK_SMOKE_CLIENT_ID" "$KEYCLOAK_SMOKE_CLIENT_SECRET" smoke)"

SMOKE_TOKEN="$smoke_token" EXPECTED_ISSUER="$issuer" EXPECTED_AUDIENCE="$KEYCLOAK_API_AUDIENCE" python3 <<'PY'
import base64, json, os, sys
token = os.environ["SMOKE_TOKEN"]
try:
    payload = token.split(".")[1]
    payload += "=" * (-len(payload) % 4)
    claims = json.loads(base64.urlsafe_b64decode(payload))
except Exception as exc:
    raise SystemExit(f"Could not decode Keycloak smoke token claims: {exc}")
aud = claims.get("aud", [])
if isinstance(aud, str):
    aud = [aud]
if claims.get("iss") != os.environ["EXPECTED_ISSUER"]:
    raise SystemExit("Keycloak smoke token issuer does not match the configured issuer")
if os.environ["EXPECTED_AUDIENCE"] not in aud:
    raise SystemExit("Keycloak smoke token is missing KEYCLOAK_API_AUDIENCE")
PY
echo "PASS: dedicated Keycloak smoke client receives the expected issuer and API audience."

printf 'header = "Authorization: Bearer %s"\n' "$admin_token" > "$tmp/admin.curl"
chmod 600 "$tmp/admin.curl"
curl --fail --silent --show-error --proto '=https' --tlsv1.2 \
  --config "$tmp/admin.curl" \
  "$admin_base/admin/realms/$KEYCLOAK_REALM/users?max=1" -o "$tmp/users.json"
jq -e 'type == "array"' "$tmp/users.json" >/dev/null
echo "PASS: least-privilege Keycloak admin service account can access the realm user API."

if [[ -n "${KEYCLOAK_TEST_USER_ID:-}" ]]; then
  user_url="$admin_base/admin/realms/$KEYCLOAK_REALM/users/$KEYCLOAK_TEST_USER_ID"
  curl --fail --silent --show-error --proto '=https' --tlsv1.2 \
    --config "$tmp/admin.curl" "$user_url" -o "$tmp/test-user.json"
  username="$(jq -r '.username // empty' "$tmp/test-user.json")"
  [[ "$username" == financeflow-it-* ]] || {
    echo "Refusing deletion: test username must start with financeflow-it-" >&2
    exit 4
  }
  if [[ "${KEYCLOAK_DELETE_TEST_USER:-false}" == "true" ]]; then
    curl --fail --silent --show-error --proto '=https' --tlsv1.2 \
      --config "$tmp/admin.curl" -X DELETE "$user_url"
    status="$(curl --silent --output /dev/null --write-out '%{http_code}' \
      --proto '=https' --tlsv1.2 --config "$tmp/admin.curl" "$user_url")"
    [[ "$status" == "404" ]] || { echo "Disposable Keycloak test user still exists (HTTP $status)" >&2; exit 5; }
    echo "PASS: explicitly designated disposable Keycloak test user was deleted and verified absent."
  else
    echo "INFO: disposable Keycloak test user found; deletion skipped (set KEYCLOAK_DELETE_TEST_USER=true to opt in)."
  fi
fi

unset admin_token smoke_token
echo "PASS: Keycloak staging smoke checks completed. Interactive authorization-code, refresh, CSRF and logout flows still require browser E2E evidence."
