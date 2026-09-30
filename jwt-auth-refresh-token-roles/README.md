# JWT Auth with Refresh Tokens, Roles and OTP

Stateless authentication built **only with Spring Security's own JWT support**
(`spring-boot-starter-oauth2-resource-server`: `JwtEncoder` / `JwtDecoder`, Nimbus JOSE under the
hood). No jjwt, no auth0 java-jwt.

| Topic | How it is done here |
|---|---|
| Access token | RS256 JWT, 30 min, signed with an RSA key pair loaded from PEM files |
| Verification by others | Public key published at `GET /.well-known/jwks.json` |
| Refresh token | Opaque 256-bit random string, 2 days, **rotated and single use**, stored as SHA-256 |
| Users ↔ roles | Unidirectional JPA many-to-many (`user_roles`), hierarchy `SUPERADMIN > ADMIN > USER`, `@PreAuthorize("hasRole(...)")` |
| One-time codes | Password reset, e-mail verification, phone verification; e-mail link or SMS code |
| Delivery | `OtpSender` strategy: SMTP mail (Mailpit locally) and an SMS stub that logs |
| Custom claims | From code (`JwtClaimsContributor` beans) and per user from the database (admin API) |
| Cleanup | `@Scheduled` job deletes expired refresh tokens and codes |

Spring Boot 4.1.1 · Java 25 · Spring Security 7 · PostgreSQL 17 · Thymeleaf · H2 in tests

## Running

```bash
cd jwt-auth-refresh-token-roles
docker compose up -d          # PostgreSQL on 5435, Mailpit on 1025 (SMTP) / 8025 (UI)
./mvnw spring-boot:run
```

- API: http://localhost:8102 · Swagger UI: http://localhost:8102/swagger-ui.html
- Mailpit (every mail the app sends): http://localhost:8025
- Seeded superadmin: `admin@example.com` / `admin12345` (override with `ADMIN_EMAIL`, `ADMIN_PASSWORD`)

Tables are created by Hibernate (`ddl-auto: update`). Tests run on H2 and need no Docker:
`./mvnw test`.

## Walkthrough with curl

```bash
# 1. Register. No tokens yet: the e-mail must be confirmed first.
curl -s -X POST localhost:8102/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"jane@example.com","password":"password123","phone":"+905551112233"}'

# 2. Open Mailpit (http://localhost:8025), open the mail, click the link and press "Confirm my e-mail".
#    The SMS code for the phone is in the application log ("SMS to +905551112233: ...").

# 3. Log in.
curl -s -X POST localhost:8102/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"jane@example.com","password":"password123"}'
# {"accessToken":"eyJ...","refreshToken":"q3F...","tokenType":"Bearer","expiresIn":1800}

ACCESS=eyJ...; REFRESH=q3F...

# 4. Call protected endpoints.
curl -s localhost:8102/api/demo/user  -H "Authorization: Bearer $ACCESS"   # 200
curl -s localhost:8102/api/demo/admin -H "Authorization: Bearer $ACCESS"   # 403
curl -s localhost:8102/api/me         -H "Authorization: Bearer $ACCESS"   # claims in the token

# 5. Rotate the refresh token. The old one is dead afterwards.
curl -s -X POST localhost:8102/api/auth/refresh -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH\"}"

# 6. As admin: add a custom claim to Jane; it appears in her next token.
curl -s -X PUT localhost:8102/api/admin/users/<jane-id>/claims/tenant_id \
  -H "Authorization: Bearer $ADMIN_ACCESS" -H 'Content-Type: application/json' -d '{"value":"acme"}'
```

## Endpoints

