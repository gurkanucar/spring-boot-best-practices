#!/usr/bin/env bash
# Configures the local Logto through its Management API. Safe to run again: it reuses what exists.
#
#   - organization roles: admin, member
#   - per company (acme, globex): an organization, an OIDC SSO connector to the company's own IdP
#     (a Dex container here), the company's e-mail domain on the connector, and just-in-time
#     provisioning: whoever signs in through that connector joins the organization as "member"
#   - sign-in experience: single sign-on enabled, e-mail identifier
#   - a web application for the Spring Boot app, with the organization scopes
#
# Needs: Logto and both Dex containers running (docker compose up -d), curl, python3, and the
# machine-to-machine application created by ./docker/logto/bootstrap.sh.
set -euo pipefail
cd "$(dirname "$0")/../.."

LOGTO=${LOGTO_ENDPOINT:-http://localhost:3011}
M2M_ID=${M2M_ID:-org-sso-setup}
M2M_SECRET=${M2M_SECRET:-org-sso-setup-secret}

token=$(curl -sf -u "$M2M_ID:$M2M_SECRET" -d grant_type=client_credentials \
  -d resource=https://default.logto.app/api -d scope=all "$LOGTO/oidc/token" \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["access_token"])')

api() { # method path [json]
  local out code
  out=$(curl -s -w '\n%{http_code}' -X "$1" "$LOGTO$2" -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" ${3:+-d "$3"})
  code=${out##*$'\n'}
  if [ "$code" -ge 400 ]; then echo "FAILED $1 $2 -> $code: ${out%$'\n'*}" >&2; return 1; fi
  printf '%s' "${out%$'\n'*}"
}
jget() { python3 -c "import sys,json; d=json.load(sys.stdin); print($1)"; }
find_id() { # path name -> id of the item whose "name" matches, or empty
  api GET "$1" | jget "next((x['id'] for x in d if (x.get('connectorName') or x.get('name'))=='$2'), '')"
}

# --- organization roles ----------------------------------------------------------------------------
for role in admin member; do
  id=$(find_id /api/organization-roles "$role")
  [ -n "$id" ] || api POST /api/organization-roles "{\"name\":\"$role\",\"description\":\"$role of an organization\"}" > /dev/null
done
member_role=$(find_id /api/organization-roles member)

# --- companies --------------------------------------------------------------------------------------
setup_company() { # key Name dex-port
  local key=$1 name=$2 port=$3 domain="$1.test"
  local issuer="http://$key.localhost:$port/dex"

  org=$(find_id /api/organizations "$name")
  [ -n "$org" ] || org=$(api POST /api/organizations "{\"name\":\"$name\",\"description\":\"$name company\"}" | jget "d['id']")

  conn=$(find_id /api/sso-connectors "$name SSO")
  if [ -z "$conn" ]; then
    conn=$(api POST /api/sso-connectors "{
      \"providerName\": \"OIDC\", \"connectorName\": \"$name SSO\", \"domains\": [\"$domain\"],
      \"config\": {\"clientId\": \"logto\", \"clientSecret\": \"$key-secret\", \"issuer\": \"$issuer\",
                   \"scope\": \"openid profile email\"}
    }" | jget "d['id']")
  fi

  # Dex only accepts redirects it knows: Logto's callback for this connector.
  sed "s#@CONNECTOR_ID@#$conn#" "docker/dex/$key.yaml.tmpl" > "docker/dex/$key.yaml"

  api PUT "/api/organizations/$org/jit/sso-connectors" "{\"ssoConnectorIds\":[\"$conn\"]}" > /dev/null
  api PUT "/api/organizations/$org/jit/roles" "{\"organizationRoleIds\":[\"$member_role\"]}" > /dev/null
  echo "$name: organization $org, SSO connector $conn (@$domain -> $issuer)"
}
setup_company acme Acme 5556
setup_company globex Globex 5557

# --- sign-in experience -----------------------------------------------------------------------------
# E-mail is the identifier: after typing it, Logto looks at the domain and hands over to the company's IdP.
api PATCH /api/sign-in-exp '{
  "singleSignOnEnabled": true,
  "signUp": {"identifiers": [], "password": false, "verify": false},
  "signIn": {"methods": [{"identifier": "email", "password": true, "verificationCode": false, "isPasswordPrimary": true}]}
}' > /dev/null && echo "sign-in experience: single sign-on enabled"

# --- the Spring Boot application --------------------------------------------------------------------
app=$(find_id /api/applications spring-boot)
if [ -z "$app" ]; then
  app=$(api POST /api/applications '{
    "name": "spring-boot", "type": "Traditional",
    "oidcClientMetadata": {
      "redirectUris": ["http://localhost:8104/login/oauth2/code/logto"],
      "postLogoutRedirectUris": ["http://localhost:8104/"]
    }
  }' | jget "d['id']")
fi
secret=$(api GET "/api/applications/$app/secrets" | jget "d[0]['value']")
echo "application spring-boot: LOGTO_CLIENT_ID=$app LOGTO_CLIENT_SECRET=$secret"
printf 'LOGTO_CLIENT_ID=%s\nLOGTO_CLIENT_SECRET=%s\n' "$app" "$secret" > .env

docker compose restart dex-acme dex-globex > /dev/null
echo "Dex restarted with the connector callbacks."
