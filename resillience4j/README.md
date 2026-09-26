# Spring Boot + Resilience4j

A shop backend that depends on six third-party APIs, and one example per Resilience4j pattern for
living with them: **retry**, **circuit breaker**, **time limiter**, **bulkhead**, **rate limiter**
and retry + circuit breaker **combined**. All of them protect us **from the services we call**.
Protecting our own API **from its callers** (per-client rate limits) is in
[`rate-limiting-bucket4j`](../rate-limiting-bucket4j/).

```text
                        ┌── Retry ───────────── FX rates API          (short 503 blips)
 GET/POST /api/** ──────┼── CircuitBreaker ──── recommendation engine (down for minutes)
                        ├── TimeLimiter ─────── courier API           (sometimes very slow)
                        ├── Bulkhead ────────── invoice PDF renderer  (slow, small capacity)
                        ├── RateLimiter ─────── SMS gateway           (max 10 requests/s)
                        └── Retry+CircuitBreaker payment gateway      (must not charge twice)
```

Spring Boot 4.1.1, Java 25, Resilience4j 2.4.0 (`resilience4j-spring-boot4`).

The third-party APIs are **fake**: they are served by this same application under `/fake-api/**`,
and you can break them at runtime (make them fail, go down, or get slow). The clients still call
them over real HTTP, through `RestClient`, exactly as they would call a remote service.

## Run

```bash
./mvnw spring-boot:run
```

The app listens on **8098**. Nothing else is needed. On Windows use `mvnw.cmd`.

```bash
./mvnw test     # Java 25
```

```bash
B=http://localhost:8098
J="Content-Type: application/json"
```

## Controlling the fake APIs

| Request | Effect |
|---|---|
| `GET  /fake-api/admin` | hits, failures and current behaviour of every fake API |
| `PUT  /fake-api/admin/{api}` `{"failNext": 2}` | the next 2 calls answer 503 |
| `PUT  /fake-api/admin/{api}` `{"down": true}` | every call answers 503 until `{"down": false}` |
| `PUT  /fake-api/admin/{api}` `{"latencyMs": 3000}` | every call takes 3 seconds |
| `POST /fake-api/admin/reset` | everything healthy again, counters to zero |

`{api}` is one of `rates`, `recommendations`, `shipping`, `invoices`, `sms`, `payments`.
The **hits** counter is the important one: it shows how many requests really reached the remote
service, which is exactly what most of these patterns change.

Resilience4j's own view is on actuator: `/actuator/health` (circuit breakers),
`/actuator/circuitbreakers`, `/actuator/circuitbreakerevents`, `/actuator/retries`,
`/actuator/retryevents`, `/actuator/ratelimiters`, `/actuator/bulkheads`, `/actuator/timelimiters`.
Every decision is also logged (`ResilienceEventLogger`):

```text
[retry:exchangeRates] attempt 1 failed (ServiceUnavailable), next try in 272 ms
[retry:exchangeRates] attempt 2 failed (ServiceUnavailable), next try in 550 ms
[circuit-breaker:recommendations] State transition from CLOSED to OPEN
[bulkhead:invoices] call rejected, bulkhead is full
```

## How it is wired

Each remote dependency has a **named instance** in `application.yaml`, and the client method refers
to it by name:

```java
@Retry(name = "exchangeRates")
public ExchangeRate rate(String from, String to) { ... }
```

```yaml
resilience4j:
  retry:
    instances:
      exchangeRates:
        max-attempts: 4
        ...
```

The annotations are applied by Spring AOP (`spring-boot-starter-aspectj`), so the usual proxy rule
applies: the method must be `public` and called **from another bean**. A call from inside the same
class skips the proxy, and with it the retry.

`resilience4j-spring-boot4` brings the auto-configuration; the pattern modules
(`resilience4j-retry`, `-circuitbreaker`, `-ratelimiter`, `-bulkhead`, `-timelimiter`) are added
one by one in `pom.xml`. It is not in the 2.4.0 BOM yet, so its version is set explicitly.

