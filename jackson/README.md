# Spring Boot Jackson Serialization Example

A teaching catalog for Jackson (de)serialization behavior in Spring Boot: the module-wide
`spring.jackson.*` switches, per-field/per-class annotation overrides, custom converters
(both local and globally registered), enum and polymorphic type handling, and where the
two can disagree in surprising ways.

Spring Boot 4.1.1 · Java 25 · Jackson 3.1.5 (`tools.jackson.*` databind/core,
`com.fasterxml.jackson.annotation.*` annotations - Jackson 3 kept the classic annotation
package but moved `ObjectMapper`/custom (de)serializer base classes to a new one; see
"Jackson 3 package layout" below)

## Running

```bash
cd jackson
./mvnw spring-boot:run
```

The app starts on port `8087`. Every example below was captured by actually running the
app and calling it with curl - not written from memory of how Jackson "should" behave.

## Module-wide configuration (`application.yaml`)

```yaml
spring:
  jackson:
    default-property-inclusion: non_null
    deserialization:
      fail-on-unknown-properties: false
    serialization:
      fail-on-empty-beans: false
    datatype:
      datetime:
        write-dates-as-timestamps: false
```

| Property | Effect demonstrated by |
|---|---|
| `default-property-inclusion: non_null` | `/api/jackson/profile` - a null `nickname` is omitted from the response entirely, not written as `"nickname":null` |
| `deserialization.fail-on-unknown-properties: false` | `/api/jackson/profile` accepts an extra field the DTO never declares, without a 400 |
| `serialization.fail-on-empty-beans: false` | `/api/jackson/ping` - a class with zero properties serializes to `{}` instead of throwing `InvalidDefinitionException` |
| `datatype.datetime.write-dates-as-timestamps: false` | `/api/jackson/profile`'s `createdAt` (an `Instant`) renders as an ISO-8601 string, not an epoch-based array/number |

## Endpoint catalog

Most endpoints are "echo" round trips: the same DTO comes in as the request body and goes
back out as the response, so it's tempting to think of a technique as applying to "the
DTO" rather than to one specific direction. The **Direction** column says which side each
technique actually governs - deserialization (request), serialization (response), or
both - since that's exactly what's easy to get wrong when reading the code out of context.

| Endpoint | Direction | Technique |
|---|---|---|
| `POST /api/jackson/profile` | Both, but per-field | **Request**: `id`/`internalRiskScore` sent by the client are ignored (`READ_ONLY`/`@JsonIgnore`); unknown fields are tolerated. **Response**: `non_null` omits `nickname`, `@JsonFormat` renders `birthDate`, `id`/`createdAt` are always present, `internalRiskScore` never appears |
| `POST /api/jackson/login` | Both, but per-field | **Request**: `password` is accepted. **Response**: `password` is never written back (`WRITE_ONLY`) |
| `POST /api/jackson/orders/legacy` | Request only | `@JsonAlias("qty")` and the `@JsonIgnoreProperties(ignoreUnknown = false)` gotcha both only affect parsing the incoming body - the response has nothing left to demonstrate |
| `POST /api/jackson/payments` | Both, opposite transforms per side | **Request**: `CardNumberDeserializer` strips spaces/dashes from the input. **Response**: `CardNumberSerializer` masks everything but the last 4 digits |
| `POST /api/jackson/coupons` | Both, same transform per side | **Request**: `UppercaseDeserializer` normalizes the input casing. **Response**: `UppercaseSerializer` keeps it uppercase even if it somehow arrived (or was constructed) lowercase |
| `POST /api/jackson/api-keys` | Response only | `PartialMaskSerializer` masks on the way out; there is deliberately no deserializer - nothing about the request is transformed |
| `POST /api/jackson/payments/status` | Both | **Request**: `@JsonCreator` parses the code or the Java name. **Response**: `@JsonValue` writes the lowercase code |
| `GET /api/jackson/notifications` | Response only | `@JsonTypeInfo`/`@JsonSubTypes` add the `"type"` discriminator when the server writes a `List<Notification>` - no request body at all |
| `POST /api/jackson/notifications/dispatch` | Request only | The client-sent `"type"` discriminator is what `@JsonTypeInfo`/`@JsonSubTypes` resolve here; the response is a plain `DispatchResult`, not a `Notification` |
| `POST /api/jackson/contacts` | Both | `@JsonUnwrapped` flattens `Address`'s fields on the way in **and** the way out |
| `POST /api/jackson/events` | Both, but one field is response-only | Most fields round-trip symmetrically (`LocalDate`, `LocalTime`, `LocalDateTime`, `Instant`, `Duration`). `publishedAt`'s UTC normalization is a **response**-only effect - it happens during serialization, not parsing |
| `GET /api/jackson/events/day-range` | Request only | `@DateTimeFormat` governs parsing the `date` query parameter; the `DayRange` response is plain, unannotated Jackson serialization |
| `GET /api/jackson/events/at` | Request only | Same - `@DateTimeFormat` governs the `at` query parameter; the response `LocalDateTime` uses Jackson's ordinary default rendering |
| `GET /api/jackson/money` | Response only | `MoneySerializer` (registered globally) - a `GET` with no body has nothing to deserialize |
| `GET /api/jackson/ping` | Response only | `fail-on-empty-beans: false` only matters when serializing the empty `Ping` - again, no request body |

