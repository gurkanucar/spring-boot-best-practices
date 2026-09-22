<title>Spring Cache Best Practices</title>

# Spring Cache Best Practices

A teaching project for Spring's `@Cacheable`/`@CachePut`/`@CacheEvict` abstraction:
fail-safe error handling, in-memory (Caffeine) and distributed (Redis) cache managers,
and a catalog of runnable examples covering the pitfalls that are easy to hit
and hard to notice — silent no-ops on non-public methods, self-invocation, and
polymorphic JSON round-tripping through Redis. No real domain logic; the examples
return fake data on purpose so the caching behavior itself stays the whole point.

Spring Boot 4.1.1 · Java 25 · Jackson 3 · Lettuce/Redis · Caffeine

## Running it

```bash
docker compose up -d     # starts Redis on localhost:6379 (see docker-compose.yml)
./mvnw spring-boot:run
```

The app starts fine even without Redis running — see [Fail-safe error handling](#fail-safe-error-handling)
below. You only need `docker compose up -d` for the Redis-backed examples and for the
tests in `RedisCachingIntegrationTest` / `SerializationExamplesTest` to have something
to talk to.

## Cache managers and policies

Choose both the backing store and TTL at the call site. Each store has managers
for `30S`, `1M`, `3M`, `5M`, `10M`, `30M`, and `1H`. `CAFFEINE_5M` is the
`@Primary` default when a cache annotation omits `cacheManager`.

```java
// Local cache: this method's results expire after one minute.
@Cacheable(cacheNames = CacheNames.USERS, cacheManager = CacheManagers.CAFFEINE_1M)
public User findLocal(Long id) { ... }

// Redis: this method's results expire after five minutes.
@Cacheable(cacheNames = CacheNames.USERS, cacheManager = CacheManagers.REDIS_5M)
public User findDistributed(Long id) { ... }

// Use the same manager when updating or evicting that cache.
@CacheEvict(cacheNames = CacheNames.USERS, cacheManager = CacheManagers.REDIS_5M, key = "#id")
public void evictDistributed(Long id) { ... }
```

Caches are created on demand, so adding a logical cache only requires a name.
Caffeine caches have a 1,000-entry bound per cache and expire after write.
Each Caffeine manager owns separate local caches.

Redis managers include their TTL in the key namespace: the same `users` entry
with key `123` becomes `caching:ttl:60s:users::123` under `REDIS_1M`, and
`caching:ttl:300s:users::123` under `REDIS_5M`. The application prefix (`caching:`)
can be overridden with `app.redis.key-prefix`. Different TTL managers therefore
cannot overwrite or clear each other's entries. When data is cached under multiple
TTLs, invalidate each relevant manager explicitly (for example with `@Caching`).

The TTL namespace changes the old Redis key format; existing entries are no longer
read and expire under their previous TTL. This causes a one-time cold cache.

## Fail-safe error handling

`CachingConfig` implements `CachingConfigurer` and supplies a `LoggingCacheErrorHandler`
instead of Spring's default `SimpleCacheErrorHandler`. The default rethrows any
exception a cache operation throws — meaning a Redis outage or a corrupted entry would
fail the `@Cacheable` call outright. `LoggingCacheErrorHandler` logs it (`WARN`, no stack
trace by default) and lets the caching aspect fall through instead: a failed `GET` is
treated as a miss (the real method still runs and the caller gets a correct result, just
uncached), and a failed `PUT`/`EVICT`/`CLEAR` is simply dropped. `FailSafeCachingTest`
proves this against a deliberately unreachable Redis (`localhost:1`).

This alone isn't enough for a *snappy* fail-safe — without a short command timeout, a
blocked call would hang for Lettuce's 60s default before the handler ever gets a chance
to swallow anything. See `application.yaml`'s `spring.data.redis.connect-timeout`/`timeout`
(2s; measured against 1s being genuinely flaky under Docker Desktop's networking, ~25%
failure rate over 8 runs — the exact kind of thing worth a real timeout budget rather than
guessing).

Diagnosing a real caching problem later: flip `LoggingCacheErrorHandler`'s constructor
argument to `true` temporarily to get the full stack trace instead of just a one-line
message — that's literally how the `BigDecimal` validator gap below was found.

## Redis serialization: default typing and its validator

`RedisCacheConfig` serializes values as JSON via `GenericJacksonJsonRedisSerializer`, and
deliberately turns **on** default typing with a scoped `PolymorphicTypeValidator`:

```java
.serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
    GenericJacksonJsonRedisSerializer.builder()
        .enableDefaultTyping(TYPE_VALIDATOR)
        .build()));
```

`GenericJacksonJsonRedisSerializer` has default typing **off** by default. Without it, a
custom object round-trips as plain JSON with no `@class` hint, so it deserializes back as
a generic `LinkedHashMap` instead of your actual type. The write "succeeds" silently —
it's the *next* read (the cache hit) that throws `ClassCastException`, which makes this
easy to miss in ad hoc testing: a single call always looks fine.

The validator allows `com.gucardev.caching.*` plus `java.math.*`/`java.time.*`/`java.util.*`
— not because those JDK packages are "trusted" in some deep sense, but because Jackson
tags certain ambiguous JDK scalars (confirmed: `BigDecimal`) with a type hint even under
`NON_FINAL` mode, since a bare JSON number could be a `Double` or a `BigDecimal`. A
validator scoped to only this project's own package rejected resolving that tag and broke
every cached value containing one — `SerializationExamplesTest` exercises exactly this
(a `BigDecimal` total nested inside a list inside a record). This is deliberately **not**
`enableUnsafeDefaultTyping()`: that allows deserializing *any* class on the classpath, and
Redis is an external, potentially-shared store — an unrestricted validator turns "whatever
JSON happens to be at this key" into "whatever class Jackson is told to instantiate," a
real deserialization attack surface, not just a style choice. `SerializationExamples` /
`SerializationExamplesTest` also prove the harder case: a polymorphic `Shipment` interface
field keeps its concrete subtype (`StandardShipment` vs `ExpressShipment`) across the
round trip, not just "some object shaped like the interface."

## Two silent-failure pitfalls

Both of these have the same shape: the annotation does *nothing*, with no error and no
warning, and the method just runs uncached forever.

**Method visibility** (`PublicMethodPitfallExamples`) — Spring's default proxying uses a
CGLIB subclass (this project implements no interfaces, so a JDK interface proxy isn't
even possible). Verified empirically here, not assumed:
- **private** methods never cache, unconditionally — a subclass can't override a private
  method under any proxy strategy. The one case the "public methods only" rule is
  unconditionally true for.
- **package-private** methods *do* cache under this project's CGLIB proxying, because
  CGLIB generates its subclass in the same package specifically so it can override
  them — measured, not assumed. That's proxy-mode-specific, though: switch this bean to
  implement an interface (forcing a JDK proxy) and this exact method would silently stop
  caching with no code change to itself.
- **public** methods work under every proxy mode, no caveats — which is why "public" stays
  the right blanket rule to teach even though the real boundary is narrower.

**Self-invocation** (`SelfInvocationExamples`) — Spring's proxy only intercepts calls that
arrive from *outside* the bean. `this.findById(id)` (or a bare `findById(id)`) called from
another method in the *same class* is a plain Java call on the raw object; it never
reaches the proxy, so the annotation on the target method is never applied for that call
path. Three versions, in the order you'd reach for them:
1. `findByIdViaSelfInvocation` — broken, silently never caches.
2. `findByIdViaSelfProxy` — works, by resolving this bean's own proxy from the
   `ApplicationContext` and calling through that — a workaround, not the fix to reach for.
3. `findByIdViaCollaborator` — **preferred**: move the cached method to a separate
   `ProductLookup` collaborator bean and call it as an ordinary cross-bean call, which goes
   through `ProductLookup`'s own proxy with nothing special required.

## Examples catalog

Everything under `com.gucardev.caching.examples` returns fake/deterministic data — the
point is the caching behavior, not the domain. Each class's invocation counters (visible
in its tests) let a test tell "was the real method body reached" apart from "was this
served from cache," since the return value alone can't distinguish the two.

