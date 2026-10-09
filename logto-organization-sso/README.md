# Logto: several organizations, each with its own SSO

Two fake companies, **Acme** and **Globex**, each with its own identity provider (Dex containers standing
in for Azure AD / Okta). In Logto each company is an **organization** with an **OIDC SSO connector** and
its e-mail domain; whoever signs in through the connector is provisioned into that organization (JIT).

```bash
docker compose up -d            # PostgreSQL, Logto, Dex (Acme), Dex (Globex)
./docker/logto/bootstrap.sh     # a machine-to-machine app for the Management API (once)
./docker/logto/setup.sh         # organizations, SSO connectors, JIT, sign-in experience, the app; writes .env
```

| | |
|---|---|
| Logto sign-in / OIDC | http://localhost:3011 (admin console http://localhost:3012) |
| Acme IdP | `ceo@acme.test`, `staff@acme.test`, password `password123` |
| Globex IdP | `ceo@globex.test`, `staff@globex.test`, password `password123` |

## Try it (browser)

Open an authorize URL for the `spring-boot` application (client id in `.env`), choose **Single Sign-On**,
type `ceo@acme.test`: Logto looks at the domain and sends the browser to Acme's Dex; with
`staff@globex.test` it goes to Globex's. Afterwards the user exists in Logto and belongs to the matching
organization as `member` (checked in the database: one row per organization).

Request the scopes `urn:logto:scope:organizations urn:logto:scope:organization_roles` to get the
user's organizations and roles in the ID token.

## Notes

- Everything here is the open-source Logto 1.43.0: Organizations, SSO connectors (OIDC, SAML, Azure AD,
  Google Workspace, Okta) and JIT provisioning were available without a license. Paid self-hosted plans
  add things like IdP-initiated SSO; check the current plan list before relying on those.
- `*.localhost` resolves to 127.0.0.1 in browsers; Logto's container reaches the same names through
  `extra_hosts: host-gateway`, and `SSRF_ALLOWED_ADDRESSES` lets it call them (Logto blocks private
  addresses otherwise).
- Dex only accepts redirects it knows, and the callback URL contains Logto's generated connector id, so
  `setup.sh` renders `docker/dex/*.yaml` from the `*.tmpl` files and restarts Dex.
- The M2M application is inserted by SQL because a fresh Logto has none and the console would need
  clicking. Demo only.
- Not done yet: the Spring Boot application that reads the organization claims
  (`logto-basic-security` shows the sign-in part).