## `non_null` inclusion + `@JsonProperty(access = READ_ONLY)`

```java
public record UserProfile(
        @JsonProperty(access = JsonProperty.Access.READ_ONLY) Long id,
        String fullName,
        String nickname,
        @JsonFormat(pattern = "dd/MM/yyyy") LocalDate birthDate,
        Instant createdAt) {}
```

Real round trip - the client tries to set `id` to `999` and omits `nickname`:

```
POST /api/jackson/profile
{"id": 999, "fullName": "Ada Lovelace", "birthDate": "10/12/1815",
 "createdAt": "2024-01-01T00:00:00Z", "extraUnknownField": "ignored"}

200 {"id":1,"fullName":"Ada Lovelace","birthDate":"10/12/1815",
     "createdAt":"2026-09-22T20:08:11.908166300Z"}
```

- `id` comes back as `1` (server-assigned), not the client's `999` - `READ_ONLY` means the
  property is written on output but ignored on input.
- `nickname` never appears at all - `non_null` inclusion, not a null value.
- `birthDate` keeps the custom `dd/MM/yyyy` shape from its own `@JsonFormat`.
- `createdAt` has no `@JsonFormat`, so it falls back to the module default
  (`write-dates-as-timestamps: false`) and renders as ISO-8601, not an epoch array.
- `extraUnknownField` doesn't cause a 400 - the module-wide leniency setting.

## `@JsonIgnore`: excluded from both directions

`UserProfile` also carries an `internalRiskScore` field the client should never see or
be able to set:

```java
public record UserProfile(
        @JsonProperty(access = JsonProperty.Access.READ_ONLY) Long id,
        String fullName,
        String nickname,
        @JsonFormat(pattern = "dd/MM/yyyy") LocalDate birthDate,
        Instant createdAt,
        @JsonIgnore Integer internalRiskScore) {}
```

```
POST /api/jackson/profile  {"fullName": "Ada", "createdAt": "2024-01-01T00:00:00Z", "internalRiskScore": 111}
200 {"id":1,"fullName":"Ada","createdAt":"...Z"}          <-- no "internalRiskScore" key at all
```

This is the difference from `READ_ONLY`/`WRITE_ONLY` above: those each expose exactly one
direction (output-only or input-only); `@JsonIgnore` exposes **neither**. The controller
itself sets `internalRiskScore` to `99` when building the response, and that value still
never reaches the wire. Because an HTTP round trip alone can't distinguish "the annotation
dropped the client's `111`" from "the controller code just never read it", the test also
deserializes straight through the injected `ObjectMapper` and asserts
`profile.internalRiskScore()` is `null` even though the JSON contained `111` -
confirming the value never survives into the constructed record at all, not just that it
never made it back out.

