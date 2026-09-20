# Spring Boot Validation Example Project

A teaching catalog that demonstrates every layer of validation in Spring Boot
through a single `User` domain. Each endpoint demonstrates **exactly one technique** in isolation.

Spring Boot 4.1.1 · Java 25 · H2 (in-memory)

## Running

```bash
cd validation
./mvnw spring-boot:run
```

H2 console: http://localhost:8080/h2-console
(JDBC URL: `jdbc:h2:mem:validationdb`, user: `sa`, password blank)
This is fine for a local demo with an in-memory database; never expose the H2
console like this in a real deployment.

In the schema you can see the `uk_user_email` / `uk_user_tckn` unique constraints
and the `users_age_check` CHECK constraint.

## Endpoint catalog

| Endpoint | Technique demonstrated | Exception triggered | Status |
|---|---|---|---|
| `POST /api/basic/users` | Built-in jakarta annotations (`@NotBlank`, `@Email`, `@Size`, `@Min`, `@Past`, `@Pattern`) | `MethodArgumentNotValidException` | 400 |
| `POST /api/custom/users` | Custom field constraints: `@TcKimlikNo`, `@StrongPassword`, `@EnumValue` | `MethodArgumentNotValidException` | 400 |
| `POST /api/cross-field/users` | Class-level constraints: `@PasswordsMatch`, `@ValidDateRange` | `MethodArgumentNotValidException` | 400 |
| `POST /api/groups/users` | `@Validated(OnCreate.class)` — id forbidden, password required | `MethodArgumentNotValidException` | 400 |
| `PUT /api/groups/users` | `@Validated(OnUpdate.class)` — id required, password optional | `MethodArgumentNotValidException` | 400 |
| `GET /api/params/users/{id}` | `@PathVariable @Min` | `HandlerMethodValidationException` | 400 |
| `GET /api/params/users/search?email=` | `@RequestParam @Email` | `HandlerMethodValidationException` | 400 |
| `POST /api/nested/users` | Nested `@Valid` + `List<@Valid ...>` | `MethodArgumentNotValidException` | 400 |
| `POST /api/nested/users/bulk` | Validating every element of a collection | `MethodArgumentNotValidException` | 400 |
| `POST /api/users` | Real persistence + `@UniqueEmail` | `MethodArgumentNotValidException` | 201 / 400 |
| `POST /api/users/unsafe` | No application-layer guards (neither `@UniqueEmail` nor `@Min(18)`); DB constraints kick in | `DataIntegrityViolationException` | 201 / 409 |
| `GET /api/users/{id}` | Service-layer `@Validated` + business rule | `ConstraintViolationException`, `UserNotFoundException` | 200 / 400 / 404 |

## Error format

Every validation error is RFC 9457 `ProblemDetail` + `errors[]`:

```json
{
  "title": "Dogrulama Hatasi",
  "status": 400,
  "detail": "Istek dogrulamadan gecemedi",
  "instance": "/api/basic/users",
  "errors": [
    {
      "field": "email",
      "code": "Email",
      "message": "Gecerli bir e-posta adresi giriniz",
      "rejectedValue": "gecersiz-eposta"
    }
  ]
}
```

A handful of framework-native errors — wrong HTTP method, unsupported media type,
an unmatched route — are not overridden and return a plain `ProblemDetail` without
`errors[]`; this project's endpoint catalogue never exercises those paths on purpose.

The `type` field never appears in the response body: `ProblemDetail.type` defaults
to `null` (not a string value like `"about:blank"`), and because the Jackson mixin
(`ProblemDetailJacksonMixin`) carries `@JsonInclude(NON_EMPTY)` at the class level,
null fields are dropped during serialization. Since `ProblemDetailFactory` never
calls `setType(...)` anywhere, `type` always stays null and is always omitted — if
`URI.create("about:blank")` were set explicitly, it would be written, because it
wouldn't be null. The example above is an exact copy of a real response.

`rejectedValue` for fields containing "password" is masked as `***`.

## Language support

Send an `Accept-Language: en` header and messages come back in English. Without the
header, they come back in Turkish. The message texts live in
`src/main/resources/messages.properties` and `messages_en.properties`; annotations
bind to them via keys such as `{validation.user.email.invalid}`.

## Two different exceptions: parameter or service?

Method parameter validation can go through two separate paths as of Spring 6.1+,
and the location of `@Validated` determines which one runs:

| Where | Is `@Validated` present | Who validates | Exception thrown |
|---|---|---|---|
| Controller parameters (`/api/params/...`) | **No** | Spring MVC's built-in parameter validation | `HandlerMethodValidationException` |
| Service method parameters (`UserService`) | **Yes** (on the class) | AOP proxy (`MethodValidationPostProcessor`) | `ConstraintViolationException` |

