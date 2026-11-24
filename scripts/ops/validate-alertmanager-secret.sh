#!/usr/bin/env bash
set -Eeuo pipefail
set +x

: "${ALERTMANAGER_WEBHOOK_URL_FILE:?Set ALERTMANAGER_WEBHOOK_URL_FILE to the secret file used by Alertmanager}"
[[ -f "$ALERTMANAGER_WEBHOOK_URL_FILE" ]] || { echo "Alertmanager webhook secret file not found" >&2; exit 2; }
mode="$(stat -c '%a' "$ALERTMANAGER_WEBHOOK_URL_FILE")"
[[ "$mode" == "600" || "$mode" == "400" ]] || {
  echo "Alertmanager webhook secret file must have mode 600 or 400" >&2
  exit 3
}
url="$(tr -d '\r\n' < "$ALERTMANAGER_WEBHOOK_URL_FILE")"
[[ -n "$url" ]] || { echo "Alertmanager webhook secret file is empty" >&2; exit 4; }
URL_TO_VALIDATE="$url" python3 <<'PY'
import os
from urllib.parse import urlsplit
url = os.environ["URL_TO_VALIDATE"]
parsed = urlsplit(url)
if parsed.scheme != "https" or not parsed.netloc or any(ch.isspace() for ch in url):
    raise SystemExit("Alertmanager receiver must be a single HTTPS URL without whitespace")
PY
unset url URL_TO_VALIDATE
echo "PASS: Alertmanager receiver file exists, is private, and contains an HTTPS URL."
