#!/usr/bin/env bash
set -Eeuo pipefail
set +x

: "${STRIPE_API_KEY:?Set STRIPE_API_KEY to a Stripe test secret key}"
: "${STRIPE_WEBHOOK_ENDPOINT_ID:?Set the registered Stripe Test Mode webhook endpoint ID}"
: "${STRIPE_WEBHOOK_ENDPOINT_URL:?Set the exact HTTPS staging webhook endpoint URL}"
command -v stripe >/dev/null || { echo "Stripe CLI is required" >&2; exit 2; }
command -v jq >/dev/null

[[ "$STRIPE_API_KEY" == sk_test_* ]] || {
  echo "Refusing to run: STRIPE_API_KEY must begin with sk_test_" >&2
  exit 3
}
[[ "$STRIPE_WEBHOOK_ENDPOINT_URL" == https://* ]] || {
  echo "Refusing to run: webhook endpoint URL must use HTTPS" >&2
  exit 4
}

tmp="$(mktemp -d)"
chmod 700 "$tmp"
trap 'rm -rf "$tmp"' EXIT

export STRIPE_API_KEY
stripe webhook_endpoints retrieve "$STRIPE_WEBHOOK_ENDPOINT_ID" --format json > "$tmp/endpoint.json"
jq -e --arg url "$STRIPE_WEBHOOK_ENDPOINT_URL" '.livemode == false and .url == $url and .status == "enabled"' \
  "$tmp/endpoint.json" >/dev/null || {
    echo "Refusing to trigger events: endpoint must be enabled, Test Mode, and match the exact staging URL." >&2
    exit 5
  }

echo "Confirmed registered webhook endpoint is enabled and in Stripe Test Mode."
for event in payment_intent.succeeded customer.subscription.deleted; do
  stripe trigger "$event" --api-key "$STRIPE_API_KEY" > "$tmp/$event.log"
  echo "Triggered Stripe Test Mode fixture: $event"
done

echo "PASS: Stripe CLI accepted the Test Mode event triggers."
echo "Verify delivery attempts and 2xx responses for this endpoint in Stripe Workbench before accepting the integration test."
echo "Then verify idempotency and resulting application state using the staging correlation/event IDs."
