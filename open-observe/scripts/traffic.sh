#!/usr/bin/env bash
# Generates telemetry to look at in OpenObserve.
#
# Usage:
#   ./scripts/traffic.sh            # 20 rounds
#   ./scripts/traffic.sh 100        # 100 rounds
#   BASE_URL=http://host:8080 ./scripts/traffic.sh
#
# Prerequisites: docker compose up -d, and the application running on :8080.
set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
ROUNDS="${1:-20}"

hit() {
  local method="$1" path="$2" body="${3:-}"
  local code
  if [ -n "$body" ]; then
    code=$(curl -s -o /dev/null -w '%{http_code}' -X "$method" "$BASE_URL$path" \
      -H 'Content-Type: application/json' -d "$body")
  else
    code=$(curl -s -o /dev/null -w '%{http_code}' -X "$method" "$BASE_URL$path")
  fi
  printf '  %-4s %-28s -> %s\n' "$method" "$path" "$code"
}

if ! curl -s -o /dev/null --max-time 5 "$BASE_URL/actuator/health"; then
  echo "No response from $BASE_URL. Start the application first." >&2
  exit 1
fi

echo "Generating traffic against $BASE_URL for $ROUNDS rounds"
for i in $(seq 1 "$ROUNDS"); do
  echo "round $i"
  hit POST /api/orders "{\"customer\":\"customer-$i\",\"amount\":$((10 + i)).50,\"password\":\"hunter2\"}"
  hit POST /api/orders '{"customer":"","amount":-1}'
  hit GET  /api/orders
  hit GET  /api/orders/1
  hit GET  /api/orders/999999
  hit GET  '/api/orders/slow?millis=1200'
  hit GET  /api/orders/fail
done

cat <<'EOT'

Done. Open http://localhost:5080 and look at, in order:
  Traces  -> /api/orders/slow: server span + @Observed + JDBC spans
  Metrics -> http_server_requests_bucket, order_service_count
  Logs    -> rows with trace_id populated; the password field is masked in Logbook dumps
EOT
