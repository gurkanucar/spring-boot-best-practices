# Spring Boot: Sign-in with Logto (OAuth2 / OIDC client)

[Logto](https://logto.io) is an open-source identity provider (an Auth0 alternative). This app lets
users sign in with Logto as a Spring Security **OAuth2 client** (OIDC authorization code flow):
Logto handles passwords, sign-up, e-mail verification and social sign-in; the app gets a verified
identity, the user's Logto roles, and keeps its own record of the user, in sync through Logto
webhooks.

Spring Boot 4.1.1, Spring Security 7.1, Java 25, Logto 1.43, PostgreSQL 17.

## Run

```bash
docker compose up -d        # PostgreSQL + Logto; the first start seeds Logto's database (~30 s)
# set up Logto once (below), then:
cp .env.example .env        # LOGTO_CLIENT_ID, LOGTO_CLIENT_SECRET, LOGTO_WEBHOOK_SIGNING_KEY
./mvnw spring-boot:run      # port 8101; Logto must already be running
```

| | URL |
|---|---|
| Logto admin console | http://localhost:3002: **open this one** to set Logto up |
| Logto OIDC endpoint | `http://localhost:3001` (issuer `http://localhost:3001/oidc`): for the app, not a page to open. Opened directly it shows "Session not found" (`/unknown-session`): its sign-in page only works when an application sends the user there |
| PostgreSQL | `localhost:5436`, database `app` (user `app` / `app`) for the application, `logto` for Logto |
| Mailpit | http://localhost:8026: every mail Logto sends (sign-up and password reset codes) |
| This app | http://localhost:8101 |

One PostgreSQL server, two databases: Logto's `logto` (created by its seed, as `postgres`, since the
seed creates roles) and the application's `app` (`docker/postgres/init.sql`, its own user).
`docker compose down -v` deletes both, including the Logto configuration below.

## Set up Logto (once)

1. Open http://localhost:3002 and create the admin account.
2. **Applications → Create application → Traditional web** (a server-side app that can keep a
   client secret), e.g. named `spring-boot`. Picking the **Java Spring Boot** framework creates the
   same type of application.
3. In the application (both lists start empty):
   - **Redirect URI**: `http://localhost:8101/login/oauth2/code/logto`
     (Spring Security's `/login/oauth2/code/{registrationId}`, registration id `logto`)
   - **Post sign-out redirect URI**: `http://localhost:8101/`
   - Save, then copy **App ID** and **App secret** into `.env`:

     ```properties
     LOGTO_CLIENT_ID=<App ID>
     LOGTO_CLIENT_SECRET=<App secret>
     ```

   Logto's Spring Boot guide, shown after creating the application, assumes port 8080 and
   suggests the callback URL as the post sign-out URI: use the values above instead. Its code is
   already in this project in an up-to-date form; do not copy it:

   | The guide | This project |
   |---|---|
   | `authorizeRequests`, `antMatchers` | removed in Spring Security 7: `authorizeHttpRequests`, `requestMatchers` |
   | a logout handler that only ends the app's session | `OidcClientInitiatedLogoutSuccessHandler`: ends Logto's session too, else the next sign-in asks for no password |
   | `authorization-uri`, `jwk-set-uri` next to `issuer-uri` | `issuer-uri` alone: the rest comes from Logto's discovery document |
   | `offline_access` scope (refresh token) | not needed for a session sign-in; `roles` instead, for the role checks |
   | the ES384 `idTokenDecoderFactory` | the same, in `SecurityConfig` |
4. Optional, for the role pages: **Authorization → Roles → Create role** `admin` and `user` (type:
   user role), then **User management → a user → Roles → Assign**.
5. Optional, to keep `app_user` in sync: **Webhooks → Create webhook**
   - **Endpoint URL**: `http://host.docker.internal:8101/webhooks/logto` (Logto runs in a
     container, the app on the host)
   - **Events**: `PostRegister`, `User.Created`, `User.Data.Updated`, `User.Deleted`
   - Create, then copy the **Signing key** into `.env` as `LOGTO_WEBHOOK_SIGNING_KEY` and restart
     the app. "Send test event" in the console checks the connection.

Users can sign up on Logto's sign-in page (username and password by default; change it under
**Sign-in experience**, or run the script below).

### E-mail sign-up and "forgot password" with Mailpit (scripted, optional)

`docker/logto/setup.sh` configures Logto through its **Management API** instead of the console:

- an **SMTP e-mail connector** pointing at the `mailpit` container (`mailpit:1025`), with code
  templates for sign-up, sign-in, password reset and generic verification;