## `@JsonProperty(access = WRITE_ONLY)`

```java
public record LoginRequest(
        @NotBlank String username,
        @NotBlank @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password) {}
```

```
POST /api/jackson/login  {"username": "neo", "password": "matrix"}
200 {"username":"neo"}
```

Echoing the *entire record* back never leaks `password` - the property is excluded from
serialization outright, not nulled or masked. Compare this to the card-number masking
below, where the field is still present but transformed.

## `@JsonAlias`, and a real gotcha with `@JsonIgnoreProperties`

```java
@JsonIgnoreProperties(ignoreUnknown = false)
public record LegacyOrderRequest(
        @NotBlank String sku,
        @JsonAlias("qty") @Positive int quantity) {}
```

`@JsonAlias` works exactly as expected - the old field name is accepted alongside the
canonical one:

```
POST /api/jackson/orders/legacy  {"sku": "SKU-1", "qty": 5}
200 {"sku":"SKU-1","quantity":5}
```

**The gotcha**: `@JsonIgnoreProperties(ignoreUnknown = false)` on the class *looks* like it
should force strict rejection of unknown fields for this one endpoint, overriding the
module-wide leniency. It does not:

```
POST /api/jackson/orders/legacy  {"sku": "SKU-1", "quantity": 5, "note": "typo?"}
200 {"sku":"SKU-1","quantity":5}          <-- still 200, "note" is silently dropped
```

Jackson ignores an unknown property if **either** the class annotation says to **or** the
module-wide `FAIL_ON_UNKNOWN_PROPERTIES` feature is disabled - it's an OR, not an
override. Once the global feature is off, no per-class annotation can turn strictness back
on; the annotation can only ever grant *more* leniency under an otherwise-strict global
default, never take leniency away once the global default already grants it. Getting
strict validation back for one endpoint needs a dedicated `ObjectMapper`/message converter
for that endpoint, not an annotation. Confirmed by
`JacksonExamplesTest.classLevelIgnoreUnknownFalseDoesNotOverrideTheLenientGlobalDefault`.

## Custom converters: local vs. global

**The rule that decides which side each one touches**: deserialize means JSON -> Java, so
it runs while Jackson is reading an incoming **request** body - `@JsonDeserialize`/
`ValueDeserializer` manipulates what the client sent. Serialize means Java -> JSON, so it
runs while Jackson is writing the outgoing **response** body - `@JsonSerialize`/
`ValueSerializer` manipulates what the client is about to see. Nothing about either
annotation's *name* hints at this - it's purely "which direction does this word describe",
and it's worth having as a fixed rule of thumb rather than re-deriving it from an example
each time.

A local converter pair (`@JsonSerialize`/`@JsonDeserialize` on one field) can do the same
transform on both sides, opposite transforms on each side, or - just as legitimately - only
implement one side at all. `/payments`, `/coupons` and `/api-keys` show all three shapes.

### Opposite transforms per direction: masking a card number

```java
public record CardPaymentRequest(
        @JsonSerialize(using = CardNumberSerializer.class)
        @JsonDeserialize(using = CardNumberDeserializer.class)
        String cardNumber,
        @Positive BigDecimal amount) {}
```

```
POST /api/jackson/payments  {"cardNumber": "4111 1111 1111 1234", "amount": 19.999}
200 {"cardNumber":"**** **** **** 1234","amount":19.999}
```

**Request**: `CardNumberDeserializer` strips spaces/dashes from the input. **Response**:
`CardNumberSerializer` masks everything but the last four digits - so the full number is
*never* echoed back, even though the controller just returns the same record it received.
`amount` round-trips as exactly `19.999`: `BigDecimal` end to end, never a `double`, so
there's no float rounding or scientific notation to worry about.

### The same transform on both sides: normalizing casing

