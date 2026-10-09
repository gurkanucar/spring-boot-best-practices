# Spring Boot: Sign-in with Casdoor (OAuth2 / OIDC client)

[Casdoor](https://casdoor.org) is an open-source identity provider. This app lets users sign in with
Casdoor as a Spring Security **OAuth2 client** (OIDC authorization code flow): Casdoor handles
passwords and sign-up; the app gets a verified identity, the user's Casdoor roles, and keeps its own
record of the user. Same structure as `logto-basic-security`.

Spring Boot 4.1.1, Spring Security 7.1, Java 25, PostgreSQL 17.

## Run

```bash
docker compose up -d        # PostgreSQL + Casdoor; Casdoor seeds docker/casdoor/init_data.json
./mvnw spring-boot:run      # port 8103; Casdoor must already be running
```

| | URL |
|---|---|
| Casdoor (console + OIDC) | http://localhost:8000: console login `admin` / `123` (built-in organization) |
| OIDC discovery | http://localhost:8000/.well-known/openid-configuration (issuer `http://localhost:8000`) |
| PostgreSQL | `localhost:5437`: database `app` (user `app` / `app`) for the application, `casdoor` for Casdoor |
| This app | http://localhost:8103 |

No manual setup: `docker/casdoor/init_data.json` creates the organization `demo`, the application
`spring-boot` (client id/secret = the defaults in `application.yaml`, redirect URIs for login and
sign-out), the roles `admin` and `user`, and two users:

| User | Password | Role |
|---|---|---|
| `alice` | `alice123` | `admin` |
| `bob` | `bob123` | `user` |

`initDataNewOnly: true` creates only what is missing, so console changes survive restarts.
`docker compose down -v` deletes everything.

## Try it

| URL | |
|---|---|
| http://localhost:8103/ | public |
| http://localhost:8103/me | redirects to Casdoor when not signed in; then the claims, authorities and the local user |
| http://localhost:8103/admin | role `admin` only (URL rule) |
| http://localhost:8103/user | role `user`, or `admin` (`@PreAuthorize` + `RoleHierarchy`) |
| http://localhost:8103/reports | `admin` or `user` (`@PreAuthorize` on `ReportService`) |
| `POST` http://localhost:8103/reports/generate | runs `@Async` as the calling user |
| `/logout` | signs out here and at Casdoor |

## The signed-in user

`AuthUser` (an `OidcUser` subclass built by `CasdoorOidcUserService`) is the principal, already
parsed: `getId()`, `getEmail()`, `getDisplayName()`, `getRoles()`, raw claims in `getClaims()`.

```java
public Map<String, Object> admin(@CurrentUser AuthUser user) { ... }   // controllers

String id = currentUserService.id();                                    // services (SecurityContextHolder)
UserProfile profile = authService.profile(); // user + the local app_user row (/me)
```

## Data that belongs to the application (`nickname`, `theme`)

Casdoor own identity; `app_user` is where the application keeps what is *its own* about a user.
Two example columns, chosen by the user here and unknown to the provider:

| | |
|---|---|
| `PUT /me/preferences` | JSON `{"nickname": "Ally", "theme": "dark"}`; CSRF token needed (browser session) |
| `GET /me` | `localUser.nickname` / `localUser.theme` |

- `theme` must be `light` or `dark`, `nickname` at most 50 characters (`AppUserService.updatePreferences`).
- Sign-ins and webhooks only refresh e-mail and name, so they never overwrite these two.
- `ddl-auto: update` adds the new nullable columns to an existing `app_user`; a real application
  uses Flyway/Liquibase for that.

## Differences from the Logto module

| | Logto | Casdoor |
|---|---|---|
| Issuer | `http://localhost:3001/oidc` | `http://localhost:8000` (no path) |
| ID token signature | ES384: needs `idTokenDecoderFactory` | RS256 (Spring's default): no decoder bean |
| Roles | `roles` scope, claim is a list of names | no scope needed; claim `roles` is a list of **role objects** in the default `JWT` token format (confirmed) (`[{"owner":"demo","name":"admin",...}]`). `CasdoorOidcUserService.rolesOf` returns the names and accepts objects and plain names (custom token format) |
| Roles are read from | userinfo + ID token merged | the ID token only (userinfo may carry a differently shaped `roles`) |
| Setup | console + `setup.sh` (Management API) | `init_data.json`, loaded at start |
| Webhooks, `/api/**` bearer API | included | not ported |

## Pitfalls

- **Casdoor must be up before the app starts** (discovery request at startup).
- **The issuer comes from `origin`** in `docker-compose.yml`; Spring compares it with `issuer-uri`.
- **Roles are read at sign-in**: a role assigned later takes effect at the next sign-in.
- **Sign-out** needs `http://localhost:8103/` in the application's redirect URIs (it is).
- The seeded client secret and the `plain` password type are for local demos only.

## Status

Checked end to end against Casdoor (`casbin/casdoor:latest`, 2026-10-09) with `docker compose up -d`
and curl: `init_data.json` seeds the organization, application, roles and users; sign-in as `alice`
(`ROLE_admin`: `/admin`, `/user`, `/reports` 200) and `bob` (`ROLE_user`: `/admin` 403, `/user` 200);
the `app_user` row is created; `PUT /me/preferences` saves `nickname`/`theme` (400 on a bad theme);
`@Async` runs for the signed-in user; `POST /logout` redirects to Casdoor's `end_session_endpoint`,
which sends the browser back to `/`, and `/me` then asks for a sign-in again.
Not checked: that Casdoor's own SSO cookie is gone after sign-out (needs a real browser), and other
Casdoor versions. `./mvnw test`: 22 tests, no Casdoor needed.