| Method & path | Access | Result |
|---|---|---|
| `POST /api/auth/register` | public | 201 `{id,email}`; 409 duplicate e-mail/phone |
| `POST /api/auth/login` | public | 200 tokens; 401 bad credentials; 403 e-mail not verified |
| `POST /api/auth/refresh` | public | 200 new tokens; 401 unknown/expired/used |
| `POST /api/auth/logout` | public | 204 (idempotent) |
| `GET /api/auth/verify-email?token=` | public | confirmation page (does **not** verify) |
| `POST /api/auth/verify-email/submit` | public | result page |
| `POST /api/auth/verify-email` | public | 204 / 400 (JSON variant) |
| `POST /api/auth/resend-verification` | public | 202 always |
| `POST /api/auth/forgot-password` `{email, channel: EMAIL\|SMS}` | public | 202 always |
| `POST /api/auth/reset-password` `{token}` or `{email, code}` + `newPassword` | public | 204 / 400 |
| `GET /api/auth/reset-password?token=` · `POST …/submit` | public | reset form / result page |
| `POST /api/auth/change-password` | authenticated | 204 / 400 |
| `GET /api/me` · `PUT /api/me/phone` · `POST /api/me/phone/verify` | authenticated | profile from token; phone (409 at verify if another account verified the number) |
| `POST`/`DELETE /api/admin/users/{id}/roles/{role}` | ADMIN | 204; 400 for `SUPERADMIN` |
| `GET /api/admin/users/{id}/claims` · `PUT`/`DELETE …/claims/{name}` | ADMIN | claims |
| `POST /api/admin/users/{id}/reset-password` | ADMIN | 204 |
| `GET /.well-known/jwks.json` | public | JWK Set |
| `GET /api/public/hello`, `/api/demo/**` (`user`, `admin`, `superadmin`, `shared`) | public / by role | demo |

Admin endpoints aimed at the superadmin account answer 403 unless the caller is the superadmin.

## Roles and hierarchy

- Three roles: `USER`, `ADMIN`, `SUPERADMIN`, with `SUPERADMIN > ADMIN > USER` (a `RoleHierarchy`
  bean). Both the filter chain (`/api/admin/**` → `hasRole('ADMIN')`) and `@PreAuthorize` apply it,
  so a user only needs the highest role: the superadmin holds just `SUPERADMIN`, an admin just `ADMIN`.
- **One superadmin.** The seeder creates it (only if no account holds `SUPERADMIN` yet). The admin API
  refuses to assign or remove `SUPERADMIN` (400).
- **The superadmin account is off limits to admins.** Otherwise an `ADMIN` could reset the
  superadmin's password and log in as the superadmin. Only the superadmin manages its own account (403
  for anyone else).
- Tokens carry only the **assigned** roles (`"roles": ["SUPERADMIN"]`); the hierarchy is applied when
  authorizing. Another service verifying our tokens through the JWKS needs the same hierarchy.

## Flows

### Login and refresh rotation

```
client                         AuthService                     RefreshTokenStore (DB)
  | POST /login {email,pw}  ->  | authenticate (BCrypt)          |
  |                             | issue JWT (RS256, 30 min)      |
  |                             | new random refresh token   ->  | save SHA-256(token), userId, +2d
  | <- {access, refresh}        |                                |
  | POST /refresh {refresh} ->  | consume(refresh)           ->  | DELETE ... WHERE hash=? AND expires_at>now
  |                             |   exactly 1 row deleted?   <-  | yes -> userId / no -> 401
  |                             | issue new JWT + new refresh -> | save
  | <- {access', refresh'}      |                                |
```

The conditional `DELETE` is what makes a refresh token single use: two requests racing with the same
token cannot both delete the row. A reused token is simply unknown (401).

A wrong password on an account whose e-mail is not verified yet is a plain 401: the password is
checked before the account status, so the answer never reveals that the account exists.

### One-time codes

```
forgot-password {email, channel}
  -> OtpService.issue(user, PASSWORD_RESET, channel)
       cooldown? (same user, purpose, channel and target within 60 s) -> send nothing
       EMAIL: 256-bit token, stored as SHA-256, sent as a link   (15 min)
       SMS:   6 digits, stored as HMAC-SHA256(pepper, ...),     (15 min, max 5 wrong tries)
  -> OtpSenderRegistry.forChannel(channel).send(message)
reset-password {token | email+code, newPassword}
  -> verify, set password, delete all reset codes of the user, revoke all refresh tokens
```

