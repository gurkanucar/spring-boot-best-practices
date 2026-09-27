# Spring Boot: Per-Client API Rate Limiting with Bucket4j

Protects **our own API** from its callers: every request is charged against token buckets chosen by
**who** is calling (API key, browser, IP as a last resort), not simply by IP. It solves the classic
problem of many real users sharing one public IP (a school, an office, a mobile carrier) without
letting a single script hide behind fresh cookies or fake `X-Forwarded-For` headers.

Buckets live in memory, or in **Redis** so that every instance of the app shares them. Both use the
same Bucket4j token-bucket algorithm.

Spring Boot 4.1.1, Java 25, Bucket4j **8.20.0** (`bucket4j_jdk17-core`, `bucket4j_jdk17-lettuce`),
Caffeine, Lettuce, Redis 8.8 (optional), Testcontainers.

> Limiting the calls **we** make to a provider (e.g. an SMS gateway with 10 requests/s) is a
> different job: see the outbound rate limiter in [`resillience4j`](../resillience4j/).

## Run

```bash
./mvnw spring-boot:run                          # in-memory buckets, port 8097

docker compose up -d                            # Redis on localhost:6382
./mvnw spring-boot:run -Dspring-boot.run.arguments=--app.rate-limit.store=redis
```

```bash
./mvnw test     # the Redis test runs only when Docker is available, otherwise it is skipped
```

## Token bucket

Each limit is a bucket of `capacity` tokens. A request takes tokens (1 by default); tokens come back
**evenly** over `period`. "20 per minute" means one token every 3 seconds, up to 20 saved.

```yaml
plans:
  free: [ { capacity: 20, period: 1m }, { capacity: 5, period: 1s } ]
```

- **Bursts are allowed, floods are not.** A client that was quiet has a full bucket and may send a
  few requests at once; after that it gets what the refill rate gives.
- **No window edge.** A fixed window ("20 per minute, counter resets at :00") lets a client send 20
  at 12:00:59 and 20 more at 12:01:00: 40 in two seconds. A token bucket has no such edge.
- **Several limits at once.** A request needs a token from every limit of its bucket: "20 per
  minute **and** 5 per second" allows short bursts but not a whole minute's quota in one second.
- **Costs.** Expensive endpoints take more tokens:

  ```yaml
  costs:
    "[/api/reports/export]": 5
  ```

  A cost must fit into the smallest capacity of every caller who may use the endpoint. With 10
  here, a free client (5 per second) could never export: its bucket can never hold 10 tokens.

## The problem with limiting by IP

The obvious key for a rate limit is the client's IP address. It fails in both directions:

- **Many people, one IP.** A school or university network, an office, a café's Wi-Fi, and most
  mobile carriers (carrier-grade NAT) put hundreds or thousands of real users behind one public
  IP. With "100 requests/minute per IP", one busy student uses up the limit of the whole campus,
  and everybody else gets 429.
- **One attacker, many IPs.** Cheap proxies, IPv6 ranges and botnets give an attacker a fresh IP
  whenever needed. And if the app trusts `X-Forwarded-For` blindly, the attacker does not even
  need them: sending a different fake header value gets a new bucket for every request.

So an IP says little about **who** is calling. The solution is to identify the caller whenever
possible, and to use the IP only as a coarse safety net.

## The solution: identify the caller

`RateLimitPolicy` puts each request into one or more **buckets** (a bucket = one token bucket):

| Caller | Buckets | Limit (demo values) |
|---|---|---|
| Application with `X-API-Key` | `client:<clientId>` | its plan: free 20/min and 5/s, pro 200/min and 20/s. **The IP is not used at all.** |
| Anonymous browser | `anon:<anon_id cookie>` | 5/min per browser |
|  | **and** `ip:<client ip>` | 30/min for all anonymous traffic from that IP |
| Anonymous, IP in a known shared network | `anon:<anon_id cookie>` | 5/min per browser |
|  | **and** `ip:<client ip>` | 300/min (`app.rate-limit.shared-networks`) |

1. **Identify the caller.** API clients send an API key and get a limit per key, so fifty
   applications behind the same NAT gateway do not affect each other. For logged-in users the same
   idea applies with the user id (with Spring Security: `request.getUserPrincipal().getName()`
   → bucket `user:<id>`). In a school, every student who is logged in has their own bucket, no
   matter how many share the IP. **This is the real fix: make heavy endpoints require login or an
   API key.**