## 1. Retry: short failures (`retry/ExchangeRateClient`)

The FX rate provider sometimes answers 503 for a moment. A second try a little later usually works.

```yaml
exchangeRates:
  max-attempts: 4                     # 1 call + 3 retries
  wait-duration: 200ms
  enable-exponential-backoff: true    # 200ms, 400ms, 800ms ...
  exponential-backoff-multiplier: 2
  enable-randomized-wait: true        # ... with +-50% jitter
  randomized-wait-factor: 0.5
  retry-exceptions: [HttpServerErrorException, ResourceAccessException]
  ignore-exceptions: [HttpClientErrorException]
```

```bash
curl -X PUT $B/fake-api/admin/rates -H "$J" -d '{"failNext": 2}'
curl $B/api/rates/USD/TRY           # 200, after 2 hidden failures
curl $B/api/rates/USD/XXX           # 502 at once: a 404 is not retried
curl -X PUT $B/fake-api/admin/rates -H "$J" -d '{"down": true}'
curl $B/api/rates/USD/TRY           # 502 after 4 attempts (~1.5 s)
curl $B/fake-api/admin                      # RATES hits: 3 + 1 + 4 = 8
```

- **Only retry what can succeed next time.** 5xx and I/O errors (connection refused, read timeout)
  are transient. A 4xx is our mistake and fails the same way every time.
- **Backoff and jitter.** Retrying immediately hits a struggling service again at once. With
  exponential backoff it gets breathing room; with jitter, thousands of clients that failed at
  the same moment do not all retry at the same moment either.
- **Only retry idempotent calls.** A GET is safe to repeat. A payment is not; see section 6.
- **Retries multiply load.** 4 attempts per call, and 3 layers of services that each retry 4 times,
  make 64 calls to the bottom service for one user click. Retry in one layer, not in every layer.

## 2. Circuit breaker: long outages (`circuitbreaker/RecommendationClient`)

The recommendation engine is down for minutes. Retrying would only add load to a service that is
already struggling, and make every product page wait for timeouts. The circuit breaker counts
failures, and when there are too many it **opens**: calls fail immediately, without touching the
remote, and a fallback answers instead.

```text
 CLOSED ── failure rate >= 50% of last 10 calls (min 5) ──▶ OPEN ── 15 s ──▶ HALF_OPEN
   ▲                                                         ▲                   │
   └──────────────── 2 trial calls succeed ──────────────────┼───────────────────┤
                                                             └── trial fails ────┘
```

```java
@CircuitBreaker(name = "recommendations", fallbackMethod = "bestsellers")
public Recommendations forProduct(long productId) { ... }

private Recommendations bestsellers(long productId, Throwable cause) { ... }   // same params + Throwable
```

```bash
curl -X PUT $B/fake-api/admin/recommendations -H "$J" -d '{"down": true}'
for i in $(seq 1 8); do curl -s $B/api/products/1/recommendations; echo; done
# 1-5: "fallbackReason": "recommendation engine failed: ServiceUnavailable"
# 6-8: "fallbackReason": "circuit open, recommendation engine not called"
curl $B/api/circuit-breakers/recommendations      # "state": "OPEN"
curl $B/fake-api/admin                                    # RECOMMENDATIONS hits: 5, not 8

curl -X PUT $B/fake-api/admin/recommendations -H "$J" -d '{"down": false}'
# after 15 s the circuit is HALF_OPEN; 2 successful calls close it again
```

- Slow calls count too: `slow-call-duration-threshold: 2s` and `slow-call-rate-threshold: 80`
  open the circuit when the remote is up but hopelessly slow.
- A 4xx is ignored (`ignore-exceptions`): our bad request says nothing about the remote's health.
- **A fallback must be acceptable to the business.** Bestsellers instead of personal
  recommendations: fine. A made-up stock level or "payment OK": not fine. Without a fallback, the
  open circuit gives `CallNotPermittedException`, which `GlobalExceptionHandler` turns into
  **503 + Retry-After**.