| Class | Demonstrates |
|---|---|
| `CacheableExamples` | `@Cacheable`: default key, SpEL composite key, `condition` (skip caching based on arguments), `unless` (skip *storing* based on the result), `sync = true` (single-flight / stampede protection) |
| `CacheMutationExamples` | `@CachePut` (always runs + refreshes), `@CacheEvict` (single key / `allEntries` / `beforeInvocation` vs. the default after-success timing), `@Caching` (evict-old + put-new "rename" in one method) |
| `ProgrammaticCacheExamples` | Direct `Cache`/`CacheManager` API — `getIfPresent`, `put`, `evict`, `get(key, Callable)` as the programmatic equivalent of `sync = true` |
| `PublicMethodPitfallExamples` | Method-visibility pitfall — see above |
| `SelfInvocationExamples` + `ProductLookup` | Self-invocation pitfall — see above |
| `SerializationExamples` + `dto/*` | Redis (de)serialization of a deliberately "confused" nested object: a record containing a `List`, a `Map`, `BigDecimal`, `Instant`, `UUID`, `Optional`, and a polymorphic interface field — see above |

## Testing notes

Without a running Redis, run the local examples, configuration/serialization checks,
and outage handling tests with:

```bash
./mvnw test '-Dtest=*,!RedisCachingIntegrationTest,!SerializationExamplesTest'
```