2. **Separate anonymous browsers.** The first response sets a random `anon_id` cookie, so two
   classmates on the same Wi-Fi get separate small buckets. The cookie grants nothing; it only
   splits buckets, so a random UUID is enough.
3. **Keep an IP ceiling for anonymous traffic.** A cookie is free to throw away: a script that
   drops it gets a new `anon` bucket on every request. So all anonymous traffic from one IP also
   shares a larger per-IP bucket. The ceiling must be generous, because it is shared by everyone
   behind that IP.
4. **Raise the ceiling for known shared networks.** If you know that a university, a partner's
   office or a carrier's NAT range sends lots of legitimate traffic, list it with a higher limit:

   ```yaml
   shared-networks:
     - name: campus
       cidr: 10.20.0.0/16
       per-ip: [ { capacity: 300, period: 1m } ]
   ```

5. **Find the real client IP safely** (`ClientIpResolver`). Behind a load balancer every request
   comes from the balancer's IP, and the client IP is in `X-Forwarded-For`. That header is plain
   text anyone can send, so:
   - it is only read when the direct peer is one of **our** proxies (`trusted-proxies`);
   - it is read **from the right**: each proxy appends the address it saw, so the right-most
     entries are written by our proxies, and the first address that is not ours is the client.
     Anything to the left of it was written by the client and is ignored.

   ```text
   X-Forwarded-For: 6.6.6.6, 203.0.113.9        peer: 10.0.0.5 (our load balancer)
                    ^^^^^^^  ^^^^^^^^^^^
                    forged   appended by our LB = the real client
   ```

   Spring Boot can do this for you (`server.forward-headers-strategy: native` with
   `server.tomcat.remoteip.internal-proxies`); `ClientIpResolver` shows what that does.

Other options, depending on the situation:

- **CAPTCHA or a challenge instead of a hard 429** for anonymous traffic above a threshold: a real
  student solves it once and continues, a script does not.
- **Different limits per endpoint.** Login and password reset get strict limits (per account and
  per IP), cheap read endpoints loose ones.
- **Make limits visible.** `RateLimit-Limit`, `RateLimit-Remaining` and `Retry-After` let
  well-behaved clients slow down before they hit the wall.
- **For DDoS, an edge layer** (CDN / WAF / API gateway) that absorbs traffic before it reaches the
  app. An application rate limiter is for fairness and abuse, not for floods.


## Try it

```bash
B=http://localhost:8097

# An API client: its own quota, the IP is irrelevant. 5 per second, then 429 with Retry-After: 1
for i in 1 2 3 4 5 6; do curl -s -o /dev/null -w "%{http_code} " -H "X-API-Key: demo-free-key" $B/api/products; done
#   200 200 200 200 200 429
curl -i -H "X-API-Key: demo-pro-key" $B/api/products
#   RateLimit-Limit: 20   RateLimit-Remaining: 19   RateLimit-Policy: 200;w=60, 20;w=1

# Two browsers on the same IP: -c/-b keep the anon_id cookie like a browser does
for i in 1 2 3 4 5 6; do curl -s -o /dev/null -w "%{http_code} " -c alice.txt -b alice.txt $B/api/whoami; done
#   200 200 200 200 200 429   <- Alice used up her 5
curl -s -o /dev/null -w "%{http_code}\n" -c bob.txt -b bob.txt $B/api/whoami
#   200                       <- Bob, same IP, own bucket

# An expensive endpoint: export costs 5 tokens, a whole minute for an anonymous browser
curl -s -o /dev/null -w "%{http_code}\n" -X POST -c carol.txt -b carol.txt $B/api/reports/export   # 200
curl -s -o /dev/null -w "%{http_code}\n" -c carol.txt -b carol.txt $B/api/products                 # 429

# A script that drops the cookie: stopped by the per-IP ceiling (30/min) instead
for i in $(seq 1 31); do curl -s -o /dev/null -w "%{http_code} " -H "X-Forwarded-For: 198.51.100.3" $B/api/whoami; done

# A campus IP gets the higher ceiling (localhost is a trusted proxy in the demo config)
curl -s -H "X-Forwarded-For: 10.20.5.17" $B/api/whoami
#   "buckets":[{"key":"ip:10.20.5.17","limits":[{"capacity":300,...}]}, {"key":"anon:...", ...}]

# A forged left-most entry is ignored
curl -s -H "X-Forwarded-For: 6.6.6.6, 10.20.5.17" $B/api/whoami        # still ip:10.20.5.17

curl -i -H "X-API-Key: made-up" $B/api/whoami                          # 401
```

