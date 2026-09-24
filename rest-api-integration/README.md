# Spring Boot REST API Integration (RestClient)

How to call another REST API from Spring Boot with `RestClient`: GET / POST / PUT / PATCH /
DELETE, Basic auth, custom and per-request headers, interceptors, error mapping, timeouts,
retry, circuit breaker, idempotent POSTs, a declarative HTTP interface, and SSL, including
how to disable certificate verification and the safer alternative.

Spring Boot 4.1.1 · Spring Framework 7 · Java 25 · JDK `HttpClient` · Resilience4j (Spring Cloud CircuitBreaker)

## Running

```bash
cd rest-api-integration
./mvnw spring-boot:run
```

The app starts on port `8093`. So that every example runs offline, the "remote" APIs it calls
live **inside the same application** (package `remote/`): a protected product API, a few
endpoints that misbehave on purpose, and a small HTTPS server with a self-signed certificate on
port `8443`. In a real project those would be someone else's services; nothing in the client
code knows the difference.

```
 your request ──> /api/...  (this app's API)
                     │  ProductClient / RestClient
                     ▼
                 /remote-api/...  (simulated third-party API, real HTTP)
                 https://localhost:8443/hello  (self-signed HTTPS server)
```

```bash
# CRUD, every call goes through RestClient to the remote API
curl localhost:8093/api/products
curl "localhost:8093/api/products?name=key"
curl localhost:8093/api/products/1
curl -X POST localhost:8093/api/products -H 'Content-Type: application/json' \
     -H 'Idempotency-Key: order-1' -d '{"name":"Monitor","price":199}'
curl -X PUT localhost:8093/api/products/1 -H 'Content-Type: application/json' -d '{"name":"Keyboard Pro","price":79}'
curl -X PATCH localhost:8093/api/products/1 -H 'Content-Type: application/json' -d '{"price":69}'
curl -X DELETE localhost:8093/api/products/2
curl localhost:8093/api/products/1/via-http-interface

# Headers: what the remote side actually received
curl localhost:8093/api/demo/headers -H 'X-Correlation-Id: abc-123'
curl "localhost:8093/api/demo/headers?bearer=true"

# Retry: the remote fails twice with 503, then succeeds
curl "localhost:8093/api/demo/retry/none?key=a&failTimes=2"         # 502, no retry
curl "localhost:8093/api/demo/retry/annotation?key=b&failTimes=2"   # 200, "attempt": 3
curl "localhost:8093/api/demo/retry/template?key=c&failTimes=2"     # 200, "attempt": 3
curl "localhost:8093/api/demo/retry/status/400?key=d"                # 400 is never retried
curl "localhost:8093/api/demo/hits?key=d"                            # {"hits":1}

# Timeout: the remote needs 5 s, the read timeout is 3 s
curl "localhost:8093/api/demo/timeout?delayMs=5000"                  # 504 after ~3 s

# Circuit breaker: run it 6 times; after 4 failures it stops calling the remote
curl "localhost:8093/api/demo/circuit-breaker?key=cb"

# SSL against the self-signed server
curl localhost:8093/api/demo/ssl/default     # 502, certificate not trusted
curl localhost:8093/api/demo/ssl/trusted     # 200, SSL bundle trusts that certificate
curl localhost:8093/api/demo/ssl/insecure    # 200, verification disabled (dev only!)
```

## Package layout

```
com.gucardev.restapiintegration
├── client/          RestClientConfig (all RestClient beans), RemoteApiProperties,
│   │                CorrelationIdInterceptor, LoggingInterceptor, InsecureSsl
│   └── error/       RemoteApiException, RemoteApiClientException (4xx), RemoteApiServerException (5xx)
├── product/         ProductClient (fluent RestClient), ProductHttpApi (declarative interface),
│   │                ProductController, ProductNotFoundException
│   └── dto/         ProductDto, ProductRequest, ProductPatchRequest
├── headers/         HeadersDemoController
├── resilience/      UnreliableApiClient (retry, timeout), CircuitBreakerClient, CircuitBreakerConfiguration
├── ssl/             SslDemoController
├── common/error/    GlobalExceptionHandler (remote failures -> 404 / 502 / 504)
└── remote/          the SIMULATED third-party APIs (server side, only here for the demo)
```

## 1. One configured `RestClient` per remote system

Everything that is the same for every call to a system goes into one bean
(`client/RestClientConfig`), so each call only describes itself:

