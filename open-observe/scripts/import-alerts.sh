#!/usr/bin/env bash
# Imports alerts/alerts.json into OpenObserve: one email template, one destination, four alerts.
#
# Usage:
#   ./scripts/import-alerts.sh
#   ./scripts/import-alerts.sh other_stream
#   ./scripts/import-alerts.sh other_stream oncall@example.com
#
# Same idea as import-dashboard.sh: the file carries placeholders, not names, and re-running
# replaces what is already there instead of erroring on the name conflict.
set -euo pipefail

BASE_URL="${OPENOBSERVE_URL:-http://localhost:5080}"
ORG="${OPENOBSERVE_ORG:-default}"
STREAM="${1:-${OPENOBSERVE_STREAM:-springboot_observe}}"
ALERT_EMAIL="${2:-${OPENOBSERVE_ALERT_EMAIL:-root@example.com}}"
TOKEN="${OPENOBSERVE_AUTH_TOKEN:-Basic cm9vdEBleGFtcGxlLmNvbTpDb21wbGV4cGFzcyMxMjM=}"
FILE="$(dirname "$0")/../alerts/alerts.json"

if ! curl -s -o /dev/null --max-time 5 "$BASE_URL/healthz"; then
  echo "OpenObserve is not answering at $BASE_URL" >&2
  exit 1
fi

# Substitution happens once, into a temp file the python steps below read back.
RENDERED="$(mktemp)"
trap 'rm -f "$RENDERED"' EXIT
sed -e "s/__STREAM__/$STREAM/g" -e "s/__ALERT_EMAIL__/$ALERT_EMAIL/g" "$FILE" > "$RENDERED"

if grep -q '__STREAM__\|__ALERT_EMAIL__' "$RENDERED"; then
  echo "A placeholder survived substitution." >&2
  exit 1
fi

BASE_URL="$BASE_URL" ORG="$ORG" TOKEN="$TOKEN" RENDERED="$RENDERED" python - <<'PY'
import json, os, sys, urllib.error, urllib.request

BASE, ORG, TOKEN = os.environ["BASE_URL"], os.environ["ORG"], os.environ["TOKEN"]
doc = json.load(open(os.environ["RENDERED"], encoding="utf-8"))


def call(method, path, body=None):
    req = urllib.request.Request(
        BASE + path,
        data=json.dumps(body).encode() if body is not None else None,
        method=method,
    )
    req.add_header("Authorization", TOKEN)
    req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def upsert(kind, name, payload):
    # POST creates, PUT updates; there is no upsert, so try the create and fall back.
    status, body = call("POST", f"/api/{ORG}/alerts/{kind}", payload)
    if status != 200:
        status, body = call("PUT", f"/api/{ORG}/alerts/{kind}/{name}", payload)
    if status != 200:
        sys.exit(f"{kind} {name} failed ({status}): {body[:200]}")
    return status


upsert("templates", doc["template"]["name"], doc["template"])
upsert("destinations", doc["destination"]["name"], doc["destination"])

_, listing = call("GET", f"/api/v2/{ORG}/alerts")
existing = {a["name"]: a["alert_id"] for a in json.loads(listing).get("list", [])}

for alert in doc["alerts"]:
    if alert["name"] in existing:
        call("DELETE", f"/api/v2/{ORG}/alerts/{existing[alert['name']]}")
    status, body = call("POST", f"/api/v2/{ORG}/alerts", alert)
    if status != 200:
        sys.exit(f"alert {alert['name']} failed ({status}): {body[:300]}")
    kind = "realtime " if alert["is_real_time"] else "scheduled"
    print(f"  {kind} {alert['name']}")
PY

echo "Imported alerts for stream '$STREAM', mailing '$ALERT_EMAIL'"
echo "  $BASE_URL/web/alerts"