```java
public record CouponRequest(
        @JsonSerialize(using = UppercaseSerializer.class)
        @JsonDeserialize(using = UppercaseDeserializer.class)
        String code) {}
```

```
POST /api/jackson/coupons  {"code": "save10"}   -> 200 {"code":"SAVE10"}
POST /api/jackson/coupons  {"code": "SaVe10"}   -> 200 {"code":"SAVE10"}
```

**Request** and **Response** both apply `String::toUpperCase` here, which is redundant in
this endpoint specifically (the deserializer already normalized it before the controller
even ran) - but keeping the serializer too means `code` stays canonical even if some other
code path ever constructs a `CouponRequest` from an already-lowercase value directly,
bypassing deserialization entirely.

**A real, verified locale gotcha**: both converters call `toUpperCase(Locale.ROOT)`,
deliberately, never the plain no-argument `toUpperCase()`. Confirmed on this machine by
running the exact same code under two different JVM default locales:

```
default locale en_US:  "istanbul".toUpperCase()             = "ISTANBUL"
                        "istanbul".toUpperCase(Locale.ROOT)  = "ISTANBUL"

-Duser.language=tr -Duser.country=TR:
                        "istanbul".toUpperCase()             = "İSTANBUL"   <-- dotted capital I
                        "istanbul".toUpperCase(Locale.ROOT)  = "ISTANBUL"   <-- unaffected
```

Turkish has separate dotted and dotless versions of "I", so `toUpperCase()` with the
JVM's *default* locale turns a lowercase `i` into `İ` (dotted) instead of the `I`
(dotless) every other locale would produce - purely because of `-Duser.language=tr` on
that specific server, for input that never had anything to do with Turkish text. A
service that normalizes codes, slugs, or enum-like strings this way would produce a
*different* result in a Turkish-locale deployment than everywhere else, for the exact
same input - `Locale.ROOT` (or an explicit `Locale.ENGLISH`) is what makes the transform
deployment-independent.

### One direction only: partial masking, serializer-only

```java
public record ApiKeyRequest(String name, @JsonSerialize(using = PartialMaskSerializer.class) String apiKey) {}
```

```
POST /api/jackson/api-keys  {"name": "prod-key", "apiKey": "sk_live_51Hc9F2abcdef"}
200 {"name":"prod-key","apiKey":"sk_******************"}
```

**Response only** - there is deliberately no `@JsonDeserialize` here. `CardPaymentRequest`
needed a deserializer because the real card number is genuinely used afterward (charging
the card); an API key that's already been issued has nothing to "unmask" - the real value
was only ever meant to be sent once, by whoever generated it. `PartialMaskSerializer`
keeps the first 3 characters (enough for a human to recognize *which* key this is) and
turns the rest into `*`, the mirror image of `CardNumberSerializer` keeping the *last*
four digits instead.

### Global - a `SimpleModule` registered once, applied everywhere `Money` appears

```java
@Bean
JsonMapperBuilderCustomizer moneyModuleCustomizer() {
    SimpleModule module = new SimpleModule("money-module");
    module.addSerializer(Money.class, new MoneySerializer());
    module.addDeserializer(Money.class, new MoneyDeserializer());
    return builder -> builder.addModule(module);
}
```

```
GET /api/jackson/money
200 "100.50 USD"
```

No `@JsonSerialize`/`@JsonDeserialize` anywhere near the `Money` record itself - every
occurrence of the type, in any DTO, anywhere in the app, gets this string representation
for free. Reach for the local, per-field form when only one field of one DTO needs special
handling; reach for a global module when a value type (a currency amount, a masked
identifier, a custom ID format) should behave the same way no matter where it shows up.

## Enums: `@JsonValue` + `@JsonCreator`

```java
public enum PaymentStatus {
    PENDING("pending"), PAID("paid"), FAILED("failed");
    @JsonValue public String code() { return code; }
    @JsonCreator public static PaymentStatus fromValue(String value) { ... }
}
```