For the complete suite, start Redis with `docker compose up -d --wait`, then run
`./mvnw test`. `CacheConfigurationTest` checks the configured serializer directly;
the two Redis integration suites additionally verify actual cache reads and writes.

Caffeine-backed tests (`CacheableExamplesTest`, `CacheMutationExamplesTest`,
`ProgrammaticCacheExamplesTest`, `PublicMethodPitfallTest`, `SelfInvocationTest`) need
nothing external — pure in-memory, no network I/O, no flakiness risk.

Redis-backed tests (`RedisCachingIntegrationTest`, `SerializationExamplesTest`,
`FailSafeCachingTest`) need `docker compose up -d` first — `FailSafeCachingTest` is the
one exception, since it deliberately points at an unreachable `localhost:1` and must pass
whether or not Redis is running.

Every Redis-backed test that writes a key uses a name unique to that test run
(`"standard-" + System.nanoTime()`, etc.) instead of a fixed key reset via `clear()` in
`@BeforeEach`. That replaced an earlier version of these tests that *did* use fixed keys
plus a `clear()`-before-each-test reset, which passed reliably in isolation but failed
intermittently as part of the full suite. Root cause, confirmed by watching `redis-cli
monitor` while the suite ran: `clear()`'s `KEYS`+`UNLINK` and the next test's own
`@Cacheable` cache-lookup `GET` for the same fixed key run over different pooled
connections; occasionally the `GET` reached Redis a moment before `UNLINK` did, read the
previous test's about-to-be-deleted leftover value, and `@Cacheable` treated that as a
hit — skipping the real method call (and the write that would have followed it)
entirely. The key was then deleted out from under it, so polling for the write to appear
timed out. Unique per-test keys remove the race structurally: there's never a leftover
value for a fresh key to collide with, so there's nothing for `@BeforeEach` to clean up
in the first place. (An earlier hypothesis blamed Spring's cross-test `ApplicationContext`
caching and reached for `@DirtiesContext` — that didn't fix it, because it wasn't the
actual cause.)

```bash
docker exec caching-redis redis-cli flushall   # optional: clear leftover keys between manual runs
./mvnw test
```