## 3. Time limiter: slow answers (`timelimiter/ShippingClient`)

The checkout page shows a shipping price. If the courier API is slow, the customer should not
wait: after 1 second a flat rate is shown instead.

```java
@TimeLimiter(name = "shipping", fallbackMethod = "flatRate")
public CompletableFuture<ShippingQuote> quote(String city) {
    return CompletableFuture.supplyAsync(() -> callCourier(city), remoteCallExecutor);
}
```

```bash
curl -X PUT $B/fake-api/admin/shipping -H "$J" -d '{"latencyMs": 3000}'
time curl "$B/api/shipping/quote?city=Ankara"      # ~1 s, "source": "flat-rate-fallback"
```

- `@TimeLimiter` only works on **async** return types (`CompletableFuture`), because something has
  to keep waiting for the slow call while we answer. Spring MVC can return the future directly.
- The time limiter stops **us waiting**, it does not stop the HTTP call. The call keeps running on
  its thread until the HTTP client gives up. That is why **every client also needs a read timeout**
  (`spring.http.clients.read-timeout: 5s` here). A time limiter is not a replacement for it.

## 4. Bulkhead: one slow dependency must not take everything (`bulkhead/InvoiceClient`)

Rendering an invoice PDF takes a second. If 200 users download invoices at once, 200 request
threads (and connections) wait on this one slow service, and the rest of the shop slows down with
it. Like the watertight compartments of a ship, a bulkhead caps the damage: at most 2 calls are in
flight, the rest are rejected immediately.

```yaml
invoices:
  max-concurrent-calls: 2
  max-wait-duration: 0      # reject at once; a small value would let callers queue briefly
```

```bash
for i in 1 2 3 4 5; do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST $B/api/orders/o-$i/invoice &
done; wait
# 200 200 503 503 503   (order varies)
```

This is a **semaphore** bulkhead: the call runs on the caller's thread, the bulkhead only counts.
Resilience4j also has a **thread-pool** bulkhead (`@Bulkhead(type = THREADPOOL)`) that runs calls on
its own bounded pool and returns a `CompletableFuture`.

## 5. Rate limiter, outbound: respect the provider's quota (`ratelimiter/SmsClient`)

The SMS gateway accepts 10 requests per second and answers **429** above that. Instead of hitting
its limit and dealing with the 429s, we never send faster than it allows: a permit every 200 ms,
and a caller without a permit **waits** for the next one.

```yaml
smsProvider:
  limit-for-period: 1
  limit-refresh-period: 200ms   # 5/s: well below the provider's 10/s
  timeout-duration: 5s          # wait up to 5 s for a permit, then RequestNotPermitted (503)
```

```bash
curl -X POST "$B/api/sms/campaign?messages=15"                  # throttled
# {"throttled":true,"messages":15,"sent":15,"rejectedByProvider429":0,"elapsedMs":2966}
sleep 1
curl -X POST "$B/api/sms/campaign?messages=20&throttle=false"   # no limiter
# {"throttled":false,"messages":20,"sent":10,"rejectedByProvider429":10,"elapsedMs":27}
```

- **Leave headroom.** Our time windows and the provider's are not aligned, and the network bunches
  requests together. At exactly 10/s we would still see some 429s.
- `@RateLimiter(name = "smsProvider")` is **one counter for all callers of this app instance**.
  That is right for an outbound quota, but with 3 instances the provider sees 3x the rate: divide
  the limit by the number of instances, or use a counter shared by all instances in Redis (see the
  Redis store in [`rate-limiting-bucket4j`](../rate-limiting-bucket4j/)).
- The same annotation is the **wrong tool for limiting our own clients**: it would put all users
  into a single bucket. That needs one bucket per client; see [`rate-limiting-bucket4j`](../rate-limiting-bucket4j/).