```java
@Bean
@Qualifier("remoteApi")
RestClient remoteApiRestClient(RestClient.Builder builder, RemoteApiProperties properties) {
    HttpClientSettings settings = HttpClientSettings.defaults()
            .withTimeouts(properties.connectTimeout(), properties.readTimeout());

    return builder
            .baseUrl(properties.baseUrl())
            .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings))   // timeouts
            .defaultHeaders(headers -> {
                headers.setBasicAuth(properties.username(), properties.password()); // Basic auth
                headers.set("X-Api-Key", properties.apiKey());                       // custom header
                headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            })
            .defaultHeader("User-Agent", "rest-api-integration/1.0")
            .requestInterceptor(new CorrelationIdInterceptor())
            .requestInterceptor(new LoggingInterceptor())
            .defaultStatusHandler(HttpStatusCode::isError,
                    (request, response) -> { throw RemoteApiException.from(request, response); })
            .build();
}
```

- **Start from the injected `RestClient.Builder`**, not `RestClient.builder()`. Boot's builder
  already has the application's JSON mapper, message converters and observation (metrics and
  tracing). It is a prototype bean: every injection point gets its own copy, so customizing it
  for one client does not affect another.
- **Configuration, not code**, for the URL, credentials and timeouts (`remote-api.*` in
  `application.yaml`, bound to the `RemoteApiProperties` record). In production the password
  comes from an environment variable or a secret manager.
- **Several remote systems means several beans**, told apart with `@Qualifier`.

## 2. The HTTP verbs (`product/ProductClient`)

| Verb | Example | Notes |
|---|---|---|
| GET list | `get().uri(...).retrieve().body(new ParameterizedTypeReference<List<ProductDto>>() {})` | generic types need a `ParameterizedTypeReference` |
| GET one | `get().uri("/products/{id}", id)...onStatus(404 → ProductNotFoundException)` | call-specific error handling |
| POST | `post().contentType(JSON).header("Idempotency-Key", key).body(request).retrieve().toEntity(ProductDto.class)` | `toEntity` also gives the status and `Location` |
| PUT | `put().uri("/products/{id}", id).body(request)` | replaces the whole resource |
| PATCH | `patch().uri("/products/{id}", id).body(patchRequest)` | only the fields to change |
| DELETE | `delete().uri(...).retrieve().toBodilessEntity()` | no response body |

- **URI templates encode values for you.** `uri("/products/{id}", id)` and
  `queryParam("name", "{name}").build(value)` encode the value fully. Writing the value straight
  into `queryParam("name", value)` does **not** encode `&`, `+` or `=`: a search for
  `"& Chips"` would reach the server as an empty `name` plus a stray parameter. The test
  `queryParameterValuesAreFullyEncoded` covers this.
- **PATCH needs a capable HTTP client.** The JDK `HttpClient` (used here) and Apache HttpClient
  support it; the old `HttpURLConnection`-based `SimpleClientHttpRequestFactory` does not.
- **PATCH bodies should contain only the changed fields.** `ProductPatchRequest` is annotated
  with `@JsonInclude(NON_NULL)`, so `new ProductPatchRequest(null, 9.9)` is sent as
  `{"price":9.9}`, not `{"name":null,"price":9.9}` (which some APIs read as "clear the name").
- **Validate before calling.** `ProductRequest` has Bean Validation constraints; invalid input
  gets a 400 from this app without a remote call.

### The same API as an interface (`product/ProductHttpApi`)

```java
@HttpExchange(url = "/products", accept = "application/json")
public interface ProductHttpApi {
    @GetExchange("/{id}") ProductDto get(@PathVariable long id);
    @PostExchange(contentType = "application/json")
    ResponseEntity<ProductDto> create(@RequestBody ProductRequest request,
                                      @RequestHeader("Idempotency-Key") String idempotencyKey);
    @PatchExchange(url = "/{id}", contentType = "application/json")
    ProductDto patch(@PathVariable long id, @RequestBody ProductPatchRequest request);
    // list, replace, delete ...
}
```

Spring generates the implementation (similar to Feign) on top of the **same configured
`RestClient`**, so auth, headers, timeouts and error mapping are identical
(`ProductHttpApiConfig`). The interface is less code; the fluent client is easier to customize
per call. Example: `ProductClient.get` turns a remote 404 into our own 404, while the interface
has no call-specific handler, so its 404 goes through the default status handler and becomes a 502.

## 3. Headers and interceptors

`GET /api/demo/headers` calls the remote echo endpoint and returns what it received:

```json
{
  "method": "GET", "path": "/remote-api/echo", "query": "page=1",
  "headers": {
    "authorization": "Basic ***",                           <- client default (defaultHeaders)
    "x-api-key": "demo-api-key",                            <- client default
    "user-agent": "rest-api-integration/1.0",               <- client default
    "accept": "application/json",                           <- client default
    "x-correlation-id": "3f1c...", or your own "abc-123"    <- CorrelationIdInterceptor / propagated
    "x-request-source": "headers-demo",                     <- this request only
    "accept-language": "tr-TR"                              <- this request only
  }
}
```