Important pitfall: if you put `@Validated` on the controller class, Spring MVC
**disables** its own parameter validation (to avoid double validation), and the
work shifts to the AOP proxy — meaning you get a `ConstraintViolationException`
instead of the `HandlerMethodValidationException` you'd expect. Constrained
parameters alone are sufficient in a controller; `@Validated` is not needed there.

Since `GlobalExceptionHandler` converts both exceptions into the same
`ProblemDetail` + `errors[]` shape, the client never sees the difference.

## Three layers: which one, when?

| Layer | Where | When it kicks in | What it returns |
|---|---|---|---|
| **DTO validation** | `@Valid @RequestBody` | On every request, before business logic | 400 + field-level error |
| **Service validation** | `@Validated` class + `@Valid`/`@Min` parameters | Wherever the service is called from (controller, scheduled job, message consumer) | 400 |
| **Database constraints** | `@Column`, `@UniqueConstraint`, `@Check` | At insert/update time, the last line of defense | 409 |

### Why all three?

`@UniqueEmail` gives the user a friendly 400 with a field-level message — but it is
**not atomic**: two concurrent requests can both pass the check at the same time
and both end up persisted. The database's `uk_user_email` constraint stops
whichever request loses that race and produces a 409. `POST /api/users` and
`POST /api/users/unsafe` show this difference side by side.

Similarly, the `age >= 18` rule lives in two places: `@Min(18)` on the DTO (a
meaningful error for the user) and a CHECK constraint in the database (a
constraint that protects the data no matter how it's written).

### Jakarta annotations on the entity

The `@NotBlank`/`@Email`/`@Size` annotations on the `User` entity do the
following: Hibernate runs them before insert/update; if there's a violation, a
`jakarta.validation.ConstraintViolationException` is thrown. Column length, on the
other hand, is set separately and explicitly with `@Column(length = ...)` — on the
`fullName` field, `@Size(max = 100)` and `@Column(length = 100)` carry the same
value, so the generated `varchar(100)` DDL alone can't tell you which one is the
source of truth; the project deliberately keeps the two consistent with each
other.

The `age` field intentionally has **no** bean-validation constraint: if it did,
Hibernate would catch it before insert, the database CHECK constraint would never
fire, and the example would lose its point.

## Mapping database constraint names

When a `DataIntegrityViolationException` is caught, finding out which constraint
was violated first tries Hibernate's `ConstraintViolationException.getConstraintName()`
method; only if that returns empty does it search the database error text for the
constraint name. The structural approach is preferred because the error text
varies by database and driver locale.

All three mappings are proven by tests (not assumed):

| Constraint | How to trigger it | Returned message |
|---|---|---|
| `uk_user_email` | A second registration to `/api/users/unsafe` with the same email | Bu e-posta adresi zaten kayitli |
| `uk_user_tckn` | A second registration to `/api/users/unsafe` with the same national ID number | Bu T.C. kimlik numarasi zaten kayitli |
| `users_age_check` | Registering with `age: 15` at `/api/users/unsafe` | Yas en az 18 olmalidir |

## Deliberate simplifications

These are intentional; a real product would do them differently:

- **A CHECK violation also returns 409.** `handleDataIntegrityViolation` responds
  to every data integrity violation with `409 Cakisma`. A range error is
  conceptually really a 400. `POST /api/users` never takes this path at all — the
  `@Min(18)` on `CreateUserRequest` catches the age before it reaches the DB. But
  `POST /api/users/unsafe` exists exactly to demonstrate this: `UnsafeCreateUserRequest`
  has no `@Min(18)`, so `age: 15` hits the DB CHECK constraint directly and
  returns 409 (this is the `users_age_check` row in the mapping table above). In
  production, `messageKeyFor` would return an HTTP status alongside the key.
- **`UserService.toEntity` takes positional parameters.** There are three
  adjacent `String` parameters; swapping two of them at the call site still
  compiles. In production, you'd use a separate overload per request type, or a
  mapper.
- **`@UniqueEmail` is not atomic.** There's a race between the query and the
  insert; that's why the database unique constraint is kept as the guarantee. The
  friendly message comes from the application layer, the guarantee comes from the
  database.

## Tests

```bash
./mvnw test
```

- Custom validators: pure `Validator` unit tests (no Spring context)
- Controllers: `@WebMvcTest` + MockMvc, error format and HTTP status
- Database constraints: `@DataJpaTest`
- Service + full flow: `@SpringBootTest`