```
POST /api/jackson/payments/status  "paid"   -> 200 "paid"
POST /api/jackson/payments/status  "PAID"   -> 200 "paid"   (accepts the Java constant name too)
POST /api/jackson/payments/status  "bogus"  -> 400 {"detail":"Failed to read request", "status":400, ...}
```

The enum serializes as its lowercase `code`, never the Java constant name, and the
`@JsonCreator` factory parses both forms back leniently. An unrecognized value surfaces as
a normal `400` (the `IllegalArgumentException` from `fromValue` gets wrapped into a
Jackson exception, then into `HttpMessageNotReadableException` by Spring) - not a `500`.

Note this only works because the enum arrives in a **request body**. A `@PathVariable`
of this enum type would be converted by Spring's own `ConversionService` (`Enum.valueOf`
by name), which never looks at `@JsonCreator`/`@JsonValue` at all - that conversion
pipeline is unrelated to Jackson.

## Polymorphism: `@JsonTypeInfo` + `@JsonSubTypes`, both directions

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = EmailNotification.class, name = "email"),
    @JsonSubTypes.Type(value = SmsNotification.class, name = "sms")
})
public sealed interface Notification permits EmailNotification, SmsNotification {}
```

**Response direction** - the server picks the concrete type, Jackson adds the
discriminator:

```
GET /api/jackson/notifications
200 [{"type":"email","to":"demo@example.com","subject":"Welcome"},
     {"type":"sms","phoneNumber":"+905551112233","message":"Your code is 4242"}]
```

**Request direction** - the client sends the discriminator, and Jackson has to resolve it
to a concrete record *before* the controller method runs:

```java
@PostMapping("/notifications/dispatch")
public DispatchResult dispatch(@RequestBody Notification notification) {
    return switch (notification) {
        case EmailNotification email -> new DispatchResult("email", "to " + email.to() + ": " + email.subject());
        case SmsNotification sms -> new DispatchResult("sms", "to " + sms.phoneNumber() + ": " + sms.message());
    };
}
```

```
POST /api/jackson/notifications/dispatch  {"type": "email", "to": "demo@example.com", "subject": "Welcome"}
200 {"channel":"email","summary":"to demo@example.com: Welcome"}

POST /api/jackson/notifications/dispatch  {"type": "sms", "phoneNumber": "+905551112233", "message": "Your code is 4242"}
200 {"channel":"sms","summary":"to +905551112233: Your code is 4242"}

POST /api/jackson/notifications/dispatch  {"type": "push", "token": "abc"}
400 {"detail":"Failed to read request", "status":400, ...}          <-- unrecognized "type"

POST /api/jackson/notifications/dispatch  {"to": "demo@example.com", "subject": "Welcome"}
400 {"detail":"Failed to read request", "status":400, ...}          <-- "type" missing entirely
```

The exhaustive `switch` over the `sealed interface` is what makes this safe: it only
compiles because every `permits`-listed subtype is handled, and `email.to()` /
`sms.phoneNumber()` only become reachable once Jackson has already resolved which record
this actually is - there's no defensive `instanceof` chain or unchecked cast anywhere.

## `@JsonUnwrapped`

```java
public record Contact(@NotBlank String name, @JsonUnwrapped Address address) {}
public record Address(String street, String city, String zipCode) {}
```

```
POST /api/jackson/contacts
{"name": "Grace Hopper", "street": "1 Infinite Loop", "city": "Norfolk", "zipCode": "23504"}

