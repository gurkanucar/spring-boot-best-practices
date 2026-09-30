#!/usr/bin/env bash
# Configures the local Logto through its Management API. Safe to run again: it updates what exists.
#
#   - SMTP e-mail connector -> the Mailpit container (mailpit:1025), code templates for every usage
#   - Sign-in experience: sign up with e-mail + password (e-mail verified by code),
#     sign in with e-mail or username + password, forgot password by e-mail code
#
# Needs: Logto running (docker compose up -d), curl, and in ../../.env a machine-to-machine app with
# the "Logto Management API access" role: LOGTO_M2M_CLIENT_ID / LOGTO_M2M_CLIENT_SECRET.
#
# Usage (from the module directory): ./docker/logto/setup.sh
set -euo pipefail

cd "$(dirname "$0")/../.."
set -a
# shellcheck disable=SC1091
. ./.env
set +a

LOGTO=${LOGTO_ENDPOINT:-http://localhost:3001}
: "${LOGTO_M2M_CLIENT_ID:?set LOGTO_M2M_CLIENT_ID in .env}"
: "${LOGTO_M2M_CLIENT_SECRET:?set LOGTO_M2M_CLIENT_SECRET in .env}"

# Management API token (client credentials). The resource indicator of the default tenant's
# Management API in a self-hosted Logto is https://default.logto.app/api.
token=$(curl -sf -u "$LOGTO_M2M_CLIENT_ID:$LOGTO_M2M_CLIENT_SECRET" \
  -d grant_type=client_credentials -d resource=https://default.logto.app/api -d scope=all \
  "$LOGTO/oidc/token" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
[ -n "$token" ] || { echo "Could not get a Management API token: check the M2M app and its role" >&2; exit 1; }

api() { # method path [json]
  curl -sf -X "$1" "$LOGTO$2" -H "Authorization: Bearer $token" -H "Content-Type: application/json" ${3:+-d "$3"}
}

# --- SMTP connector -------------------------------------------------------------------------------
# Mailpit accepts any credentials (MP_SMTP_AUTH_ACCEPT_ANY); the connector requires some.
smtp_config='{
  "host": "mailpit",
  "port": 1025,
  "auth": {"type": "login", "user": "logto", "pass": "logto"},
  "fromEmail": "Logto <no-reply@example.com>",
  "templates": [
    {"usageType": "Register",       "contentType": "text/plain", "subject": "Your sign-up code",
     "content": "Your verification code is {{code}}. It expires in 10 minutes."},
    {"usageType": "SignIn",         "contentType": "text/plain", "subject": "Your sign-in code",
     "content": "Your sign-in code is {{code}}. It expires in 10 minutes."},
    {"usageType": "ForgotPassword", "contentType": "text/plain", "subject": "Reset your password",
     "content": "Your password reset code is {{code}}. If you did not ask for it, ignore this mail."},
    {"usageType": "Generic",        "contentType": "text/plain", "subject": "Your verification code",
     "content": "Your verification code is {{code}}."}
  ]
}'

# Find the SMTP connector if it is already there (target=smtp); "id" is the first key of each item.
connector_id=$(api GET "/api/connectors?target=smtp" | sed -n 's/^\[{"id":"\([^"]*\)".*/\1/p')

if [ -n "$connector_id" ]; then
  api PATCH "/api/connectors/$connector_id" "{\"config\": $smtp_config}" > /dev/null
  echo "SMTP connector updated ($connector_id)"
else
  api POST /api/connectors "{\"connectorId\": \"simple-mail-transfer-protocol\", \"config\": $smtp_config}" > /dev/null
  echo "SMTP connector created"
fi

# --- Sign-in experience ---------------------------------------------------------------------------
api PATCH /api/sign-in-exp '{
  "signUp": {"identifiers": ["email"], "password": true, "verify": true},
  "signIn": {"methods": [
    {"identifier": "email",    "password": true, "verificationCode": false, "isPasswordPrimary": true},
    {"identifier": "username", "password": true, "verificationCode": false, "isPasswordPrimary": true}
  ]},
  "forgotPasswordMethods": ["EmailVerificationCode"]
}' > /dev/null
echo "Sign-in experience updated: sign up with e-mail, sign in with e-mail or username, reset by e-mail"