A rejected request gets:

```http
HTTP/1.1 429
Retry-After: 1
RateLimit-Limit: 5
RateLimit-Remaining: 0
RateLimit-Policy: 20;w=60, 5;w=1
Content-Type: application/problem+json

{"status":429,"title":"Too Many Requests","detail":"Rate limit exceeded for client:acme-mobile",
 "bucket":"client:acme-mobile","policy":"20;w=60, 5;w=1","cost":1,"retryAfterSeconds":1}
```

`Retry-After` is exact: Bucket4j reports how long until enough tokens are back. `RateLimit-Limit` /
`Remaining` describe the tightest limit, `RateLimit-Policy` lists all of them (IETF RateLimit
header draft). `X-RateLimit-Bucket` is for this demo only; a real API would not reveal how it
identifies callers.

## Limits charged from code: 3 reports per hour per e-mail

The filter only sees the request: API key, cookie, IP, path. Some rules need something only the
application knows, e.g. "an e-mail address may request 3 reports per hour, whatever IP, browser or
API key it uses". For those, `RateLimiter` charges a **named limit** from code, using the same store
(in memory or Redis):

```yaml
app:
  rate-limit:
    limits:
      report-per-email: [ { capacity: 3, period: 1h } ]
```

```java
@RateLimited(limit = "report-per-email", key = "#email")      // RateLimitExceededException -> 429
public Map<String, Object> generateMonthly(String email) { ... }

rateLimiter.consume("report-per-email", email);              // the same, without the annotation
```

- `@RateLimited` is a Spring AOP proxy (`RateLimitedAspect`, `spring-boot-starter-aspectj`): it only
  works on public methods called from another bean. `key` is SpEL over the parameters.
- The key must be normalised **before** the call (`ReportController.normalize`: trim, lower case with
  `Locale.ROOT`), or `Alice@x.com` and `alice@x.com` would be two buckets.

- `RateLimitExceptionHandler` turns the exception into the same 429 as the filter (`Retry-After`,
  `RateLimit-*`, problem details).
- The check is in `ReportService`, not in the controller: the rule belongs to generating a report,
  so it also holds when a report is requested from a scheduled job or a message listener.
- It comes **on top of** the filter: `/api/reports/monthly` still takes 1 token from the caller's
  bucket.
- The demo reads the e-mail from `X-User-Email` to stay small. Anyone can send any header: in a real
  application take it from the authenticated user (JWT, session), never from the request.

```bash
for i in 1 2 3 4; do curl -s -o /dev/null -w "%{http_code} " -X POST -H "X-API-Key: demo-pro-key"   -H "X-User-Email: alice@example.com" $B/api/reports/monthly; done
#   200 200 200 429   <- Retry-After: 1200 (one token every 20 minutes)
```

## Do you need all this? The simple way and the alternatives

Most of this project is here to **show** things: trusting `X-Forwarded-For` safely, anonymous
browsers, per-IP ceilings, shared networks, in-memory vs Redis, fail open, `RateLimit-*` headers.
A single rule like "3 reports per hour per e-mail" needs none of it. On one instance this is
enough, no extra beans:

```java
@Service
class ReportService {

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(1))   // at least the period, or a user gets a fresh bucket
            .build();

    Map<String, Object> generateMonthly(String email) {
        Bucket bucket = buckets.get(email, key -> Bucket.builder()
                .addLimit(limit -> limit.capacity(3).refillGreedy(3, Duration.ofHours(1)))
                .build());
        if (!bucket.tryConsume(1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        }
        ...
    }
}
```

With several instances, replace the `Cache` with Bucket4j's Redis `ProxyManager`
(see `RedisRateLimitStore`): a few more lines.

