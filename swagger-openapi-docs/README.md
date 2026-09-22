# Spring Boot Swagger / OpenAPI Documentation Example

A teaching catalog for springdoc-openapi / Swagger v3 annotations: per-field `@Schema`
examples, named request-body `@ExampleObject` variants, documented response codes,
`@ParameterObject` flattening, and the two ways to hide something from the generated
doc without taking the endpoint itself offline.

A single `Product` resource (in-memory, no database) is documented every way that
matters, and `OpenApiDocsTest` replays the generated `/v3/api-docs` document against
the running app - so a broken or misleading annotation fails a test, not just a
Swagger UI screenshot.

Spring Boot 4.1.1 · Java 25 · springdoc-openapi 3.1.1

## Running

```bash
cd swagger-openapi-docs
./mvnw spring-boot:run
```

The app starts on port `8086`.

- Swagger UI: http://localhost:8086/swagger-ui.html
- Raw OpenAPI document: http://localhost:8086/v3/api-docs

## Endpoint catalog

| Endpoint | Technique demonstrated |
|---|---|
| `GET /api/products` | `@ParameterObject` (pagination flattened into `page`/`size` query params) + a `@Parameter(hidden = true)` header |
| `GET /api/products/slow` | A plain documented 200, useful for trying Swagger UI's "display request duration" |
| `GET /api/products/exception` | A documented `500` with a real exception behind it |
| `GET /api/products/{id}` | Path variable `@Parameter` with an example; documented `400`/`404` |
| `POST /api/products` | `@Schema`-annotated request/response DTOs; two named `@ExampleObject` request bodies (valid / fails validation) |
| `POST /api/products/internal/reset` *(hidden)* | `@Hidden` - absent from the doc entirely, still callable |

## springdoc configuration (`application.yaml`)

```yaml
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
    tags-sorter: alpha
    operations-sorter: method
    display-request-duration: true
```

- **`api-docs.path`** — where the raw OpenAPI JSON is served. Moving it off the
  `/v3/api-docs` default (e.g. behind an internal-only prefix) is a common way to keep
  the machine-readable document away from casual discovery without fully disabling it.
- **`swagger-ui.path`** — where the interactive UI is served; independent of
  `api-docs.path`, since the UI is just a static page that then calls the JSON endpoint.
- **`tags-sorter: alpha`** — groups in Swagger UI (here, just `Products`) list
  alphabetically instead of in declaration order.
- **`operations-sorter: method`** — within a tag, operations are ordered by HTTP
  method (`GET` before `POST`, etc.) instead of by the order they were declared in the
  controller.
- **`display-request-duration: true`** — Swagger UI's "Try it out" shows how long the
  call actually took. `GET /api/products/slow`'s random 200-800 ms delay exists
  specifically to make this setting visible instead of always reading ~0 ms.
- **`api-docs.enabled` / `swagger-ui.enabled`** — not set here (both default to `true`);
  see "Hiding the whole document in production" below for how `application-prod.yaml`
  flips them off. These are the profile-wide, all-or-nothing switches - contrast with
  `@Hidden`/`@Parameter(hidden = true)` above, which hide one operation or parameter
  while the document (and every other endpoint) stays fully served.

## `@ParameterObject`: flattening a request DTO into query parameters

`GET /api/products` takes pagination as one object parameter in code, but the OpenAPI
document (and Swagger UI) shows two independent query parameters, `page` and `size`:

```java
@GetMapping
public ApiResult<List<ProductResponse>> list(
        @ParameterObject ProductPageRequest query, ...) { ... }
```

```java
@Schema(name = "ProductPageRequest", description = "Pagination request, flattened into query parameters.")
public record ProductPageRequest(
        @Schema(description = "Zero-based page index.", example = "0", defaultValue = "0") Integer page,
        @Schema(description = "Page size.", example = "20", defaultValue = "20") Integer size) { ... }
```

Real captured `/v3/api-docs` output for this operation - note there is no
`ProductPageRequest` object parameter, just two scalars, each carrying the
`@Schema`'s own `description`/`example`/`defaultValue`:

```json
{
  "name": "page",
  "in": "query",
  "description": "Zero-based page index.",
  "required": false,
  "schema": { "type": "integer", "format": "int32", "default": 0, "example": 0 }
},
{
  "name": "size",
  "in": "query",
  "description": "Page size.",
  "required": false,
  "schema": { "type": "integer", "format": "int32", "default": 20, "example": 20 }
}
```

