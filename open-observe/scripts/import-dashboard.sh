#!/usr/bin/env bash
# Imports dashboards/observability.json into OpenObserve.
#
# Usage:
#   ./scripts/import-dashboard.sh
#   ./scripts/import-dashboard.sh other_stream
#   ./scripts/import-dashboard.sh other_stream other_service_timer
#
# The dashboard file carries placeholders instead of hard-coded names, so the same definition
# serves any service exporting these signals. Re-running replaces the dashboard of the same
# title rather than adding a duplicate.
set -euo pipefail

BASE_URL="${OPENOBSERVE_URL:-http://localhost:5080}"
ORG="${OPENOBSERVE_ORG:-default}"
STREAM="${1:-${OPENOBSERVE_STREAM:-springboot_observe}}"
# The two @Observed panels point at one service timer. An app naming its timer differently
# passes its own prefix here; a project without one simply leaves those two panels empty.
SERVICE_METRIC="${2:-${OPENOBSERVE_SERVICE_METRIC:-order_service}}"
TOKEN="${OPENOBSERVE_AUTH_TOKEN:-Basic cm9vdEBleGFtcGxlLmNvbTpDb21wbGV4cGFzcyMxMjM=}"
FILE="$(dirname "$0")/../dashboards/observability.json"

# Every python call below reconfigures stdout to LF. On Windows it defaults to CRLF, and a
# stray CR ends up inside the URLs built from this output, which curl rejects as malformed.
read_title() {
  python -c "
import json, sys
sys.stdout.reconfigure(newline='\n')
print(json.load(open(sys.argv[1]))['title'])
" "$FILE"
}

existing_ids() {
  curl -s -H "Authorization: $TOKEN" "$BASE_URL/api/$ORG/dashboards" | python -c "
import json, sys
sys.stdout.reconfigure(newline='\n')
title = sys.argv[1]
for d in json.load(sys.stdin).get('dashboards', []):
    body = next((d[k] for k in ('v1','v2','v3','v4','v5','v6','v7','v8') if d.get(k)), {})
    if body.get('title') == title:
        print(body.get('dashboardId'))
" "$1"
}

if ! curl -s -o /dev/null --max-time 5 "$BASE_URL/healthz"; then
  echo "OpenObserve is not answering at $BASE_URL" >&2
  exit 1
fi

TITLE="$(read_title)"

while read -r id; do
  [ -z "$id" ] && continue
  curl -s -o /dev/null -X DELETE -H "Authorization: $TOKEN" \
    "$BASE_URL/api/$ORG/dashboards/$id"
  echo "  replaced existing dashboard $id"
done < <(existing_ids "$TITLE")

# __BASE_URL__ and __ORG__ only feed the "Open trace" drilldown links on the tables.
CODE=$(sed -e "s/__STREAM__/$STREAM/g" -e "s/__SERVICE_METRIC__/$SERVICE_METRIC/g" \
           -e "s|__BASE_URL__|$BASE_URL|g" -e "s/__ORG__/$ORG/g" "$FILE" \
  | curl -s -o /tmp/oo-dashboard-import.json -w '%{http_code}' \
      -X POST -H "Authorization: $TOKEN" -H 'Content-Type: application/json' \
      "$BASE_URL/api/$ORG/dashboards" --data-binary @-)

if [ "$CODE" != "200" ]; then
  echo "Import failed (HTTP $CODE):" >&2
  head -c 400 /tmp/oo-dashboard-import.json >&2
  exit 1
fi

if grep -q '__STREAM__\|__SERVICE_METRIC__\|__BASE_URL__\|__ORG__' /tmp/oo-dashboard-import.json; then
  echo "A placeholder survived substitution - the dashboard will query a literal name." >&2
  exit 1
fi

echo "Imported '$TITLE' for stream '$STREAM', service metric '$SERVICE_METRIC'"
echo "  $BASE_URL/web/dashboards"
