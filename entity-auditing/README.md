# Spring Boot Entity Auditing with Hibernate Envers

Keeps the **complete change history** of entities: every insert, update and delete, who made
it, when, which fields changed, and what the whole entity looked like at that moment. No
auditing code in the services: annotate the entity with `@Audited` and Hibernate Envers writes
the history in the same transaction as the change.

The example also uses **Spring Data JPA auditing** (`createdBy`, `lastModifiedAt`, ...) and
shows how the two complement each other.

Spring Boot 4.1.1 · Java 25 · Hibernate 7.4 + Envers · Spring Data Envers · H2

## Running

```bash
cd entity-auditing
./mvnw spring-boot:run
```

Port `8094`. The H2 console is at `http://localhost:8094/h2-console` (JDBC URL
`jdbc:h2:mem:auditingdb`, user `sa`): look at `product_history` and `revision_info`.

Who made a change is taken from the `X-User` header (**demo only**, see
[Who made the change](#who-made-the-change)).

## A complete example: request by request

Every response below is real output from this scenario.

```bash
# rev 1: admin creates a category
curl -X POST localhost:8094/api/categories -H 'Content-Type: application/json' -H 'X-User: admin' \
     -d '{"name":"Peripherals"}'

# rev 2: alice creates a product
curl -X POST localhost:8094/api/products -H 'Content-Type: application/json' -H 'X-User: alice' \
     -d '{"name":"Keyboard","price":49.90,"stock":10,"categoryId":1}'

# rev 3: bob lowers the price
curl -X PUT localhost:8094/api/products/1 -H 'Content-Type: application/json' -H 'X-User: bob' \
     -d '{"name":"Keyboard","price":39.90,"stock":10,"categoryId":1}'

# rev 4: admin renames the category (a change to the category, not to the product)
curl -X PUT localhost:8094/api/categories/1 -H 'Content-Type: application/json' -H 'X-User: admin' \
     -d '{"name":"Input Devices"}'

# rev 5: bob changes name and stock in one request
curl -X PUT localhost:8094/api/products/1 -H 'Content-Type: application/json' -H 'X-User: bob' \
     -d '{"name":"Mechanical Keyboard","price":39.90,"stock":7,"categoryId":1}'
```

### Current state: `GET /api/products/1`

The row itself only knows the **first and the last** change (Spring Data auditing columns):

```json
{
  "id": 1,
  "name": "Mechanical Keyboard",
  "price": 39.90,
  "stock": 7,
  "categoryName": "Input Devices",
  "createdAt": "2026-09-24T16:16:16.546239Z",
  "createdBy": "alice",
  "lastModifiedAt": "2026-09-24T16:16:18.725588Z",
  "lastModifiedBy": "bob"
}
```

### Full history: `GET /api/products/1/history`

Envers knows **everything in between**. This is the response at this point of the scenario
(after revision 5), when the product has not been deleted yet. The same call after the
deletion ends with a `DELETED` entry; see [Deleted products](#deleted-products).

```json
[
  {
    "revision": 2,
    "type": "CREATED",
    "at": "2026-09-24T16:16:16.548Z",
    "changedBy": "alice",
    "changedFields": [],
    "state": { "id": 1, "name": "Keyboard", "price": 49.90, "stock": 10,
               "categoryId": 1, "categoryName": "Peripherals" }
  },
  {
    "revision": 3,
    "type": "UPDATED",
    "at": "2026-09-24T16:16:17.623Z",
    "changedBy": "bob",
    "changedFields": ["price"],
    "state": { "id": 1, "name": "Keyboard", "price": 39.90, "stock": 10,
               "categoryId": 1, "categoryName": "Peripherals" }
  },
  {
    "revision": 5,
    "type": "UPDATED",
    "at": "2026-09-24T16:16:18.726Z",
    "changedBy": "bob",
    "changedFields": ["name", "stock"],
    "state": { "id": 1, "name": "Mechanical Keyboard", "price": 39.90, "stock": 7,
               "categoryId": 1, "categoryName": "Input Devices" }
  }
]
```

How to read it:

- **`revision`** is a global counter shared by all audited entities, one per transaction.
  Revisions 1 and 4 are missing here because they changed only the category.
- **`state`** is the whole product *after* that revision, not just a diff.
- **`changedFields`** comes from the `*_mod` flag columns (`withModifiedFlag = true`). It is
  empty for CREATED and DELETED, where the whole entity changed.
- **`categoryName`** is the category **as it was at that revision**: revisions 2 and 3 show
  `Peripherals`, revision 5 shows `Input Devices`. Renaming a category never rewrites the
  product's past.

### Only the price changes: `GET /api/products/1/price-history`

Revision 5 changed name and stock but not the price, so it is not listed:

```json
[
  { "revision": 2, "at": "2026-09-24T16:16:16.548Z", "changedBy": "alice", "price": 49.90 },
  { "revision": 3, "at": "2026-09-24T16:16:17.623Z", "changedBy": "bob",   "price": 39.90 }
]
```

### One revision: `GET /api/products/1/revisions/2`

Answered through Spring Data Envers' `RevisionRepository`:

```json
{
  "revision": 2,
  "type": "CREATED",
  "at": "2026-09-24T16:16:16.548Z",
  "changedBy": "alice",
  "changedFields": [],
  "state": { "id": 1, "name": "Keyboard", "price": 49.90, "stock": 10,
             "categoryId": 1, "categoryName": "Peripherals" }
}
```

### A revision across all entities: `GET /api/revisions/3`

```json
{
  "revision": 3,
  "at": "2026-09-24T16:16:17.623Z",
  "changedBy": "bob",
  "modifiedEntities": ["com.gucardev.entityauditing.product.Product"]
}
```

A transaction that changes a category and a product together gets one revision listing both.

### State at a point in time: `GET /api/products/1/as-of?at=2026-09-24T16:16:17Z`

Returns the `state` object as it was at that instant (here: revision 2, price 49.90). Before
anything was recorded:

```json
{ "title": "Not Found", "status": 404,
  "detail": "Nothing was recorded before 1970-01-01T00:00:00Z",
  "instance": "/api/products/1/as-of" }
```

### Revert to an old revision

```bash
curl -X POST localhost:8094/api/products/1/revert/2 -H 'X-User: carol'   # rev 6
```

The response is the product with the values of revision 2. The category stays
`Input Devices`, since only the category id was stored in revision 2. A revert is **a new
revision** (6, by carol); history is never rewritten:

```json
{
  "id": 1, "name": "Keyboard", "price": 49.90, "stock": 10, "categoryName": "Input Devices",
  "createdAt": "2026-09-24T16:16:16.546239Z", "createdBy": "alice",
  "lastModifiedAt": "2026-09-24T16:16:20.154308700Z", "lastModifiedBy": "carol"
}
```

## Deleted products

The scenario continues: alice adds a second product, then both are deleted.

```bash
curl -X POST localhost:8094/api/products -H 'Content-Type: application/json' -H 'X-User: alice' \
     -d '{"name":"Mouse","price":19.90,"stock":25,"categoryId":1}'              # rev 7, product 2
curl -X DELETE localhost:8094/api/products/1 -H 'X-User: carol'                 # rev 8
curl -X DELETE localhost:8094/api/products/2 -H 'X-User: dave'                  # rev 9
```

### The product itself is gone

The row is deleted from `product`, so the normal API answers 404, and so does a revert
(there is no current product to write into):

```bash
curl localhost:8094/api/products/1
curl -X POST localhost:8094/api/products/1/revert/2
```

```json
{ "title": "Not Found", "status": 404, "detail": "Product 1 not found", "instance": "/api/products/1" }
```

### Its history is still complete: `GET /api/products/1/history`

The last entry is the deletion: who deleted it, when, and **what was deleted**:

```json
[
  "... revisions 2, 3, 5 as above ...",
  {
    "revision": 6,
    "type": "UPDATED",
    "at": "2026-09-24T16:21:24.067Z",
    "changedBy": "carol",
    "changedFields": ["name", "price", "stock"],
    "state": { "id": 1, "name": "Keyboard", "price": 49.90, "stock": 10,
               "categoryId": 1, "categoryName": "Input Devices" }
  },
  {
    "revision": 8,
    "type": "DELETED",
    "at": "2026-09-24T16:21:25.169Z",
    "changedBy": "carol",
    "changedFields": [],
    "state": { "id": 1, "name": "Keyboard", "price": 49.90, "stock": 10,
               "categoryId": 1, "categoryName": "Input Devices" }
  }
]
```

For a DELETED entry, `state` is the last state **before** the deletion.

### All deleted products: `GET /api/products/deleted`

Newest deletion first. Answered from the DELETE rows of `product_history`
(`AuditEntity.revisionType().eq(RevisionType.DEL)`):

```json
[
  {
    "id": 2,
    "revision": 9,
    "deletedAt": "2026-09-24T16:21:26.229Z",
    "deletedBy": "dave",
    "lastState": { "id": 2, "name": "Mouse", "price": 19.90, "stock": 25,
                   "categoryId": 1, "categoryName": "Input Devices" }
  },
  {
    "id": 1,
    "revision": 8,
    "deletedAt": "2026-09-24T16:21:25.169Z",
    "deletedBy": "carol",
    "lastState": { "id": 1, "name": "Keyboard", "price": 49.90, "stock": 10,
                   "categoryId": 1, "categoryName": "Input Devices" }
  }
]
```

### The delete revision and the time around it

`GET /api/products/1/revisions/8`:

```json
{
  "revision": 8,
  "type": "DELETED",
  "at": "2026-09-24T16:21:25.169Z",
  "changedBy": "carol",
  "changedFields": [],
  "state": { "id": 1, "name": "Keyboard", "price": 49.90, "stock": 10,
             "categoryId": 1, "categoryName": "Input Devices" }
}
```

`GET /api/revisions/8`:

```json
{ "revision": 8, "at": "2026-09-24T16:21:25.169Z", "changedBy": "carol",
  "modifiedEntities": ["com.gucardev.entityauditing.product.Product"] }
```

`GET /api/products/1/as-of?at=...` with a time **before** the deletion still returns the
product; with a time **after** it:

```json
{ "title": "Not Found", "status": 404,
  "detail": "Product 1 did not exist at 2026-09-24T16:21:26Z",
  "instance": "/api/products/1/as-of" }
```

### What the table really holds: `store_data_at_delete`

Raw `product_history` rows from a separate, shorter run: a product that was created (rev 2),
got a new price (rev 3) and was deleted (rev 4):

**`store_data_at_delete: true`** (this project):

| id | rev | revtype | revend | name | name_mod | price | price_mod | stock | stock_mod | category_id | category_mod |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 2 | 0 (ADD) | 3 | Keyboard | true | 49.90 | true | 10 | true | 1 | true |
| 1 | 3 | 1 (MOD) | 4 | Keyboard | false | 39.90 | **true** | 10 | false | 1 | false |
| 1 | 4 | 2 (DEL) | null | Keyboard | false | 39.90 | false | 10 | false | 1 | false |

**`store_data_at_delete: false`** (the Envers default):

| id | rev | revtype | revend | name | name_mod | price | price_mod | stock | stock_mod | category_id | category_mod |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 2 | 0 (ADD) | 3 | Keyboard | true | 49.90 | true | 10 | true | 1 | true |
| 1 | 3 | 1 (MOD) | 4 | Keyboard | false | 39.90 | **true** | 10 | false | 1 | false |
| 1 | 4 | 2 (DEL) | null | **null** | true | **null** | true | **null** | true | **null** | true |

With the default, you know *that* the product was deleted and *who* did it (through
`revision_info`), but the DELETE row itself is empty. To see what was deleted, you would have to
read the previous revision. The `deleted` endpoint and the DELETED history entry of this
project rely on the stored data (`true`).

Also visible in these rows:

- **`revend`** (validity strategy): each row says until which revision it was the current
  state. `null` = the latest row. For a deleted entity the latest row is the DEL row.
- **`*_mod`** flags: the MOD row (rev 3) has `price_mod = true` only, which is where
  `"changedFields": ["price"]` comes from.

### Restoring a deleted product

Envers does not undelete. The history holds everything needed to create a **new** product from
`lastState` (with a new id when ids are generated), and that new product starts its own
history. Linking it to the old one (for example a `restoredFrom` column) is up to the
application.

## Endpoints

| Method | Path | What |
|---|---|---|
| POST / GET / PUT / DELETE | `/api/products`, `/api/products/{id}` | CRUD, no audit code involved |
| POST | `/api/products/{id}/view` | changes only a `@NotAudited` field, so no revision |
| GET | `/api/products/{id}/history` | every revision with changed fields and full state |
| GET | `/api/products/{id}/revisions/{rev}` | one revision (Spring Data `RevisionRepository`) |
| GET | `/api/products/{id}/price-history` | only revisions where the price changed |
| GET | `/api/products/{id}/as-of?at=<ISO instant>` | state at a point in time |
| POST | `/api/products/{id}/revert/{rev}` | copy an old revision's values onto the product |
| GET | `/api/products/deleted` | deleted products: who, when, last state |
| GET | `/api/revisions/{rev}` | who, when, which entity types |
| POST / GET / PUT | `/api/categories`, `/api/categories/{id}` | categories (audited, to show relations) |

## Package layout

```
com.gucardev.entityauditing
├── common/audit/   AuditRevision (revision entity) + AuditRevisionListener, CurrentUser + CurrentUserFilter,
│                   BaseAuditedEntity + AuditingConfig (Spring Data auditing), RevisionController
├── common/error/   ProblemDetail error handling
├── category/       Category (@Audited), repository, controller, dto
└── product/        Product (@Audited(withModifiedFlag = true)), ProductRepository (+ RevisionRepository),
    │               ProductService (CRUD + revert), ProductHistoryService/Controller (history queries)
    └── dto/        ProductRequest, ProductResponse, ProductRevisionResponse, ProductSnapshot, PriceChange,
                    DeletedProduct
```

## Database schema (6 tables)

Generated by Hibernate from the mappings (`ddl-auto`). In production create the same tables
with Flyway or Liquibase.

**`product`** (current state + Spring Data auditing columns)

| column | type | notes |
|---|---|---|
| `id` | bigint | PK, identity |
| `name` | varchar(100) | not null |
| `price` | numeric(12,2) | not null |
| `stock` | integer | not null |
| `category_id` | bigint | FK → `category.id` |
| `last_viewed_at` | timestamp | `@NotAudited` |
| `created_at`, `created_by` | timestamp, varchar(50) | `@CreatedDate`, `@CreatedBy` |
| `last_modified_at`, `last_modified_by` | timestamp, varchar(50) | `@LastModifiedDate`, `@LastModifiedBy` |

**`product_history`** (one row per product per revision; the name is `product` + `audit_table_suffix`)

| column | type | notes |
|---|---|---|
| `id` | bigint | PK part 1: the product id |
| `rev` | bigint | PK part 2: → `revision_info.id` |
| `revtype` | tinyint | `0` = ADD, `1` = MOD, `2` = DEL |
| `revend` | bigint | revision where this row stopped being current (validity strategy); null = still current |
| `name`, `price`, `stock`, `category_id` | | the audited values at that revision |
| `name_mod`, `price_mod`, `stock_mod`, `category_mod` | boolean | "changed in this revision" (`withModifiedFlag`) |

`last_viewed_at` (`@NotAudited`) and the Spring Data auditing columns are **not** copied.

**`category`** / **`category_history`**: the same pattern (`id`, `name`, auditing columns /
`id`, `rev`, `revtype`, `revend`, `name`).

**`revision_info`** (one row per transaction that changed audited data)

| column | type | notes |
|---|---|---|
| `id` | bigint | PK, identity, the revision number |
| `timestamp` | bigint | epoch millis |
| `username` | varchar(50) | set by `AuditRevisionListener` |

**`revision_changed_entity`** (`rev`, `entity_name`): which entity types each revision touched
(`@ModifiedEntityNames`).

## How it works

### 1. Mark what to audit

```java
@Entity
@Audited(withModifiedFlag = true)        // history + a "_mod" flag per column
public class Product extends BaseAuditedEntity {
    private String name;
    private BigDecimal price;
    private int stock;
    @ManyToOne(fetch = FetchType.LAZY) private Category category;   // Category is @Audited too
    @NotAudited private Instant lastViewedAt;                         // noisy, no business value
}
```

- An audited relation must point to an audited entity; otherwise mark it
  `@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)`, and history then shows the
  target's *current* state instead of its historic one.
- `withModifiedFlag` costs one boolean column per property and makes "what changed" and "when
  did the price change" cheap queries. It can also be set per field.

### 2. Configuration (`application.yaml`)

```yaml
spring.jpa.properties.org.hibernate.envers:
  audit_table_suffix: _history     # product -> product_history (default suffix: _AUD)
  store_data_at_delete: true       # keep the last state in the DELETE row
  audit_strategy: validity         # adds REVEND for faster "state at revision N" queries
```

- **`audit_table_suffix`** (or `audit_table_prefix`) names the audit tables. A single table can
  be renamed with `@AuditTable("...")`.
- **`store_data_at_delete`**: without it, the DELETE row contains only the id, and "what was
  deleted?" cannot be answered.
- **`audit_strategy: validity`**: the default strategy finds "the row valid at revision N" with
  a `max(rev)` subquery; the validity strategy also writes `revend` into the previous row, so
  reads become a simple range check (`rev <= N and (revend > N or revend is null)`). Writes are
  slightly more expensive; reads over large histories are much faster. Pick one before going
  live, because switching later requires filling `revend` for existing rows.

### 3. A custom revision entity: who made the change

```java
@Entity @Table(name = "revision_info")
@RevisionEntity(AuditRevisionListener.class)
public class AuditRevision {
    @Id @GeneratedValue @RevisionNumber private Long id;
    @RevisionTimestamp private long timestamp;
    private String username;                          // set by the listener
    @ModifiedEntityNames @ElementCollection private Set<String> modifiedEntityNames;
}

public class AuditRevisionListener implements RevisionListener {
    public void newRevision(Object revision) {
        ((AuditRevision) revision).setUsername(CurrentUser.get());
    }
}
```

Envers' default `REVINFO` has only a number and a timestamp. A custom revision entity adds
whatever the audit trail needs: user, IP address, request id, a reason. It is stored once per
transaction, not once per changed row.

### Who made the change

`CurrentUser` holds the user for the current thread; both the revision listener and Spring Data's
`AuditorAware` read it. Here `CurrentUserFilter` fills it from the `X-User` header, which
**anyone can forge**, so it is a demo shortcut. In a real application read the authenticated user
from Spring Security (`SecurityContextHolder`). Changes outside a request (batch jobs, tests) are
recorded as `system`, requests without the header as `anonymous`.

### 4. Reading the history

**Spring Data Envers** for the simple cases: extend `RevisionRepository` and Spring Boot wires
it automatically:

```java
public interface ProductRepository extends JpaRepository<Product, Long>,
                                           RevisionRepository<Product, Long, Long> { }

productRepository.findRevision(id, 2L);         // Optional<Revision<Long, Product>>
productRepository.findRevisions(id);            // all revisions
productRepository.findLastChangeRevision(id);
```

**Envers `AuditReader`** for everything else (`ProductHistoryService`):

```java
AuditReader reader = AuditReaderFactory.get(entityManager);

// history with changed property names: rows of [entity, revision entity, RevisionType, Set<String>]
reader.createQuery().forRevisionsOfEntityWithChanges(Product.class, true)
      .add(AuditEntity.id().eq(id))
      .addOrder(AuditEntity.revisionNumber().asc())
      .getResultList();

// only revisions where the price changed (needs the modified flags)
reader.createQuery().forRevisionsOfEntity(Product.class, false, true)
      .add(AuditEntity.id().eq(id))
      .add(AuditEntity.property("price").hasChanged())
      .getResultList();

// state at a point in time
Number rev = reader.getRevisionNumberForDate(instant);
Product then = reader.find(Product.class, id, rev);
```

Read history inside a (read-only) transaction: the historic entity's relations (its category)
are loaded lazily from the audit tables.

## Spring Data auditing vs. Envers

| | Spring Data JPA auditing | Hibernate Envers |
|---|---|---|
| Stores | `createdBy/At`, `lastModifiedBy/At` on the row | a full copy per change in `*_history` |
| Answers | "who created it, who touched it last" | "who changed what, when, and what was it before" |
| After a delete | gone with the row | kept, including the deleted state |
| Cost | 4 columns | one extra row per change + a revision row per transaction |

They work well together: the auditing columns are always at hand without a query into the
history, and Envers holds the full trail. Envers ignores fields of a `@MappedSuperclass` that is
not `@Audited`, so the auditing columns are not duplicated into the history.

## Pitfalls

- **Bulk updates bypass Envers.** `update Product p set ...` in JPQL, native SQL and
  `@Modifying` queries do not go through entity events: the change happens but **no history
  is written** (see `EnversBehaviourTest.bulkUpdatesBypassEnvers`). Change audited data through
  entities, or write the audit rows yourself.
- **One transaction = one revision**, holding the final state. Creating and then updating a
  product in the same transaction gives a single CREATED revision with the final values.
- **No change, no revision.** Saving identical values or changing only `@NotAudited` fields
  writes nothing.
- **Audit tables grow forever.** Plan archiving or partitioning, and index `rev` / `revend` for
  large tables.
- **Sensitive data is copied too.** Personal data in audited columns is duplicated into the
  history. An erasure request (GDPR) must clean the `*_history` rows as well, or exclude such
  fields with `@NotAudited`.
- **Schema changes need migrations for both tables.** A new column on `product` needs the same
  column on `product_history` (plus `_mod` if flags are on).
- **Historic entities are read-only snapshots.** Do not modify and save an entity returned by
  the audit reader; copy its values onto the current entity (see `ProductService.revert`).
- **Revision numbers are global**, not per entity: a product's history has gaps where other
  entities changed.

## Tests

```bash
./mvnw test
```

- `ProductAuditApiTest` (MockMvc): full lifecycle with types, users, changed fields and the
  deleted state; no revision for identical values or `@NotAudited` changes; price history;
  historic category names; single revision via Spring Data and revert; state at a point in
  time; deleted products (404 on the product, history, `/deleted` list, delete revision,
  as-of before and after); Spring Data auditing columns; revision details; anonymous user; 404s.
- `EnversBehaviourTest`: one revision per transaction covering several entities, `system` user
  outside HTTP, bulk updates bypassing Envers, and the generated tables and columns.