Without `@ParameterObject`, springdoc would either reject `ProductPageRequest` as an
unsupported "complex" query parameter or document it as a single opaque object - the
annotation is what tells springdoc "treat every accessor on this type as its own
`@Parameter`". This is the same mechanism the original project used for Spring Data's
`Pageable` (via springdoc's `pageable-converter`); here it is applied to a plain
project-owned record instead, so no `spring-data-commons` dependency is needed just to
document a page/size pair.

## Hiding something from the doc, two ways

**`@Parameter(hidden = true)`** hides one parameter while the endpoint still accepts
it. `GET /api/products` reads an optional `X-Internal-Client` header meant for
service-to-service callers - it is functional, but a public API consumer reading the
docs never learns it exists:

```java
@Parameter(hidden = true) @RequestHeader(value = "X-Internal-Client", required = false)
        String internalClient
```

Confirmed for real: `/v3/api-docs` lists exactly two parameters for this operation
(`page`, `size`) - `X-Internal-Client` never appears - while a request that actually
sends the header still returns `200 OK`.

**`@Hidden`** hides an entire operation. `POST /api/products/internal/reset` (a
test/demo utility that restores the seed data) carries `@Hidden` at the method level:

```java
@Hidden
@Operation(summary = "Reset the in-memory catalog", ...)
@PostMapping("/internal/reset")
public ApiResult<Void> reset() { ... }
```

Confirmed for real: the path `/api/products/internal/reset` is **absent** from
`/v3/api-docs`'s `paths` object and never appears in Swagger UI, but
`curl -X POST http://localhost:8086/api/products/internal/reset` still returns
`200 {"success":true,"message":"Catalog reset"}` - hiding documentation is not the
same thing as disabling a route.

This is a different axis from `springdoc.api-docs.enabled=false` in
`application-prod.yaml` (see below): that switch is profile-wide and turns off the
*entire* document and UI; `@Hidden`/`@Parameter(hidden = true)` are per-operation and
per-parameter, and only affect the doc - the route keeps working either way. A fourth,
unused-here variant is `@Schema(hidden = true)`, which hides one *property* of a
request/response body from its schema the same way `@Parameter(hidden = true)` hides
a parameter.

## Request/response examples and schemas

`ProductRequest`/`ProductResponse` carry a class-level `@Schema(example = "...")`
(the whole-object example Swagger UI shows first) plus a per-field `@Schema` on every
component. Real captured schema for `ProductRequest.sku` - `@NotBlank` contributes
`minLength`, `@Size(max = 64)` contributes `maxLength`, the rest comes from `@Schema`:

```json
{
  "type": "string",
  "description": "Stock keeping unit. Unique across products; a repeat returns 409.",
  "example": "SKU-0042",
  "maxLength": 64,
  "minLength": 0
}
```

`POST /api/products` additionally documents its request body with two named
`@ExampleObject` variants instead of one:

```java
examples = {
    @ExampleObject(name = "valid", summary = "A product that will be created", value = "..."),
    @ExampleObject(name = "invalid", summary = "Fails validation, returns 400", value = "...")
}
```

Both are replayed against the live endpoint by
`documentedRequestExamplesProduceDocumentedStatuses()`: the "valid" example must
produce `201`, and "invalid" must produce a status that `@ApiResponses` on the method
actually documents (`400`). A `@ExampleObject` that no longer matches what the
controller does is a test failure, not a stale screenshot.

## The `ApiResult<T>` envelope, generated too

Every response - success or error - is wrapped in `ApiResult<T>`
(`success`/`errorCode`/`message`/`data`/`page`/`errors`). springdoc generates a
distinct schema per generic instantiation actually used, e.g. `ApiResultProductResponse`
for `ApiResult<ProductResponse>`, so the documented response body always matches the
real envelope shape - there's no hand-maintained "generic wrapper" schema to drift out
of sync.

## Hiding the whole document in production

```yaml
# application-prod.yaml
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

```yaml
# application-test.yaml
springdoc:
  api-docs:
    enabled: true
  swagger-ui:
    enabled: true
```

With no profile active (or `test`), both endpoints work normally. With `prod` active,
both `GET /v3/api-docs` and `GET /swagger-ui.html` return `404` - confirmed by
`SwaggerDisabledInProdTest` - while every `/api/products/**` endpoint keeps working
exactly as before. The one subtlety this required: `GlobalExceptionHandler`'s
catch-all must re-throw any exception that already carries its own HTTP status (like
the `NoResourceFoundException` Spring throws for a disabled/unmapped route), or a
disabled doc turns into a `500` instead of the correct `404`.

## Tests

```bash
./mvnw test
```

10 tests, all against a real running server via `RestTestClient`:

- `OpenApiDocsTest` (7) — every documented path/schema/response-code is real; the two
  named request-body examples produce their documented statuses; the two hidden things
  (`@Hidden` operation, `@Parameter(hidden = true)` header) are absent from the doc
  while remaining callable; the documented `page`/`size` query parameters are actually
  accepted
- `SwaggerDisabledInProdTest` (2) — `prod` profile turns off both `/v3/api-docs` and
  `/swagger-ui.html`
- `SwaggerOpenapiDocsApplicationTests` (1) — context loads

## Deliberate simplifications

- **No persistence.** `ProductRepository` is an in-memory `ConcurrentHashMap`, seeded
  with three products on startup (and by `/internal/reset`) - the point of this module
  is the documentation layer, not a real data store.
- **No auth enforcement.** `OpenApiConfig` documents a `bearerAuth` security scheme for
  reference, but no endpoint here actually requires it.
- **`GlobalExceptionHandler` skips i18n.** Messages are plain strings, not resolved
  through a `MessageSource`/locale - not needed to demonstrate the OpenAPI annotations.