| Purpose | Channel | Lifetime |
|---|---|---|
| `PASSWORD_RESET` | EMAIL link or SMS code to the **verified** number (user chooses) | 15 min |
| `EMAIL_VERIFICATION` | EMAIL link | 24 h |
| `PHONE_VERIFICATION` | SMS code to the **pending** number | 5 min |

### Phone numbers: verified vs pending

- `users.phone` holds the **verified** number and is unique; `users.pending_phone` holds a number
  waiting for its SMS code and is not unique.
- Register (`phone`) or `PUT /api/me/phone` only sets `pending_phone` and texts a code there. No
  uniqueness check at this point, so nobody can squat a number they do not own, and the answer does
  not reveal which numbers are registered.
- `POST /api/me/phone/verify` moves the pending number into `phone`, or answers 409 if another account
  has verified that number meanwhile. Until then the old verified number keeps working (password
  reset by SMS goes there).
- `PUT` with the current verified number does nothing. The `phone_verified` claim is `phone != null`.

At most one code exists per user, purpose and channel, so an e-mail reset and an SMS reset do not
cancel each other; a successful reset with either deletes both.

The SMS attempt limit holds under parallel requests: an attempt is taken with one atomic
`UPDATE ... WHERE attempts < 5` **before** the code is compared, and a code is accepted only by the
request whose `DELETE` actually removed it. A code whose attempts are used up stays stored until it
expires, so asking for a new code does not reset the limit. The requests that check a code commit
even when they answer 400 (`noRollbackFor`), so a wrong guess is always counted.

Issuing a code locks the user row (`SELECT ... FOR UPDATE`), so a double-clicked "forgot password"
still answers 202 twice and sends one code. The mail or SMS goes out only after the transaction
commits, and SMTP has 5-second timeouts.

Mail links only **show** a page on GET. Mail security scanners (Outlook Safe Links, antivirus bots)
open links on their own, so confirming the e-mail and setting the new password happen in the POST
behind the page's button.

## Custom claims

- **From code:** implement `JwtClaimsContributor` as a bean. `declaredClaims()` lists the names it may
  emit; they become reserved. Two contributors declaring the same name, or a stored user claim
  already using it, stop the application at startup (`ClaimRegistry`).
- **From the database:** `PUT /api/admin/users/{id}/claims/{name}`. Names must match
  `^[a-z][a-z0-9_]{0,63}$` and must not be reserved (`sub`, `roles`, `email_verified`, ...).
- **Merging:** the issuer adds the standard claims first and then each contributor's result with
  `putIfAbsent`; any duplicate throws. Bean order never decides which value wins.
- **Reading:** `CurrentUser.from(jwt).claim("tenant_id")` in a controller that takes
  `@AuthenticationPrincipal Jwt jwt`; `GET /api/me` shows every claim of the caller.

Spring Security 7 adds a `FACTOR_BEARER` authority to every JWT authentication, next to the
`ROLE_*` authorities built from the `roles` claim.

## Interfaces and implementations

| Interface | Implementation | Swap it for |
|---|---|---|
| `RefreshTokenStore` | `JpaRefreshTokenStore` | Redis with TTL keys |
| `OtpStore` | `JpaOtpStore` | Redis |
| `OtpSender` | `EmailOtpSender`, `SmsOtpSender` | Twilio, SES, ... |
| `MailComposer` | `ThymeleafMailComposer` | FreeMarker, a mail service's templates |
| `OtpCodeHasher` | `HmacOtpCodeHasher` | HMAC with a key from a KMS |
| `TokenHasher` | `Sha256TokenHasher` | — |
| `RandomTokenGenerator` | `SecureRandomTokenGenerator` | — |
| `AccessTokenIssuer` | `NimbusAccessTokenIssuer` | a different key source (KMS signing) |
| `JwtClaimsContributor` | `VerificationStatusClaimsContributor`, `UserClaimsContributor` | your own claims |
| `PasswordPolicy` | `MinLengthPasswordPolicy` | breached-password check, complexity rules |
| `ExpiredEntryCleaner` | both JPA stores | anything else that expires |

