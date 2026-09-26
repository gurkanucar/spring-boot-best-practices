# Spring Boot: Secure File Upload & Download on S3 (public / private, presigned URLs)

The S3 version of [`file-operations-io`](../file-operations-io/): the same validation rules
(Apache Tika magic-byte detection, allowlist + blocklist, size limit, sanitized names, UUID keys,
security headers), with files in S3 and metadata in H2, plus:

- **public and private files** — public ones get a permanent URL, private ones only short-lived
  presigned URLs;
- **presigned uploads** — the client sends the bytes straight to S3, the server validates them
  before anything becomes readable;
- **presigned downloads** — straight from S3, still as an attachment with the detected type.

Spring Boot 4.1.1, Java 25, AWS SDK for Java v2 **2.55.6**, Apache Tika **4.0.0** (`tika-core`),
H2, MinIO locally and in tests (Testcontainers **2.0.5**).

## Run

```bash
docker compose up -d        # MinIO: S3 API on :9000, console on :9001 (minioadmin / minioadmin)
./mvnw spring-boot:run      # creates the bucket "files", its public-read policy and lifecycle rule
```

MinIO no longer publishes community images; `docker-compose.yml` and the tests pin the last one,
`minio/minio:RELEASE.2025-04-22T22-12-26Z`. Unlike simple S3 mocks, it enforces bucket policies and
presigned signatures, so the public/private rules are really tested.

## Bucket layout

| Key | Who can read it |
|---|---|
| `public/<uuid>` | Anyone: the bucket policy allows `s3:GetObject` on `public/*` |
| `private/<uuid>` | Nobody without credentials: through the API or a presigned GET only |
| `pending/<uuid>` | Nobody: presigned uploads waiting for validation; a lifecycle rule deletes them after 1 day |

Keys are built from a UUID only. The original filename lives in H2 and in the objects'
`Content-Disposition`, never in a key.

## Upload through the API

```bash
curl -i -F "file=@logo.png" -F "visibility=PUBLIC" http://localhost:8080/api/files
# 201 {"id":"3f1c...","originalFilename":"logo.png","contentType":"image/png","size":4821,
#      "visibility":"PUBLIC","status":"READY","publicUrl":"http://localhost:9000/files/public/3f1c...", ...}
```

Validated before it is stored, exactly like `file-operations-io` (400 empty, 413 too large, 415 type
not accepted). `visibility` defaults to `PRIVATE`.

## Presigned upload (client → S3 directly)

The server never sees the bytes during the upload, so it cannot check them first. Instead nothing is
readable until a **complete** step has validated what arrived:

```text
1. POST /api/files/uploads {filename, size, visibility}
     name rules that need no content (blocked extensions, extension of some allowed type), size ≤ max
     → PENDING row + presigned PUT for pending/<uuid>, valid 10 min, signed for exactly `size` bytes
2. PUT <url>   (client → S3, with the returned headers)
3. POST /api/files/{id}/complete
     HEAD (uploaded? same size?) → read only the first 8 KB → Tika + all rules
     ok       → copy to public/ or private/ with the detected Content-Type, delete pending → 200 READY
     rejected → delete pending → 415, status REJECTED
```

```bash
curl -s -X POST http://localhost:8080/api/files/uploads -H "Content-Type: application/json" \
  -d '{"filename":"report.pdf","size":48213,"visibility":"PRIVATE"}'
# 201 {"id":"7a9e...","url":"http://localhost:9000/files/pending/7a9e...?X-Amz-...","method":"PUT",
#      "headers":{"content-length":"48213"},"expiresAt":"..."}

curl -X PUT --upload-file report.pdf "<url>"
curl -X POST http://localhost:8080/api/files/7a9e.../complete     # 200 READY or 415
```

Completing before the PUT gives `409`. A body of another size than declared is refused by S3
(`403`), because `Content-Length` is part of the signature.

## Download

```bash
curl -OJ http://localhost:8080/api/files/{id}/content        # streamed through the API
curl http://localhost:8080/api/files/{id}/download-url       # straight from S3
# public:  {"url":"http://localhost:9000/files/public/3f1c...","expiresAt":null}
# private: {"url":"http://localhost:9000/files/private/7a9e...?X-Amz-...","expiresAt":"...+5 min"}
```