| Option | When | Note |
|---|---|---|
| The snippet above | One rule, one place | No `Retry-After` / `RateLimit-*` headers unless you add them. |
| `RateLimiter` / `@RateLimited` (this project) | The same kind of rule in several places | Config in YAML, same store and 429 answer as the filter. |
| [`bucket4j-spring-boot-starter`](https://github.com/MarcGiffing/bucket4j-spring-boot-starter) | **Least code**: limits per URL in YAML, key in SpEL (`cache-key: getHeader('X-User-Email')`), an annotation for methods | The latest release (0.12.10, June 2025) is for Spring Boot 3. Its master branch has moved to Boot 4, but no release yet, so this Boot 4 project cannot use it. |
| Resilience4j `@RateLimiter` | One global limit for all callers, e.g. protecting a provider | No key: it cannot limit per e-mail or per user. |
| API gateway (Spring Cloud Gateway `RequestRateLimiter`, nginx `limit_req`, Kong, a cloud gateway) | Limits by IP, API key or header without code in the app | Rules that need the application's knowledge (report generation) usually stay in the app. |

A sensible split: general limits (IP, API key) at the gateway, rules tied to business operations
in the application.

## Where the buckets live

| `app.rate-limit.store` | Class | |
|---|---|---|
| `in-memory` (default) | `InMemoryRateLimitStore` | Bucket4j local buckets in a Caffeine cache. A client idle for the longest period is dropped: its bucket would be full again anyway, so memory does not grow with every IP that ever called. |
| `redis` | `RedisRateLimitStore` | Bucket4j's Lettuce proxy manager. Each bucket's state is one Redis key, updated with **compare-and-swap** (write only if nobody changed it meanwhile, else retry), so instances never lose each other's updates. Keys expire once their bucket would be full again. |

In-memory buckets exist once per JVM: with 3 instances behind a load balancer, a client spread
across them gets 3x its limit. Use Redis when there is more than one instance.

**If Redis is down, the filter fails open**: requests are served without limits and a warning is
logged. `spring.data.redis.timeout: 500ms` keeps that fast. Rejecting every request because the
limiter is broken is usually worse; where abuse is the bigger risk (login, SMS verification), fail
closed instead.

## Configuration

```yaml
app:
  rate-limit:
    store: in-memory                  # in-memory | redis
    trusted-proxies: [127.0.0.1/32, ::1/128]
    api-keys:
      demo-free-key: { client-id: acme-mobile, plan: free }
      demo-pro-key: { client-id: globex-backend, plan: pro }
    plans:
      free: [ { capacity: 20, period: 1m }, { capacity: 5, period: 1s } ]
      pro: [ { capacity: 200, period: 1m }, { capacity: 20, period: 1s } ]
    anonymous:
      per-client: [ { capacity: 5, period: 1m } ]
      per-ip: [ { capacity: 30, period: 1m } ]
    shared-networks:
      - { name: campus, cidr: 10.20.0.0/16, per-ip: [ { capacity: 300, period: 1m } ] }
    costs:
      "[/api/reports/export]": 5
    limits:                           # named limits charged from code (RateLimiter)
      report-per-email: [ { capacity: 3, period: 1h } ]
```

Choosing the numbers: the per-IP ceiling must be high enough for the biggest shared network you
expect; the real fairness comes from identity-based buckets.

## Code

```text
ratelimit/
  RateLimitFilter        charges /api/** requests, 429 + Retry-After, RateLimit-* headers, fail open
  RateLimitPolicy        which buckets a request belongs to (API key / anon cookie / IP), its cost
  RateLimiter            named limits charged from code; RateLimitExceededException -> 429
  RateLimited            the same as an annotation (RateLimitedAspect), key in SpEL
  ClientIpResolver       real client IP from X-Forwarded-For, only through trusted proxies
  CidrRange, RateLimitBucket, RateLimitProperties, RateLimitConfig
ratelimit/store/
  RateLimitStore (interface)
  InMemoryRateLimitStore Bucket4j local buckets + Caffeine
  RedisRateLimitStore    Bucket4j + Lettuce, shared by all instances
demo/
  DemoController         /api/whoami, /api/products (1 token), /api/reports/export (5 tokens)
report/
  ReportController       POST /api/reports/monthly, e-mail from X-User-Email
  ReportService          3 reports per hour per e-mail (@RateLimited)
```

## Tests

- `InboundRateLimitTest` (real HTTP): API clients have their own quota whatever their IP; tokens come
  back gradually; export costs 5; browsers behind one IP get separate buckets; dropping the cookie
  does not escape the per-IP ceiling; campus ceiling; forged `X-Forwarded-For` ignored; unknown key
  401; `RateLimit-*` headers.
- `RedisRateLimitStoreTest` (Testcontainers Redis): two "instances" share one bucket; every limit
  must have enough tokens; a request costing more than the capacity gets a bounded `Retry-After`.
- `ReportRateLimitTest`: 3 reports per e-mail, then 429 while another e-mail still passes; case and
  spaces in the e-mail do not give a fresh bucket.
- `ClientIpResolverTest`, `FailOpenTest`.