- the **sign-in experience**: sign up with e-mail + password (the e-mail is verified with a code),
  sign in with e-mail or username + password, "Forgot password?" by e-mail code;
- the **API resource**, permissions, roles and the `api-demo-client` for `/api/**` (see below).

Matching items in the Management API's JSON lists uses `jq`; without a local one the script runs
the official `ghcr.io/jqlang/jq` image through Docker.

It needs a machine-to-machine application once (the Management API has no anonymous access):

1. **Applications → Create application → Machine-to-machine**, e.g. `setup-script`, and assign it
   the **Logto Management API access** role.
2. Put its credentials into `.env`: `LOGTO_M2M_CLIENT_ID`, `LOGTO_M2M_CLIENT_SECRET`.
3. `./docker/logto/setup.sh` (safe to run again; it updates the connector instead of adding one).

Then sign up on Logto's page (open http://localhost:8101/me → "Create account"), and read the code
at http://localhost:8026. "Forgot password?" on the sign-in page sends a reset code the same way.
Logto sends **codes**, not links. Mailpit accepts any SMTP login (`MP_SMTP_AUTH_ACCEPT_ANY`), because
Logto's SMTP connector always sends credentials.

## Mobile (Flutter) and SPA clients, and "my own login screen"

Logto does **not** let an app collect the password in its own form and post it to an API: the
password grant (ROPC) is deprecated in OAuth 2.1 and Logto does not offer it. The password only ever
goes to Logto. The options:

| Option | How | Own UI? |
|---|---|---|
| Logto SDK + browser (recommended) | Flutter: `logto_dart_sdk` `signIn()` opens an in-app browser (ASWebAuthenticationSession / Custom Tabs) on Logto's page and returns through a redirect URI; SPAs redirect the same way (authorization code + PKCE) | Logto's page, branded in the console (logo, colours, custom CSS, languages) |
| Bring your own UI + Experience API | You write the sign-in / sign-up / reset pages; they call Logto's Experience API. They still run inside the OIDC flow, served by Logto, in a browser | Fully yours, but a web page, not native widgets |
| Fully native form | Not possible with Logto; the backend would have to take the password itself (as `jwt-auth-refresh-token-roles` does) | Yes, without Logto |

Either way the client ends up with an **access token** and calls the Spring Boot API with
`Authorization: Bearer ...`. This module has that API too, next to the browser sign-in:

| | Browser pages (`SecurityConfig`) | `/api/**` (`ApiSecurityConfig`) |
|---|---|---|
| Spring Security role | OAuth2 **client** (`oauth2Login`) | OAuth2 **resource server** (bearer JWT) |
| Not signed in | 302 to Logto | 401 + `WWW-Authenticate: Bearer` |
| State | session cookie, CSRF | stateless, no CSRF |
| Checks | roles (`ROLE_admin`, `ROLE_user`) | permissions (`SCOPE_read:reports`, `SCOPE_write:reports`) |

- `GET /api/whoami` (any valid token), `GET /api/reports` (`read:reports`), `POST /api/reports`
  (`write:reports`).
- A token is accepted only if it is an **RFC 9068 access token** (`typ: at+jwt`, which is what Logto
  issues; Spring Security's default validator only accepts `JWT` and would reject every Logto access
  token), from `app.api.issuer`, with `app.api.audience` (the API resource indicator) in `aud`.
  Tokens for the Management API or another service, and ID tokens, are refused.
- Access tokens carry **permissions**, not role names: in Logto a role grants permissions
  (`user` → `read:reports`, `admin` → both), and the API only asks for the permission.
- `docker/logto/setup.sh` creates the API resource `http://localhost:8101/api`, its two permissions,
  the roles, and a machine-to-machine client **`api-demo-client`** (role `api-client`: read only),
  and writes its credentials to `http/http-client.private.env.json` (git-ignored).
- **`http/api.http`** (IntelliJ HTTP Client, environment `local`): token by client credentials →
  `whoami` → `GET` 200 → `POST` 403 (read-only client) → no token 401 → a Management API token 401
  → a tampered token 401.

A Flutter app gets a token for a **user** instead (authorization code + PKCE through the Logto SDK,
with `resource: http://localhost:8101/api`); the API checks it the same way, with the permissions
coming from the user's roles.

### Trying it as a user (Postman or IntelliJ)

The token the browser sign-in of this app obtains cannot be reused: it stays on the server (the
browser only gets a session cookie), and it is an opaque token for Logto's userinfo, not for this API
(no `resource`). A client that wants a token for `/api/**` signs the user in itself. `setup.sh` creates
a public client for that, **`api-tester`** (SPA type: no secret, PKCE), with the Postman and IntelliJ
callbacks as redirect URIs. New sign-ups get the `user` role (Logto default role);
`LOGTO_ADMIN_EMAIL=you@example.com ./docker/logto/setup.sh` makes an existing user an `admin`.