- **Defaults** (`defaultHeader(s)`) are for every call; **per-request** headers
  (`.header(...)`, `.headers(h -> ...)`) are added to one call and **replace** a default with the
  same name: `?bearer=true` sends `Authorization: Bearer ...` instead of the Basic default.
- **Interceptors** (`ClientHttpRequestInterceptor`) handle cross-cutting concerns.
  `CorrelationIdInterceptor` adds `X-Correlation-Id` unless the caller already set one, so an
  incoming id is propagated. `LoggingInterceptor` logs method, path, status and duration, but not
  headers, query strings or bodies, which can hold credentials and personal data.

## 4. Errors: typed exceptions and meaningful status codes

`defaultStatusHandler` turns every unhandled 4xx/5xx into `RemoteApiClientException` (4xx) or
`RemoteApiServerException` (5xx), carrying the status and a size-limited body. The split
matters later: **a 5xx may succeed if retried, a 4xx never will.**

`GlobalExceptionHandler` answers our own caller:

| What happened | Our response |
|---|---|
| remote 404 on a product, handled with `onStatus` | `404 Not Found` |
| any other remote error status | `502 Bad Gateway` with `remoteStatus`; the remote body is logged, not returned |
| read or connect timeout (`ResourceAccessException` → `HttpTimeoutException`) | `504 Gateway Timeout` |
| TLS handshake failure (`SSLException`) | `502` with the root cause class name |
| connection refused / DNS failure | `502` |

Without a status handler, `RestClient` throws `HttpClientErrorException` or
`HttpServerErrorException`. Those work too, but your own types keep Spring's HTTP classes out of
the service layer and let you decide which exceptions a retry or circuit breaker reacts to.

## 5. Timeouts

```yaml
remote-api:
  connect-timeout: 2s    # establishing the TCP connection (unreachable host, firewall drop)
  read-timeout: 3s       # waiting for the response once connected (slow server)
```

- **Always set both.** The JDK `HttpClient` has no connect timeout by default and a request
  without a timeout waits forever. One stuck remote can block every request thread of your
  application.
- They are applied through `HttpClientSettings` when the request factory is built. To use the
  same timeouts for every auto-configured client instead, use the global properties
  `spring.http.clients.connect-timeout` / `spring.http.clients.read-timeout`.
- **Need a different timeout for one endpoint?** Create a second `RestClient` bean with its own
  settings; the timeout belongs to the request factory, not to a single call.
- A read timeout is **not** automatically safe to retry: the server may still be processing the
  request. That is why the timeout demo does not retry.

## 6. Retry (Spring Framework 7, no extra dependency)

Declarative, on a bean method, enabled with `@EnableResilientMethods` on the application class:

```java
@Retryable(includes = {RemoteApiServerException.class, ResourceAccessException.class},
           maxRetries = 3, delay = 100, multiplier = 2, maxDelay = 2000, jitter = 20)
public Map<String, Object> flakyWithAnnotation(String key, int failTimes) { ... }
```

Programmatic, with a `RetryTemplate`:

```java
RetryTemplate retryTemplate = new RetryTemplate(RetryPolicy.builder()
        .maxRetries(3).delay(Duration.ofMillis(100)).multiplier(2).maxDelay(Duration.ofSeconds(2))
        .includes(RemoteApiServerException.class, ResourceAccessException.class)
        .build());
retryTemplate.invoke(() -> flaky(key, failTimes));
```

Rules this example follows:

- **Retry only what can succeed next time**: 5xx and I/O errors. Never 4xx: bad credentials or
  invalid input fail the same way every time. (Test: a 400 reaches the server once, a 500 four
  times.)
- **Exponential backoff with jitter.** Retrying immediately hits a struggling server harder;
  jitter keeps many clients from retrying at the same moment.
- **`maxRetries = 3` means 4 attempts** in total. After the last one, the last exception is
  thrown.
- **Only retry idempotent calls.** GET, PUT and DELETE are safe to repeat. A POST is safe only
  with an **idempotency key**. `ProductController` creates the key *once*, outside the retried
  `ProductClient.create`, so every attempt sends the same key and the server creates the product
  only once. (Test: the same `Idempotency-Key` twice returns `201` and then `200` with the
  same product.)
- `@Retryable` works through the Spring proxy: calling the method from **another bean**
  retries, calling it from the same class (`this.method()`) does not.
