# Spring Boot: Secure File Upload & Download

Upload, list, download and delete files over a REST API, following the
[OWASP File Upload Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html):

- the file type is detected from the **content (magic bytes)** with Apache Tika, not from the
  extension or the client's `Content-Type`;
- an **allowlist** of types, plus a **blocklist** of dangerous types and extensions;
- a configurable **maximum size**;
- files are stored on disk under a **UUID**; the **original filename** is kept as metadata in H2;
- downloads are always attachments, with **security headers**.

Spring Boot 4.1.1, Java 25, H2, Spring Data JPA, Apache Tika 4.0.0 (`tika-core`).

## Run

```bash
./mvnw spring-boot:run                                   # add -Dspring-boot.run.profiles=local for the H2 console
```

```bash
# upload
curl -i -F "file=@report.pdf" http://localhost:8080/api/files
# 201 Location: /api/files/3f1c...
# {"id":"3f1c...","originalFilename":"report.pdf","contentType":"application/pdf","size":48213,
#  "sha256":"9b2e...","uploadedAt":"2026-09-26T12:00:00Z"}

curl http://localhost:8080/api/files                     # list (page, size; newest first)
curl http://localhost:8080/api/files/{id}                # metadata
curl -OJ http://localhost:8080/api/files/{id}/content    # download with the original name
curl -X DELETE http://localhost:8080/api/files/{id}      # 204

# rejected: an executable renamed to .pdf
curl -i -F "file=@setup.exe;filename=invoice.pdf" http://localhost:8080/api/files
# 415 {"title":"Unsupported Media Type","detail":"File type application/x-msdownload is not allowed", ...}
```

Ready-made requests for every case (uploads, downloads and each rejection) are in
[`http/requests.http`](http/requests.http) (IntelliJ IDEA HTTP Client), with sample files in
`http/samples/`. Run them top to bottom: ids and URLs from responses are passed on in variables.

Metadata is stored in `./data` (H2 file database) and file bytes in `./uploads`. Both are git-ignored.
The H2 console (`/h2-console`, JDBC URL `jdbc:h2:file:./data/files`) runs only with the `local` profile.

## Upload pipeline

A file must pass every step; nothing is kept when one fails.

1. **Not empty** → otherwise `400`.
2. **Size** — `spring.servlet.multipart.max-file-size` (fed from `file-storage.max-file-size`) makes
   the servlet container reject a larger upload with `413` before any of our code runs.
3. **Filename sanitizing** — path parts, control characters and characters such as `<>:"|?*` are
   removed, leading dots stripped, length capped. `../../secret.txt` becomes `secret.txt`.
   The name is only metadata; it is never used to build a disk path.
4. **Type detection** — Tika reads the first bytes (magic bytes) of the upload, without the filename.
   The container has already buffered the upload (on disk for large files), so it can be read twice
   without holding it in memory.
5. **Rules** (`415` when one fails):
   - the detected type, or any of its super types, is in `blocked-types`;
   - the detected type is not in `allowed-types`;
   - any extension in the name is in `blocked-extensions` (`invoice.pdf.exe`, `shell.php.png`);
   - the name has no extension, or its last extension is not allowed for the detected type
     (PNG bytes named `photo.pdf`).
6. **Store** — only now is the upload streamed to `uploads/<uuid>` (no extension), with its SHA-256
   computed on the way, and the metadata row saved. A rejected upload never reaches storage.

## Security checklist

Covered:

| Risk | Measure |
|---|---|
| Fake extension / Content-Type | Type from magic bytes (Tika); client `Content-Type` ignored |
| Dangerous file types | Allowlist; blocklist of executables, scripts, HTML, SVG, XML, archives |
| Double extensions | Every extension in the name is checked |
| Path traversal | Disk name is a UUID; original name sanitized and stored as metadata only |
| Large uploads / disk filling | Multipart size limit, enforced by the servlet container |
| Stored XSS when a file is opened | `Content-Disposition: attachment`, detected `Content-Type`, `nosniff`, `CSP: sandbox` |
| Header injection via filename | `ContentDisposition` builder encodes the name (`filename*=UTF-8''...`) |
| Clickjacking / embedding | `X-Frame-Options: DENY`, `frame-ancestors 'none'`, `Cross-Origin-Resource-Policy: same-origin` |
| Caching of private files | `Cache-Control: no-store` |
| URL leaks | `Referrer-Policy: no-referrer` |
| Error details | RFC 9457 ProblemDetail; raw user input is not echoed |
| Tampering / duplicates | SHA-256 stored with every file |

Not covered (add them for production):

- **Authentication and authorization** — who may upload, and who may download *which* file. Store the
  owner with the metadata and check it on every request. Spring Security also adds most of the
  headers above by default.
- **Virus / malware scanning** — e.g. ClamAV on the upload before step 6.
- **Rate limiting and quotas** per user.
- **Office documents (DOCX, XLSX)** — they are ZIP containers, which `tika-core` detects as
  `application/zip` (blocked). Allowing them needs `tika-parsers-standard-package` for container
  detection, and then protection against ZIP bombs.
- **Image re-encoding** to strip metadata or hidden payloads.
- **Object storage** (S3) instead of local disk when running several instances.

## Configuration

```yaml
file-storage:
  directory: ./uploads            # files stored as <uuid>
  max-file-size: 10MB             # also feeds spring.servlet.multipart.max-file-size
  max-filename-length: 255
  allowed-types:                  # detected type -> allowed extensions
    "[application/pdf]": [pdf]    # brackets keep the "/" in the map key
    "[image/png]": [png]
    "[image/jpeg]": [jpg, jpeg]
    "[image/gif]": [gif]
    "[text/plain]": [txt, csv]    # CSV has no magic bytes: it is detected as text/plain
  blocked-types: [application/x-dosexec, application/x-msdownload, text/html, image/svg+xml,
                  application/zip, ...]
  blocked-extensions: [exe, bat, sh, js, jar, php, jsp, html, svg, zip, ...]
```

See `src/main/resources/application.yaml` for the full lists.

## Code

Every component is used through an interface, so an implementation can be swapped without touching
its callers (e.g. `LocalFileStorage` → an S3 storage, `TikaFileTypeDetector` → another detector).

```text
file/
  FileController                          endpoints; FileResponse record
  FileService          → FileServiceImpl  upload / list / get / download / delete
  StoredFile, StoredFileRepository        metadata entity (H2)
  FileStorageProperties                   the file-storage section of application.yaml
  FileRejectedException                   400 / 413 / 415 as ProblemDetail
file/storage/
  FileStorage          → LocalFileStorage       store / open / delete by UUID
file/validation/
  FileValidator        → DefaultFileValidator   filename sanitizing, allow / block rules
  FileTypeDetector     → TikaFileTypeDetector   magic-byte detection (the only Tika code)
web/
  SecurityHeadersFilter                   OWASP response headers for /api/**
```

## Tests

`./mvnw test` runs `FileControllerTest` (MockMvc, temporary storage directory):
PNG / PDF upload and download (bytes, original name, headers), a non-ASCII filename (`résumé.txt`)
encoded in `Content-Disposition`, an EXE renamed to `.pdf`, HTML
content, an extension that does not match the content, `shell.php.png`, no extension, path traversal
in the name, an empty file, unknown id, delete, and no leftover file after a
rejected upload.
`UploadSizeLimitTest` sends a real HTTP request over the 1 KB test limit (`413`), because MockMvc
skips the container's multipart size check.
