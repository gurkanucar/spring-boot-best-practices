#!/usr/bin/env bash
# Creates the machine-to-machine application that setup.sh uses for Logto's Management API, straight in
# the database: a fresh Logto has no application yet and creating the first one needs the admin console.
# Local demo only. Run once after `docker compose up -d` (Logto must have finished its seed).
set -euo pipefail
cd "$(dirname "$0")/../.."
docker compose exec -T postgres psql -U postgres -d logto -v ON_ERROR_STOP=1 <<'SQL'
insert into applications (tenant_id, id, name, secret, description, type, oidc_client_metadata)
values ('default', 'org-sso-setup', 'org-sso-setup', 'org-sso-setup-secret', 'Local setup script (Management API)',
        'MachineToMachine', '{"redirectUris":[],"postLogoutRedirectUris":[]}')
on conflict (id) do nothing;
insert into applications_roles (tenant_id, id, application_id, role_id)
select 'default', 'org-sso-setup-role', 'org-sso-setup', id from roles
where tenant_id = 'default' and name = 'Logto Management API access'
on conflict do nothing;
SQL
echo "M2M application org-sso-setup created"