## 6. Retry + circuit breaker on a payment (`combined/PaymentClient`)

```java
@Retry(name = "payments")
@CircuitBreaker(name = "payments")
public Charge charge(String idempotencyKey, BigDecimal amount, String currency) { ... }
```

### Order of the aspects

Resilience4j always nests its aspects in this order, **regardless of the order of the annotations**
in the source:

```text
Retry ( CircuitBreaker ( RateLimiter ( TimeLimiter ( Bulkhead ( your method ) ) ) ) )
```

So each retry attempt is one call through the circuit breaker: failed attempts are counted and can
open the circuit. Once it is open, retrying is pointless (the circuit is still open 300 ms later),
so the retry ignores `CallNotPermittedException`:

```yaml
payments:
  ignore-exceptions:
    - org.springframework.web.client.HttpClientErrorException
    - io.github.resilience4j.circuitbreaker.CallNotPermittedException
```

The order can be changed (`resilience4j.retry.retry-aspect-order`,
`resilience4j.circuitbreaker.circuit-breaker-aspect-order`, ...), but the default is what you want
in almost every case.

### Idempotency: do not charge twice

A payment can fail **after** the gateway charged the card: the response is lost on the way back.
A blind retry charges the customer again. So every attempt of one payment sends the same
`Idempotency-Key`, and the gateway charges at most once per key. The key is created **once, before
the retry** (in the controller). Created inside the retried method, every attempt would get a new
key and the protection would be gone. The fake gateway simulates exactly this lost response:

```bash
curl -X PUT $B/fake-api/admin/payments -H "$J" -d '{"failNext": 2}'
curl -X POST -H "$J" $B/api/payments -d '{"amount": 49.90, "currency": "EUR"}'   # 200
curl $B/fake-api/payments          # "count": 1  (3 attempts reached the gateway, 1 charge)
```

No fallback here on purpose: pretending that a payment worked is worse than a clear error.

## Rate limiting our own API

Everything above protects us **from the services we call**. Protecting our API **from its callers**
(per-client limits, many users behind one IP, API keys, Redis-shared buckets) is a different job
and has its own example: [`rate-limiting-bucket4j`](../rate-limiting-bucket4j/).

## Choosing the numbers

| Setting | Start from |
|---|---|
| Read timeout | a bit above the remote's p99 latency; always set one |
| Retry attempts | 2-3 retries for reads, with backoff and jitter; none for non-idempotent calls without an idempotency key |
| Circuit breaker window | enough calls to be meaningful (10-100); `minimum-number-of-calls` so 1 failure out of 1 does not open it |
| Open state duration | about as long as the remote usually needs to recover |
| Bulkhead size | what the remote (or its connection pool) can really handle concurrently |
| Outbound rate limit | clearly below the provider's quota, divided by the number of instances |

Then watch the metrics (`/actuator/circuitbreakers`, `resilience4j_*` Micrometer metrics) and adjust.

## Project layout

```text
src/main/java/com/gucardev/resillience4j
├── fakeapi/          fake third-party APIs + admin endpoints to break them
├── common/           RestClient, exception -> HTTP status mapping, event logging
├── retry/            ExchangeRateClient       @Retry
├── circuitbreaker/   RecommendationClient     @CircuitBreaker + fallback
├── timelimiter/      ShippingClient           @TimeLimiter + fallback
├── bulkhead/         InvoiceClient            @Bulkhead
├── ratelimiter/      SmsClient                @RateLimiter (outbound)
└── combined/         PaymentClient            @Retry + @CircuitBreaker + Idempotency-Key
```

## HTTP status summary

| Situation | Status |
|---|---|
| Circuit open, bulkhead full, no outbound rate limit permit | **503** + `Retry-After`: we did not call the remote |
| Remote answered 5xx or I/O error (after retries) | **502** |
| Remote answered 4xx | **502**, not retried |
| Time limiter without fallback | **504** |
