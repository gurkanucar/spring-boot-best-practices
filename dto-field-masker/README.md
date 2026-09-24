# Spring Boot DTO Field Masker

A small annotation, `@MaskData`, that masks String fields of a response DTO while Jackson
writes the JSON. Sensitive values (account numbers, id numbers, card numbers) stay intact in
your domain objects and are hidden only in the API output, with the masking rule declared right
on the field:

```java
public record AccountResponse(
        String accountName,

        @MaskData(replaceChar = "*", maskingOption = MaskingOption.LAST_X_CHARS_MASKED, value = 10)
        String accountNumber) {   // "123456789012345" -> "12345**********"
}
```

Spring Boot 4.1.1 · Java 25 · Jackson 3 (`tools.jackson.*` for the serializer API,
`com.fasterxml.jackson.annotation.*` for the annotations, which Jackson 3 kept)

## Running

```bash
cd dto-field-masker
./mvnw spring-boot:run
```

The app starts on port `8091`.

```bash
curl localhost:8091/api/customers        # masked customers with their accounts
curl localhost:8091/api/customers/2      # a single customer
curl localhost:8091/api/showcase         # every option applied to the same input
```

Response of `GET /api/customers`:

```json
[
  {
    "name": "Gurkan",
    "idNumber": "xxxx56789",
    "accounts": [
      { "accountName": "account1", "accountNumber": "12345**********" },
      { "accountName": "account2", "accountNumber": "123456**********" }
    ]
  },
  {
    "name": "Mehmet",
    "idNumber": "xxxx54321",
    "accounts": [
      { "accountName": "account3", "accountNumber": "12345678**********" },
      { "accountName": "account4", "accountNumber": "123456789**********" }
    ]
  }
]
```

The real values are `123456789` / `987654321` for the id numbers and 15 to 19 digit account
numbers; the API never returns them.

## Package layout

```
com.gucardev.dtofieldmasker
├── masking/        MaskData (annotation), MaskDataSerializer (Jackson), Masker (the algorithm)
├── customer/       Customer + Account (domain), repository, service, controller, mapper
│   └── dto/        CustomerResponse, AccountResponse, MaskIdNumber (custom annotation)
└── showcase/       ShowcaseController
    └── dto/        ShowcaseResponse
```

`masking/` is the reusable part and has no dependency on the demo features; `customer/` and
`showcase/` are examples of using it.

## The annotation

```java
@MaskData(
    int value() default 3,                       // how many characters the option refers to
    String replaceChar() default "x",            // what each masked character becomes
    MaskingOption maskingOption() default LAST_X_CHARS_CLEAR)

enum MaskingOption {
    FIRST_X_CHARS_CLEAR,    // first X readable, rest masked
    FIRST_X_CHARS_MASKED,   // first X masked, rest readable
    LAST_X_CHARS_CLEAR,     // last X readable, rest masked
    LAST_X_CHARS_MASKED     // last X masked, rest readable
}
```

`GET /api/showcase` applies each option to the same input, `1234567890123456`:

| Field | Annotation | Output |
|---|---|---|
| `original` | none | `1234567890123456` |
| `defaults` | `@MaskData` | `xxxxxxxxxxxxx456` |
| `firstXClear` | `FIRST_X_CHARS_CLEAR`, 4 | `1234xxxxxxxxxxxx` |
| `firstXMasked` | `FIRST_X_CHARS_MASKED`, 4 | `xxxx567890123456` |
| `lastXClear` | `LAST_X_CHARS_CLEAR`, 4 | `xxxxxxxxxxxx3456` |
| `lastXMasked` | `LAST_X_CHARS_MASKED`, 4 | `123456789012xxxx` |
| `customReplaceChar` | `replaceChar = "*"`, `LAST_X_CHARS_CLEAR`, 4 | `************3456` |
| `multiCharReplacement` | `replaceChar = "[#]"`, `FIRST_X_CHARS_CLEAR`, 12 | `123456789012[#][#][#][#]` |
| `shortValueFailsClosed` | `LAST_X_CHARS_CLEAR`, 4, on the input `abc` | `xxx` |