- **IntelliJ**: requests 8-10 in `http/api.http` use `{{$auth.token("logto-user")}}`; the OAuth2
  configuration is in the generated `http-client.private.env.json`.
- **Postman**: Authorization → OAuth 2.0 → Get New Access Token

  | Field | Value |
  |---|---|
  | Grant type | Authorization Code (With PKCE) |
  | Callback URL | `https://oauth.pstmn.io/v1/callback` ("Authorize using browser") |
  | Auth URL / Access Token URL | `http://localhost:3001/oidc/auth` / `http://localhost:3001/oidc/token` |
  | Client ID | printed by `setup.sh` (`api-tester`); Client Secret empty |
  | Scope | `openid profile read:reports write:reports` |
  | Advanced → Resource | `http://localhost:8101/api` (auth **and** token request) |

  Without the resource Logto issues an opaque token for its own userinfo endpoint, which this API
  answers with 401. Logto only grants the requested permissions the user's roles allow: a `user`
  gets `read:reports` (POST → 403), an `admin` gets both.

## Try it

| URL | |
|---|---|
| http://localhost:8101/ | public: signed in or not, and the links below |
| http://localhost:8101/me | redirects to Logto when not signed in; afterwards the claims Logto sent, the authorities and the local user |
| http://localhost:8101/admin | only with the Logto role `admin`, otherwise 403 (URL rule) |
| http://localhost:8101/user | with the Logto role `user`, or `admin`, which includes it (`@PreAuthorize("hasRole('user')")` + `RoleHierarchy`) |
| http://localhost:8101/reports | with `admin` **or** `user` (`@PreAuthorize("hasAnyRole('admin', 'user')")` on `ReportService`) |
| `POST` http://localhost:8101/reports/generate | the same roles; runs `@Async` and answers with the thread and the user it ran for |
| http://localhost:8101/logout | signs out here **and** at Logto, back to `/` |

## How it works

```text
browser ── GET /me ──────────────────────► app: not signed in
        ◄─ 302 /oauth2/authorization/logto ─── (one provider, so no login page of its own)
        ◄─ 302 localhost:3001/oidc/auth?client_id=...&scope=openid profile email roles&state&nonce
        ── username + password ──────────► Logto
        ◄─ 302 /login/oauth2/code/logto?code=...
app ── code + client secret ─────────────► Logto /oidc/token  → ID token (signed, ES384) + access token
app ── access token ─────────────────────► Logto /oidc/me     → userinfo
app: LogtoOidcUserService → roles claim → ROLE_*, upsert app_user → session cookie → 302 /me
```

```text
security/
  SecurityConfig          rules, oauth2Login, logout at Logto too, ES384 ID token decoder, RoleHierarchy
  LogtoOidcUserService    after each sign-in: Logto roles -> ROLE_*, records the local user
  CurrentUser             @CurrentUser OidcUser user: the signed-in user as a controller parameter
  CurrentUserService      the signed-in user in services, from the SecurityContext
user/
  AppUser                 the app's own row per Logto user, linked by Logto's user id (sub)
  AppUserRepository       insertIfMissing: INSERT ... ON CONFLICT DO NOTHING
  AppUserService          sign-in and webhook updates, delete
webhook/
  LogtoWebhookController  POST /webhooks/logto: signature, then LogtoUserSync
  LogtoWebhookSignature   HMAC-SHA256 of the raw body with the signing key
  LogtoUserSync           PostRegister, User.Created, User.Data.Updated, User.Deleted -> app_user
  LogtoWebhookEvent       the payload fields used
config/
  AsyncConfig             @EnableAsync + TaskDecorator: @Async runs as the calling user
home/
  HomeController          /, /me, /admin, /user
report/
  ReportController, ReportService   /reports, /reports/generate (@Async)
```

- **Configuration** (`application.yaml`): the registration `logto` with client id and secret, and
  only `issuer-uri`. Spring reads every endpoint and the signing keys from
  `http://localhost:3001/oidc/.well-known/openid-configuration` at startup.
- **Roles**: the `roles` scope makes Logto put the user's role names into the `roles` claim
  (`["admin"]`); `LogtoOidcUserService` turns them into `ROLE_admin`, so `hasRole("admin")` works.
  Roles are read at sign-in: a role assigned in Logto takes effect at the next sign-in.
