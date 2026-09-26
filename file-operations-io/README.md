# Spring Boot: Secure File Upload & Download

Upload, list, download and delete files over a REST API, following the
[OWASP File Upload Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html):

- the file type is detected from the **content (magic bytes)** with Apache Tika, not from the
  extension or the client's `Content-Type`;
- an **allowlist** of types, plus a **blocklist** of dangerous types and extensions;
- a configurable **maximum size**;
- files are stored on disk under a **UUID**; the **original filename** is kept as metadata in H2;
- downloads are always attachments, with **security headers**.

Spring Boot 4.1.1, Java 25, H2, Spring Data JPA, Apache Tika (`tika-core`).

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

Metadata is stored in `./data` (H2 file database) and file bytes in `./uploads`. Both are git-ignored.
The H2 console (`/h2-console`, JDBC URL `jdbc:h2:file:./data/files`) runs only with the `local` profile.

## Upload pipeline

A file must pass every step; nothing is kept when one fails.

1. **Not empty** → otherwise `400`.
2. **Size** — Spring's multipart limit (`413`) and a second byte count while copying to disk.
3. **Filename sanitizing** — path parts, control characters and characters such as `<>:"|?*` are
   removed, leading dots stripped, length capped. `../../secret.txt` becomes `secret.txt`.
   The name is only metadata; it is never used to build a disk path.
4. **Temp file** — the upload is streamed to a temp file in the storage directory (never fully in
   memory) and its SHA-256 is computed on the way.
5. **Type detection** — Tika reads the magic bytes of the temp file, without the filename.
6. **Rules** (`415` when one fails):
   - the detected type, or any of its super types, is in `blocked-types`;
   - the detected type is not in `allowed-types`;
   - any extension in the name is in `blocked-extensions` (`invoice.pdf.exe`, `shell.php.png`);
   - the name has no extension, or its last extension is not allowed for the detected type
     (PNG bytes named `photo.pdf`).
7. **Store** — the temp file is moved to `uploads/<uuid>` (no extension) and the metadata row saved.

## Security checklist

Covered:

| Risk | Measure |
|---|---|
| Fake extension / Content-Type | Type from magic bytes (Tika); client `Content-Type` ignored |
| Dangerous file types | Allowlist; blocklist of executables, scripts, HTML, SVG, XML, archives |
| Double extensions | Every extension in the name is checked |
| Path traversal | Disk name is a UUID; original name sanitized and stored as metadata only |
| Large uploads / disk filling | Multipart limit + byte count while streaming |
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
- **Virus / malware scanning** — e.g. ClamAV on the temp file before step 7.
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

```text
file/
  FileController          endpoints; FileResponse record
  FileService             upload / list / get / download / delete
  FileTypeValidator       filename sanitizing, Tika detection, allow / block rules
  FileStorage             temp file with size limit and SHA-256, move, open, delete
  StoredFile, StoredFileRepository
  FileStorageProperties   the file-storage section of application.yaml
  FileRejectedException   400 / 413 / 415 as ProblemDetail
web/
  SecurityHeadersFilter   OWASP response headers for /api/**
```

## Tests

`./mvnw test` runs `FileControllerTest` with a temporary storage directory and a 1 KB limit:
PNG / PDF upload and download (bytes, original name, headers), a non-ASCII filename (`Rapor Öğrenci.txt`)
encoded in `Content-Disposition`, an EXE renamed to `.pdf`, HTML
content, an extension that does not match the content, `shell.php.png`, no extension, path traversal
in the name, a file over the limit, an empty file, unknown id, delete, and no leftover file after a
rejected upload.