### Behavior worth knowing

- **Fail-closed for `*_CLEAR` options.** If the number of readable characters covers the whole
  value (`LAST_X_CHARS_CLEAR` with 4 on `abc`), the *entire* value is masked instead of
  revealed. A masker that shows a short value in full because of its configuration is worse
  than one that hides it. `*_MASKED` options with a large count naturally mask everything.
- **Characters are Unicode code points**, not UTF-16 `char`s, so an emoji counts as one
  character and is never cut in half.
- **`replaceChar` may be longer than one character** (`"[#]"`); it is repeated once per masked
  character, so the output grows accordingly.
- **`null` stays `null`.** Jackson never calls a value serializer for null; an empty string
  stays empty.
- **Misconfiguration fails on the first serialization** with a message naming the property:
  `@MaskData` on a non-String property, or a negative `value`. It does not silently mask wrongly.

## How it works

```
@MaskData ──(@JacksonAnnotationsInside)──> @JsonSerialize(using = MaskDataSerializer.class)
```

1. `@MaskData` is meta-annotated with `@JsonSerialize(using = MaskDataSerializer.class)`, so
   Jackson uses that serializer for any property carrying the annotation. No module
   registration and no `ObjectMapper` configuration is needed.
2. `@JacksonAnnotationsInside` is what lets Jackson treat those meta-annotations as if they
   were written on the field itself.
3. `MaskDataSerializer` overrides `createContextual(...)`. Jackson calls it once per annotated
   property, passing the property so the serializer can read that property's `@MaskData`
   settings and return a copy configured with them. Jackson caches the copy, so the annotation
   is read once per property, not once per request.
4. `serialize(...)` then just delegates to `Masker.mask(...)`, the pure algorithm. It has no
   Jackson dependency, so it is unit-tested on its own and can be reused elsewhere (for
   example to mask a value before logging it).

### Your own shortcut annotations

Because `@MaskData` is itself a Jackson meta-annotation, you can bundle a rule under a
domain-specific name and reuse it (`customer/dto/MaskIdNumber`):

```java
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@JacksonAnnotationsInside
@MaskData(maskingOption = MaskingOption.FIRST_X_CHARS_MASKED, value = 4)
public @interface MaskIdNumber {
}

public record CustomerResponse(String name, @MaskIdNumber String idNumber, ...) {}
```

Changing the rule in one place updates every DTO that uses it.

## Limitations and pitfalls

- **Masking happens while writing JSON, nothing else.** `toString()`, `equals()`, logging of
  the DTO object, and the domain objects all still hold the real value. Do not log a response
  DTO with `log.info("{}", dto)`, since a record's generated `toString()` prints the raw
  value. Log a masked string (`Masker.mask(...)`) or override `toString()` instead.
- **It applies to every JSON the application's `ObjectMapper` writes**, not only HTTP
  responses: a cache, a message queue payload or an audit record serialized with the same
  mapper would be masked too, and the original would be lost. Put `@MaskData` only on
  response DTOs, never on objects that are stored and read back.
- **Serialization only.** A client that reads a masked value and posts it back sends the
  masked text; the annotation does nothing on deserialization.
- **String properties only** (any `CharSequence`). Numbers would change JSON type if masked,
  so convert to a String in the DTO first.
- **Masking is not access control.** It reduces exposure in the API output; the endpoint itself
  still needs authorization, and an attacker who can call it repeatedly still sees the visible
  part of every value.

## Tests

```bash
./mvnw test
```

- `MaskerTest`: every option, zero and oversized counts, the fail-closed rule, empty input,
  multi-character replacement, code points (emoji), negative count.
- `MaskDataSerializerTest`: the Jackson wiring without Spring: annotation defaults, per-property
  settings, nesting and collections, nulls, POJO fields and getters, and the fail-fast errors.
- `CustomerApiTest`: the full stack with MockMvc: the exact JSON above, single customer, the
  raw values never appearing in the response, domain objects unchanged, and the showcase table.