- **No list of roles in the code** (no enum): every role Logto sends is mapped, so a new role needs
  only the Logto console and a check where it matters. Names are case-sensitive: `Admin` is not
  `admin`. Renaming a role in Logto means changing the `hasRole(...)` checks that use it.
- **Where to check roles**: a URL rule in `SecurityConfig` for a whole area (`/admin/**`), or
  `@PreAuthorize` (`@EnableMethodSecurity`) next to the code it protects, which also works on
  service methods that no URL reaches. `hasAnyRole('admin', 'user')` allows either.
- **Role hierarchy**: on their own, roles do not include each other: an admin without the `user`
  role would get 403 on `/user`. The `RoleHierarchy` bean in `SecurityConfig` says `admin` implies
  `user`, so there is no need to give every admin both roles in Logto:

  ```java
  @Bean
  static RoleHierarchy roleHierarchy() {
      return RoleHierarchyImpl.withDefaultRolePrefix()
              .role("admin").implies("user")
              .build();
  }
  ```

  URL rules and `@PreAuthorize` both use it. It only affects checks: `/me` still lists the
  authorities exactly as Logto sent them. It names only roles that include others; every other
  role, including ones added in Logto later, works as before. The other way round does not hold:
  a `user` is not an `admin`.
- **The signed-in user**: `OidcUser` holds everything Logto sent at sign-in (`getSubject()`,
  `getEmail()`, `getClaimAsStringList("roles")`, ...); knowing who is calling needs no database.

  ```java
  public Me me(@CurrentUser OidcUser user) { ... }          // controllers (@AuthenticationPrincipal)

  String logtoId = currentUserService.logtoId();            // services (SecurityContextHolder)
  ```

  The claims are a snapshot from sign-in; for fresh data call Logto's userinfo endpoint with the
  session's access token (`@RegisteredOAuth2AuthorizedClient("logto")`) or the Management API.
- **`@Async` runs as the calling user**: Spring Security keeps the user in a thread-local, empty on
  an executor's thread. `AsyncConfig` gives Spring Boot's executor a `TaskDecorator`
  (`DelegatingSecurityContextRunnable`): it captures the context when the task is submitted, sets
  it on the executor's thread and clears it afterwards, so `CurrentUserService` and
  `@PreAuthorize` work in `@Async` methods and nothing leaks between users on pooled threads.

  ```java
  @Bean
  TaskDecorator securityContextTaskDecorator() {
      return DelegatingSecurityContextRunnable::new;
  }
  ```

  It is a snapshot: a long task keeps the roles the user had when it started. Work without a user
  (`@Scheduled`, a message from a queue, a job stored in a database) has no context to carry:
  pass the user id as a parameter there. Do not use `MODE_INHERITABLETHREADLOCAL` instead: pooled
  threads are reused, so one user's context can end up in another user's task.
- **Local user**: Logto owns identity; anything the application stores about a user goes into
  `app_user`, linked by `sub`, never by e-mail (the user can change it in Logto).