200 {"name":"Grace Hopper","street":"1 Infinite Loop","city":"Norfolk","zipCode":"23504"}
```

`address`'s fields are flattened directly into `Contact`'s JSON on **both** sides - there
is no nested `"address": {...}` object in the request or the response, even though
`Address` is a perfectly normal separate record in the Java model. This works for records
here (not just classes with setters), including on the deserialization side.

## Date and time types: date-only, time-only, date+time, with/without a zone

Real APIs rarely need just one `java.time` shape - sometimes a field is a pure calendar
date with no time-of-day (a birthday, a due date), sometimes it's a bare time-of-day with
no date (a shop's opening time), sometimes it's a full date+time, and sometimes that
date+time also needs to be unambiguous about *which* real moment it refers to. `EventSchedule`
lines all of these up side by side:

```java
public record EventSchedule(
        LocalDate eventDate,                          // date only
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,  // time only, custom format
        LocalDateTime reminderAt,                      // date+time, no zone/offset
        OffsetDateTime publishedAt,                    // date+time, WITH an offset
        Instant createdAt,                              // absolute instant, always UTC
        Duration reminderLeadTime) {}                   // a time span, not a point in time
```

None of this needs the separate `jackson-datatype-jsr310` module Jackson 2 required -
Jackson 3's core databind understands `java.time` out of the box.

```
POST /api/jackson/events
{"eventDate": "2026-09-22", "startTime": "14:30", "reminderAt": "2026-09-22T09:00:00",
 "publishedAt": "2026-09-22T09:00:00+03:00", "createdAt": "2026-09-22T06:00:00Z",
 "reminderLeadTime": "PT2H30M"}

200 {"eventDate":"2026-09-22","startTime":"14:30","reminderAt":"2026-09-22T09:00:00",
     "publishedAt":"2026-09-22T06:00:00Z","createdAt":"2026-09-22T06:00:00Z",
     "reminderLeadTime":"PT2H30M"}