## Package layout

```
com.gucardev.jwtauthrefreshtokenroles
├── auth/     controller/ (JSON + Thymeleaf pages), service/, dto/
├── user/     controller/ (me, admin), service/, repository/, entity/, dto/
├── role/     repository/, entity/
├── token/    access/ (issuer, CurrentUser) + access/claims/, refresh/, jwks/
├── otp/      service/, store/, delivery/
├── demo/     controller/, service/, dto/
└── common/   config/, security/, cleanup/, error/
```

## Database tables

| Table | Content |
|---|---|
| `users` | id (UUID), email (unique), password (BCrypt), email_verified, phone (verified, unique), pending_phone, created_at |
| `roles` | id, name (`USER`, `ADMIN`, `SUPERADMIN`) |
| `user_roles` | user_id, role_id |
| `user_claims` | id, user_id, claim_name, claim_value — unique (user_id, claim_name) |
| `refresh_tokens` | token_hash (PK), user_id, expires_at |
| `one_time_codes` | id, user_id, purpose, channel, code_hash, target, expires_at, attempts, created_at — unique (user_id, purpose, channel) |

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `security.jwt.private-key` / `public-key` | `classpath:keys/app.key` / `app.pub` | PEM key pair |
| `security.jwt.key-id` | `demo-key-1` | `kid` header and JWKS key id |
| `security.jwt.access-token-ttl` / `refresh-token-ttl` | `30m` / `2d` | lifetimes |
| `security.public-paths` | see `application.yaml` | endpoints without a token |
| `security.token-cleanup-cron` | `0 0 * * * *` | cleanup schedule, `-` disables |
| `security.password.min-length` | `8` | password policy |
| `otp.pepper` | demo value, env `OTP_PEPPER` | HMAC key for SMS codes, ≥ 32 bytes |
| `otp.cooldown` / `otp.max-attempts` | `60s` / `5` | resend window, wrong SMS tries |
| `otp.ttl.*` | `15m` / `24h` / `5m` | reset / e-mail / phone code lifetimes |
| `app.base-url` | `http://localhost:8102` | links in mails |

Tests load `src/test/resources/config/application.yaml` on top of the main file: H2 in memory, the
cleanup cron disabled, and a mocked `JavaMailSender`.

Generate your own keys:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out app.key
openssl pkey -in app.key -pubout -out app.pub
# then: SECURITY_JWT_PRIVATE_KEY=file:/secrets/app.key SECURITY_JWT_PUBLIC_KEY=file:/secrets/app.pub
```

## Limitations and production notes

- **Demo secrets.** The key pair in `src/main/resources/keys` and the default `otp.pepper` are public.
  Replace both outside local development.
- **Revocation is not instant.** Password changes, resets and role/claim changes delete refresh
  tokens, but an access token already issued stays valid until it expires (at most 30 min). Shorter
  access tokens or a deny-list would close that window.
- **Tokens in URLs.** Mail links carry the token in the query string. The app sends
  `Referrer-Policy: no-referrer`, the pages load no third-party resources, GET never consumes a token
  and tokens are single use and short-lived, but a reverse proxy may still log the URL. A SPA can read
  the token in the browser and call the JSON endpoints instead.
- **SMS is a stub.** `SmsOtpSender` only logs the code.
- **Numeric codes.** 6 digits are weak on their own; the HMAC pepper protects them in a database dump,
  the 5-attempt limit and short lifetime online.
- **Schema.** `ddl-auto: update` keeps the example small; use Flyway or Liquibase in a real system.
- **Synchronous delivery.** Codes are sent after commit but still on the request thread, so a slow
  SMTP server slows down register / forgot-password (up to the 5-second timeouts). An outbox or an
  async sender would decouple it; a crash between commit and send loses that one mail (the user can
  ask again after the cooldown).