# --- API resource for /api/** (ApiSecurityConfig) --------------------------------------------------
# Matching items in JSON lists needs jq; without a local one, the official image is used.
if command -v jq > /dev/null 2>&1; then JQ=(jq); else JQ=(docker run --rm -i ghcr.io/jqlang/jq:1.7.1); fi
jq() { "${JQ[@]}" "$@"; }

API_INDICATOR=${API_INDICATOR:-http://localhost:8101/api}   # = app.api.audience in application.yaml

resource_id=$(api GET /api/resources | jq -r --arg i "$API_INDICATOR" '.[] | select(.indicator == $i) | .id')
if [ -z "$resource_id" ]; then
  resource_id=$(api POST /api/resources "$(jq -n --arg i "$API_INDICATOR" '{name: "Spring Boot demo API", indicator: $i}')" | jq -r .id)
  echo "API resource created: $API_INDICATOR"
fi

scope_id() { # name description -> id (creates the permission when missing)
  local id
  id=$(api GET "/api/resources/$resource_id/scopes" | jq -r --arg n "$1" '.[] | select(.name == $n) | .id')
  if [ -z "$id" ]; then
    id=$(api POST "/api/resources/$resource_id/scopes" "$(jq -n --arg n "$1" --arg d "$2" '{name: $n, description: $d}')" | jq -r .id)
  fi
  echo "$id"
}
read_scope=$(scope_id read:reports "Read reports")
write_scope=$(scope_id write:reports "Create reports")

ensure_role() { # name type description scopeId... -> id; grants the scopes it does not have yet
  local name=$1 type=$2 description=$3 id missing
  shift 3
  id=$(api GET /api/roles | jq -r --arg n "$name" '.[] | select(.name == $n) | .id')
  if [ -z "$id" ]; then
    id=$(api POST /api/roles "$(jq -n --arg n "$name" --arg t "$type" --arg d "$description" \
      '{name: $n, type: $t, description: $d}')" | jq -r .id)
  fi
  missing=$(api GET "/api/roles/$id/scopes" | jq -c --args '[$ARGS.positional[]] - [.[].id]' "$@")
  if [ "$missing" != "[]" ]; then
    api POST "/api/roles/$id/scopes" "{\"scopeIds\": $missing}" > /dev/null
  fi
  echo "$id"
}
# Users: "user" may read, "admin" may read and write (tokens a Flutter app or SPA gets for a user).
user_role=$(ensure_role user User "Reads reports" "$read_scope")
admin_role=$(ensure_role admin User "Reads and creates reports" "$read_scope" "$write_scope")
# Every new sign-up gets "user" (Logto default role); users created earlier keep what they have.
api PATCH "/api/roles/$user_role" '{"isDefault": true}' > /dev/null
# The demo machine client may only read: the same token gets 200 for GET and 403 for POST.
client_role=$(ensure_role api-client MachineToMachine "Demo M2M client: reads reports" "$read_scope")
echo "Roles: user (read, default for new users), admin (read+write), api-client (read, machine-to-machine)"

# Optional: LOGTO_ADMIN_EMAIL=you@example.com ./docker/logto/setup.sh gives that user the admin role.
if [ -n "${LOGTO_ADMIN_EMAIL:-}" ]; then
  admin_user=$(api GET "/api/users?search=$LOGTO_ADMIN_EMAIL" \
    | jq -r --arg e "$LOGTO_ADMIN_EMAIL" '.[] | select(.primaryEmail == $e) | .id')
  if [ -z "$admin_user" ]; then
    echo "LOGTO_ADMIN_EMAIL: no user with e-mail $LOGTO_ADMIN_EMAIL (sign up first)" >&2
  elif ! api GET "/api/users/$admin_user/roles" | jq -e --arg r "$admin_role" 'any(.[]; .id == $r)' > /dev/null; then
    api POST "/api/users/$admin_user/roles" "{\"roleIds\": [\"$admin_role\"]}" > /dev/null
    echo "Assigned admin to $LOGTO_ADMIN_EMAIL (effective in the next token)"
  fi
fi

# --- Machine-to-machine demo client used by http/api.http -----------------------------------------
app_id=$(api GET "/api/applications?types=MachineToMachine" | jq -r '.[] | select(.name == "api-demo-client") | .id')
if [ -z "$app_id" ]; then
  app_id=$(api POST /api/applications '{"name": "api-demo-client", "type": "MachineToMachine"}' | jq -r .id)
  echo "Application created: api-demo-client ($app_id)"
fi
if ! api GET "/api/applications/$app_id/roles" | jq -e --arg r "$client_role" 'any(.[]; .id == $r)' > /dev/null; then
  api POST "/api/applications/$app_id/roles" "{\"roleIds\": [\"$client_role\"]}" > /dev/null
fi
app_secret=$(api GET "/api/applications/$app_id/secrets" | jq -r '.[0].value')

# --- Public client for USER tokens from Postman / the IntelliJ HTTP Client -------------------------
# Signs a user in through Logto's page (authorization code + PKCE, no secret), like a Flutter app.
POSTMAN_CALLBACK=https://oauth.pstmn.io/v1/callback
INTELLIJ_CALLBACK=http://localhost:12345/callback
tester_metadata=$(jq -n --arg p "$POSTMAN_CALLBACK" --arg i "$INTELLIJ_CALLBACK" \
  '{redirectUris: [$p, $i], postLogoutRedirectUris: []}')
tester_id=$(api GET /api/applications | jq -r '.[] | select(.name == "api-tester") | .id')
if [ -z "$tester_id" ]; then
  tester_id=$(api POST /api/applications "$(jq -n --argjson m "$tester_metadata" \
    '{name: "api-tester", type: "SPA", description: "Postman / IntelliJ HTTP Client: user tokens for /api/**", oidcClientMetadata: $m}')" \
    | jq -r .id)
  echo "Application created: api-tester ($tester_id), redirect URIs: Postman, IntelliJ"
else
  api PATCH "/api/applications/$tester_id" "$(jq -n --argjson m "$tester_metadata" '{oidcClientMetadata: $m}')" > /dev/null
fi

# Everything the IntelliJ HTTP Client needs; git-ignored because of the client secret.
mkdir -p http
jq -n --arg id "$app_id" --arg secret "$app_secret" --arg tester "$tester_id" \
  --arg logto "$LOGTO" --arg resource "$API_INDICATOR" --arg redirect "$INTELLIJ_CALLBACK" '{
  local: {
    clientId: $id,
    clientSecret: $secret,
    testerClientId: $tester,
    Security: {Auth: {"logto-user": {
      "Type": "OAuth2",
      "Grant Type": "Authorization Code",
      "Auth URL": ($logto + "/oidc/auth"),
      "Token URL": ($logto + "/oidc/token"),
      "Client ID": $tester,
      "Redirect URL": $redirect,
      "PKCE": true,
      "Scope": "openid profile read:reports write:reports",
      "Custom Request Parameters": {"resource": {"Value": $resource, "Use": "Everywhere"}}
    }}}
  }}' > http/http-client.private.env.json
echo "Wrote http/http-client.private.env.json (api-demo-client credentials, api-tester OAuth2 config)"
echo "Postman: OAuth 2.0 / Authorization Code (With PKCE), Client ID $tester_id, resource $API_INDICATOR"