```

| Field | Type | Shape | Notes |
|---|---|---|---|
| `eventDate` | `LocalDate` | `"2026-09-22"` | No time component at all, in or out |
| `startTime` | `LocalTime` | `"14:30"` | Default would be `"14:30:00"`; `@JsonFormat(pattern = "HH:mm")` trims the seconds |
| `reminderAt` | `LocalDateTime` | `"2026-09-22T09:00:00"` | Date+time, deliberately carries no zone/offset either way |
| `publishedAt` | `OffsetDateTime` | `"2026-09-22T06:00:00Z"` | See the gotcha below - notice this is `06:00`, not the `09:00` that was sent |
| `createdAt` | `Instant` | `"2026-09-22T06:00:00Z"` | Always UTC (`Z`); an `Instant` has no other concept of "zone" to begin with |
| `reminderLeadTime` | `Duration` | `"PT2H30M"` | ISO-8601 period, not a raw count of seconds/minutes |

**The gotcha**: `OffsetDateTime` is *not* rendered with its original offset by default.
Sending `09:00:00+03:00` comes back as `06:00:00Z` - Jackson converts to the equivalent
UTC instant and always writes `Z`, discarding the offset the client actually sent. This is
easy to miss because the *value* is still correct (`09:00+03:00` and `06:00Z` are the same
real moment) - what's lost is which local offset the caller originally meant, which
matters if you wanted to show "published at 9am in the author's own timezone" rather than
"published at 6am UTC". Confirmed for both a positive and a negative offset:

```
"publishedAt": "2026-09-22T09:00:00+03:00"  -> "publishedAt":"2026-09-22T06:00:00Z"
"publishedAt": "2026-09-22T09:00:00-05:00"  -> "publishedAt":"2026-09-22T14:00:00Z"
```

Both inputs land on a *different* UTC instant (as they should - `+03:00` and `-05:00` are
8 hours apart), which is what proves this is a genuine timezone conversion and not just a
display quirk. If preserving the caller's original offset matters, you need either a
plain `String`/custom converter (the same technique `CardPaymentRequest.cardNumber` and
`Money` above use for other fields), a `ZonedDateTime` combined with the right Jackson
serialization feature, or to just document that the API always normalizes to UTC and
have callers treat it that way.

## `@RequestParam`/`@PathVariable` dates: `@DateTimeFormat`, not `@JsonFormat`

Everything above governs a JSON **request body** field, which Jackson controls. A query
parameter or path variable is completely different: it's bound by Spring MVC's own
`ConversionService`, using `@DateTimeFormat` from `org.springframework.format.annotation` -
Jackson never sees it at all. This is the same reason `PaymentStatus` above needed a
request body instead of a `@PathVariable` to use its `@JsonCreator`.

The scenario this endpoint simulates: the database column behind `date` is a
`LocalDateTime`, but callers only think in terms of a calendar day, so the controller
accepts a plain date and expands it into the half-open window a `LocalDateTime` query
would actually need:

```java
@GetMapping("/events/day-range")
public DayRange dayRange(@RequestParam @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate date) {
    LocalDateTime start = date.atStartOfDay();
    return new DayRange(date, start, start.plusDays(1));
}
```

```
GET /api/jackson/events/day-range?date=03/04/2026
200 {"date":"2026-04-03","rangeStart":"2026-04-03T00:00:00","rangeEnd":"2026-04-04T00:00:00"}
```

`03/04/2026` resolves to **April 3rd**, not March 4th - proof the `dd/MM/yyyy` pattern is
genuinely driving the parse, not some other default taking over.

**The gotcha**: the custom pattern does not *exclude* ISO input. A plain
`date=2026-09-22` (dashes, not the configured slash pattern) is accepted too:

```
GET /api/jackson/events/day-range?date=2026-09-22
200 {"date":"2026-09-22", ...}                    <-- still 200, despite not matching dd/MM/yyyy
```

This is confirmed straight from the Spring source
(`org.springframework.format.datetime.standard.TemporalAccessorParser.parse`): when the
custom pattern fails to parse **and** `@DateTimeFormat.fallbackPatterns()` is left at its
default (empty), the parser silently retries with the type's plain ISO parser
(`LocalDate.parse(text)`) before giving up. In other words, `@DateTimeFormat(pattern =
"dd/MM/yyyy")` really means *"dd/MM/yyyy, or ISO-8601"* - never just the one pattern you
wrote - unless you explicitly set `fallbackPatterns` to something else. What *does* still
get rejected is input that matches neither:

```
GET /api/jackson/events/day-range?date=2026/09/22    -> 400 (day "2026" is impossible under dd/MM/yyyy, and it isn't ISO either)
GET /api/jackson/events/day-range?date=not-a-date     -> 400
```

If an endpoint genuinely must accept exactly one format and nothing else (not even ISO),
`@DateTimeFormat` alone can't do it - that needs a dedicated `Converter`/`Formatter` bean
registered directly, bypassing the annotation's built-in fallback entirely.

The same mechanism applies to a full `LocalDateTime` query parameter, not just a bare
`LocalDate`:

```java
@GetMapping("/events/at")
public LocalDateTime eventAt(
        @RequestParam @DateTimeFormat(pattern = "dd/MM/yyyy HH:mm") LocalDateTime at) {
    return at;
}
```

```
GET /api/jackson/events/at?at=22/09/2026 14:30
200 "2026-09-22T14:30:00"

GET /api/jackson/events/at?at=03/04/2026 15:45     (day/month check: April 3rd, not March 4th)
200 "2026-04-03T15:45:00"

GET /api/jackson/events/at?at=2026-09-22T14:30:00   (the same ISO-fallback gotcha, confirmed here too)
200 "2026-09-22T14:30:00"

GET /api/jackson/events/at?at=nope
400 {"detail":"Failed to convert 'at' with value: 'nope'", "status":400, ...}
```

This endpoint makes the two-mechanism split especially visible: `@DateTimeFormat` (Spring's
`ConversionService`) controls parsing the **input** query string, while the bare ISO shape
of the response is Jackson's ordinary default `LocalDateTime` rendering on the way
**out** - the same rendering `EventSchedule.reminderAt` uses above, just reached from a
completely different starting point.

## Malformed JSON and validation failures

`spring.mvc.problemdetails.enabled=true` (in `application.yaml`) means Spring's default
exception handling for both is already an RFC 9457 `ProblemDetail`, with no custom
`@RestControllerAdvice` needed anywhere in this module:

```
POST /api/jackson/profile  {bad json
400 {"detail":"Failed to read request","instance":"/api/jackson/profile","status":400,"title":"Bad Request"}

POST /api/jackson/login  {"username": "neo", "password": ""}
400 {"detail":"Invalid request content.","instance":"/api/jackson/login","status":400,"title":"Bad Request"}
```

The first is `HttpMessageNotReadableException` (Jackson couldn't even parse the body); the
second is a Bean Validation failure (`@NotBlank` on `password`) - both land as a clean
`400`, never a `500`, and never Spring's default whitelabel error page.

## Jackson 3 package layout

This project runs on Jackson 3 (via `spring-boot-starter-jackson`), which kept the
classic annotation package but renamed/relocated the databind API:

| Concept | Package |
|---|---|
| `@JsonProperty`, `@JsonFormat`, `@JsonAlias`, `@JsonIgnoreProperties`, `@JsonValue`, `@JsonCreator`, `@JsonTypeInfo`, `@JsonSubTypes`, `@JsonUnwrapped` | `com.fasterxml.jackson.annotation.*` (unchanged from Jackson 2) |
| `@JsonSerialize`, `@JsonDeserialize`, `@JsonNaming` | `tools.jackson.databind.annotation.*` (moved) |
| `ObjectMapper`, `JsonNode`, custom serializer base class (was `JsonSerializer`) | `tools.jackson.databind.*` - the base class for a custom serializer is now `ValueSerializer<T>`, and a custom deserializer extends `ValueDeserializer<T>` |
| `SimpleModule` | `tools.jackson.databind.module.SimpleModule` |
| The Spring Boot customizer bean to register a module globally | `org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer`, called with a `tools.jackson.databind.json.JsonMapper.Builder` |

Getting an import from the wrong package (e.g. `com.fasterxml.jackson.databind.ObjectMapper`
instead of `tools.jackson.databind.ObjectMapper`) is the most common mistake when working
in this version - the class often doesn't exist under the old package at all, so it fails
at compile time rather than silently misbehaving.

## Tests

```bash
./mvnw test
```

28 tests, every one a real HTTP round trip through `RestTestClient` (or, for `@JsonIgnore`'s
input side, a direct read through the injected `ObjectMapper`) - no mocking of Jackson, no
asserting against hand-written JSON strings without sending them through the app first:

- Every technique above (`non_null`, `READ_ONLY`/`WRITE_ONLY`, `@JsonIgnore`, `@JsonAlias`,
  the `@JsonIgnoreProperties` gotcha, local and global custom converters - including the
  uppercase and partial-mask ones - enum round-trip and its `400` on an unknown value,
  polymorphism in both directions, `@JsonUnwrapped`, `fail-on-empty-beans`)
- Every `java.time` shape on `EventSchedule`, including the `OffsetDateTime`-to-UTC
  normalization gotcha proven with both a positive and a negative offset
- `@DateTimeFormat` on both a `LocalDate` and a `LocalDateTime` `@RequestParam`: the custom
  pattern, the day/month-ordering proof, the silent ISO fallback, and the `400` when
  neither format matches
- Malformed JSON and a Bean Validation failure both land as `400`, never `500`
- `JacksonApplicationTests` - context loads

## Deliberate simplifications

- **No persistence.** `/api/jackson/profile`'s `id` is an in-memory `AtomicLong`, not a
  database sequence - the point here is the JSON shape, not storage.
- **`PaymentStatus.fromValue` throws `IllegalArgumentException` for an unknown code**,
  relying on Jackson/Spring's default wrapping into a `400`, rather than a dedicated
  business exception - fine for a demo endpoint, but a real domain object would likely
  want a named exception type callers can catch specifically.
- **No dedicated "strict" `ObjectMapper`.** The `@JsonIgnoreProperties` gotcha above is
  documented and tested as-is; building the dedicated message converter that *would* make
  one endpoint strict again is a real technique but out of scope for this module.