- Retries multiply latency: 4 attempts with a 3 s read timeout can take over 12 s. Keep the
  total within what your own caller will wait (`RetryPolicy.builder().timeout(...)` caps it).

## 7. Circuit breaker (Resilience4j)

When a remote system is down, retrying every request just adds load and makes your own
responses slow. A circuit breaker notices the failure rate and **stops calling** for a while:

```java
circuitBreakerFactory.create("remote-api").run(
        () -> client.flaky(key, failTimes),
        throwable -> fallback(throwable));      // also called instantly while the circuit is open
```

`CircuitBreakerConfiguration`: the circuit opens when at least 50% of the last 4 calls failed,
stays open for 30 s, and then lets one trial call through. Only 5xx and I/O errors count as
failures, since a 4xx says nothing about the remote side's health.

```
call 1-4  -> remote 503 -> fallback "remote call failed"       (remote hits: 4)
call 5..  -> fallback "circuit open, remote not called"         (remote hits: still 4)
```

In `application.yaml`:

```yaml
spring.cloud.circuitbreaker.resilience4j:
  disable-thread-pool: true
  disable-time-limiter: true
```

By default Spring Cloud runs every call on a separate thread pool under a **1 second time
limiter**. That silently cancels any call slower than 1 s, whatever the HTTP timeouts say, and
moves the call to another thread (losing thread-bound context such as MDC or transactions).
Here the HTTP client's own timeouts are the single source of truth.

To combine the two, put the retry inside the circuit breaker, so that one logical call with
its retries counts as one outcome. Do not retry when the circuit is open: that failure is
immediate.

## 8. SSL: disabling verification, and the better alternative

`https://localhost:8443/hello` uses a self-signed certificate
(`src/main/resources/ssl/demo-server.p12`), like many internal or partner test servers.

| Client | How | Result |
|---|---|---|
| `defaultSsl` | JDK default trust store | **502**: `PKIX path building failed` |
| `trustedSsl` | Spring Boot SSL bundle that trusts exactly this certificate | **200**, fully verified |
| `insecureSsl` | trust-all `SSLContext` | **200**, but would also accept an attacker's certificate |

**The right way: trust that specific certificate** with an SSL bundle:

```yaml
spring.ssl.bundle.pem.demo-server-trust.truststore.certificate: classpath:ssl/demo-server.crt
```

```java
@Bean
RestClient trustedSslRestClient(RestClient.Builder builder, RestClientSsl ssl) {
    return builder.apply(ssl.fromBundle("demo-server-trust")).build();
}
```

Chain validation, expiry and hostname checks all stay on; only this one extra certificate (or
your company's internal CA) is trusted.

**Disabling verification (development only):**

```java
var requestFactory = ClientHttpRequestFactoryBuilder.jdk()
        .withHttpClientCustomizer(httpClient -> httpClient.sslContext(InsecureSsl.trustAllContext()))
        .build();
```

`InsecureSsl` uses an `X509ExtendedTrustManager` with empty methods. A plain
`X509TrustManager` is not enough: the JDK wraps it and still checks the hostname.

This disables TLS's protection against man-in-the-middle attacks: anyone on the network path
can read and change the traffic, including the credentials in it. If you use it at all, restrict
it to a local profile, never apply it to the auto-configured builder that other clients share,
and never ship it to production. Prefer the SSL bundle; it is two lines of configuration.

The private key in `demo-server.p12` exists only for this demo and is public in this
repository; it secures nothing.

## Tests

```bash
./mvnw test
```

The tests start the application on a free port (`IntegrationTestBase`) because the clients make
**real HTTP calls**. With a mocked server, timeouts, TLS and retry-on-connection-error could not
be observed. The simulated remote API counts requests per `key`, so the tests can check how
many calls actually reached the server.

- `ProductApiTest`: every verb through our API, `Location` on create, idempotent POST, PATCH
  keeping other fields, remote 404 becomes our 404 (GET, PUT, PATCH, DELETE), validation
  before the call, query parameter encoding, and the HTTP interface with every verb.
- `RemoteApiAuthTest`: Basic auth and API key (200 / 401 / 403) as typed exceptions; error
  messages without query string or credentials.
- `HeadersDemoTest`: default, interceptor and per-request headers as received by the remote
  side; correlation id propagation; per-request Authorization replacing the default.
- `ResilienceTest`: no retry, annotation and template retry, retry exhaustion (exactly 4
  calls), no retry for 4xx, read timeout becoming 504 without waiting for the slow server,
  circuit opening after 4 failures and then not calling the remote at all, and 4xx not opening
  the circuit.
- `SslDemoTest`: the default trust store rejects the self-signed server, the SSL bundle and
  the trust-all client both connect.