Both ways the file comes as `Content-Disposition: attachment` with the detected `Content-Type`:
stored on the object for direct reads, and forced with `response-content-disposition` /
`response-content-type` on presigned GETs. Only `READY` files can be downloaded.

Other endpoints: `GET /api/files` (READY files, paged), `GET /api/files/{id}` (metadata, any
status), `DELETE /api/files/{id}`.

## Security checklist

Everything from [`file-operations-io`](../file-operations-io/README.md#security-checklist), plus:

| Risk | Measure |
|---|---|
| Unvalidated bytes via presigned upload | Uploads land in `pending/` (never readable); `complete` validates before copying them anywhere readable |
| Oversized presigned uploads | Declared size checked against the limit; `Content-Length` signed into the URL |
| Leaked presigned URLs | Short lifetimes: 10 min upload, 5 min download |
| Uploading over other objects | Keys are chosen by the server (`pending/<uuid>`); the URL is valid for that key only |
| Abandoned uploads | Lifecycle rule deletes `pending/` after 1 day |
| Client-chosen Content-Type on S3 | Replaced with the detected type when the object is copied (`MetadataDirective.REPLACE`) |
| Private files readable by URL guessing | No public access outside `public/`; private reads need a signature |

For production on AWS:

- **Credentials** — remove `access-key` / `secret-key`; the default chain uses the IAM role. Grant
  least privilege: `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject` on the bucket's objects only.
- **Public files** — keep S3 Block Public Access on and serve `public/*` through CloudFront with
  Origin Access Control; set `public-base-url` to the CloudFront domain instead of a bucket policy.
- **Bucket setup** — set `create-bucket: false` and manage the bucket, policy and lifecycle rule with
  Terraform / CloudFormation.
- **Browser uploads** — add a bucket CORS rule allowing `PUT` from your web origin.
- **Encryption** — SSE-S3 is the default; use SSE-KMS for stricter key control.
- **Authentication and ownership**, **virus scanning** and **rate limiting** are out of scope, as in
  `file-operations-io`.

## Configuration

```yaml
file-storage:
  max-file-size: 10MB                # also the largest presigned upload
  max-filename-length: 255
  allowed-types / blocked-types / blocked-extensions   # as file-operations-io
  s3:
    endpoint: http://localhost:9000  # MinIO; remove for AWS
    region: eu-central-1
    bucket: files
    access-key: minioadmin           # local only
    secret-key: minioadmin
    path-style: true                 # MinIO
    create-bucket: true              # local / tests; false in AWS
    public-base-url: http://localhost:9000/files   # CloudFront domain in AWS
  presign:
    upload-ttl: 10m
    download-ttl: 5m
```

## Code

Every component is used through an interface; only `S3ObjectStorage` and `config/` use the AWS SDK.

```text
config/
  S3Config                      S3Client (Apache HTTP client) + S3Presigner
  BucketInitializer             bucket, public-read policy for public/*, pending/ lifecycle rule
file/
  FileController                endpoints; request / response records
  FileService → FileServiceImpl API upload, presigned upload + complete, downloads, delete
  StoredFile (Visibility, Status), StoredFileRepository
  FileStorageProperties, FileRejectedException
file/storage/
  ObjectStorage → S3ObjectStorage        put, head, readHead, copy, open, delete, presign, publicUrl
file/validation/                          same as file-operations-io
  FileValidator → DefaultFileValidator   + checkFilename for presigned uploads
  FileTypeDetector → TikaFileTypeDetector
web/
  SecurityHeadersFilter
```

## Tests

`./mvnw test` (needs Docker) runs `FileS3IntegrationTest` against MinIO over real HTTP, calling
presigned URLs without credentials like a browser:

- a public file is readable anonymously through its permanent URL, as an attachment;
- a private file gives `403` anonymously and `200` through its presigned URL;
- presigned upload → PUT → complete → `READY`, downloadable, pending object gone;
- EXE bytes named `.pdf` via presigned upload → `415`, `REJECTED`, pending object deleted;
- a PUT with a different size than signed → `403`;
- blocked extension refused before an upload URL is issued; completing before the PUT → `409`;
- disallowed type through the API → `415`; unknown id → `404`; delete removes object and row.