- **Keeping it in sync**: on every sign-in, and through Logto webhooks, which also cover users
  created by an admin who never signed in here, and changes made in Logto between two sign-ins:

  | Event | When | Where the user is | `app_user` |
  |---|---|---|---|
  | `PostRegister` | a user signs up | `user` | created / updated |
  | `User.Created` | signed up, or created by an admin (console, Management API) | `data` | created / updated |
  | `User.Data.Updated` | e-mail, name, username changed | `data` | created / updated |
  | `User.Deleted` | deleted in Logto | `params.userId` (`data` is null) | deleted |

  - **Signature**: the endpoint is public (Logto has no session); what proves a request is from
    Logto is `logto-signature-sha-256`, the HMAC-SHA256 hex of the **raw** body with the webhook's
    signing key. The controller takes the body as `byte[]`: parsing and re-serialising it would
    change the bytes. Compared in constant time. No key configured: every webhook is rejected.
  - **Idempotent**: one sign-up sends both `PostRegister` and `User.Created`, and Logto retries a
    delivery that got a 5xx (up to 3 times). Every handler gives the same result when repeated.
  - **Concurrent**: the first sign-in and the sign-up webhooks arrive at the same moment. With
    "find, then save" both would insert and one would fail on the unique key (the sign-in, at
    worst: an error page). `insertIfMissing` is `INSERT ... ON CONFLICT DO NOTHING`, so both succeed.
  - **Synchronous**: a database update is quick, and answering only after it succeeded means a
    failure reaches Logto as an error (retried, visible in the webhook's delivery log). Answering 2xx
    and working in the background would lose that.
  - **Order is not guaranteed**: a retried `User.Data.Updated` arriving after `User.Deleted` would
    recreate the row. Rare; if it matters, compare `createdAt` of the event with the last one applied.
  - **Deleting**: the row is deleted because nothing references `app_user`. Once other tables do
    (orders, invoices), anonymise it instead, so their history still points to a user.
- **Sign-out**: `POST /logout` ends the app session and redirects to Logto's `end_session_endpoint`
  (`OidcClientInitiatedLogoutSuccessHandler`). Without that, Logto's session stays alive and the
  next "sign in" logs the user in again without asking for the password.

## Pitfalls

- **ES384.** Logto signs ID tokens with ES384; Spring Security expects RS256 unless told otherwise,
  and every sign-in fails with `invalid_id_token`. `SecurityConfig.idTokenDecoderFactory()` sets it.
- **`ENDPOINT` is the issuer.** `ENDPOINT=http://localhost:3001` in `docker-compose.yml` is what
  Logto writes into every token as the issuer, and Spring checks it against `issuer-uri`. Here both
  are `localhost:3001` because the app runs on the host. If the app ran in a container, `localhost`
  would be the container itself: use a hostname both the browser and the app can resolve.
- **Logto must be up before the app starts**, because of the discovery request at startup.
- **`updated_at` and `created_at` are in milliseconds** in Logto's claims; OIDC says seconds, so
  `OidcUser.getUpdatedAt()` returns a date in the year 58709. Read the raw claim if you need them.
- **Sign-out needs the post sign-out redirect URI registered** in Logto, otherwise Logto refuses
  to redirect back.
- **Webhooks to a private address are blocked** by Logto (SSRF protection), and the app on the host
  is one: `host.docker.internal` is `192.168.65.254` on Docker Desktop, the docker0 gateway
  (usually `172.17.0.1`) on Linux. `SSRF_ALLOWED_ADDRESSES` in `docker-compose.yml` allows both
  (IPs or CIDR ranges, comma-separated; host names are not accepted). In production the endpoint is
  a public URL and needs no exception.
- **A signing key with a trailing `\r` or newline** (a `.env` saved on Windows, a copy-paste)
  fails every signature while looking right. `LogtoWebhookSignature` strips it.
- **`ddl-auto: update` does not change existing columns.** `last_sign_in_at` became nullable (a
  user created by a webhook has not signed in); a database created before that needs
  `ALTER TABLE app_user ALTER COLUMN last_sign_in_at DROP NOT NULL;` (or `docker compose down -v`).
  One more reason for Flyway or Liquibase in a real application.

## Tests

- `HomeControllerTest` (`@WebMvcTest`, no Logto needed): `/` is public; `/me` redirects anonymous
  users to Logto; `/me` shows the signed-in user; `/admin` is 403 without the role and 200 with it;
  `/user` needs `user`; `admin` includes `user` (`/user` 200) but not the other way round (`/admin`
  403 for a `user`); `/reports` accepts `admin` or `user`, not others, and `ReportService` gets the
  user from `CurrentUserService`;
  sign-out redirects to Logto's `end_session_endpoint` with `id_token_hint` and
  `post_logout_redirect_uri`.
- `LogtoOidcUserServiceTest`: Logto roles become `ROLE_*` authorities.
- `CurrentUserServiceTest`: the signed-in user from the security context; empty or an error when
  nobody is signed in.
- `LogtoWebhookControllerTest` (`@WebMvcTest`, payloads shaped like Logto's): each event reaches
  the right `AppUserService` call; other events are acknowledged and ignored; a missing or wrong
  signature is 401 and changes nothing. Signatures are computed in the test, independently.
- `LogtoWebhookSignatureTest`: the well-known HMAC-SHA256 test vector ("The quick brown fox..."); a changed body fails;
  whitespace around the key is ignored; no key accepts nothing.
- `AsyncSecurityContextTest` (Spring Boot's real executor): an `@Async` method runs on another
  thread as the calling user, and on reused threads each task gets its own caller. Without the
  `TaskDecorator` it fails with "No signed-in user".

The whole flow was checked against the Logto in `docker-compose.yml`: sign-in with username and
password, ID token verified (ES384), `roles` → `ROLE_admin`, `app_user` row created, `/admin` 200,
sign-out ends the Logto session (the next visit to `/me` shows Logto's sign-in page again).
Webhooks from Logto, signature verified: a user created and changed by an admin appears in
`app_user` without signing in, a deleted one disappears, a sign-up (`PostRegister` +
`User.Created`) leaves one row; ten concurrent deliveries for one new user: all 204, one row.
`POST /reports/generate` ran on `task-1` for the signed-in user.
