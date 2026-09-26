package com.gucardev.fileoperationss3.file;

import com.gucardev.fileoperationss3.file.StoredFile.Visibility;
import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

/**
 * Files in S3, public or private, uploaded through the API or directly with presigned URLs.
 * Unknown ids, and files that are not READY, end in 404 for downloads.
 */
public interface FileService {

    /** The client PUTs the file to {@code url}, sending {@code headers} exactly as given. */
    record UploadUrl(UUID id, String url, String method, Map<String, String> headers, Instant expiresAt) {
    }

    /** {@code expiresAt} is null for a public file's permanent URL. */
    record DownloadUrl(String url, Instant expiresAt) {
    }

    /** A file ready to stream back through the API; the caller closes {@code content}. */
    record Download(StoredFile file, InputStream content) {
    }

    /**
     * Validates and stores an upload that comes through the API.
     *
     * @throws FileRejectedException 400 (empty), 413 (too large) or 415 (type not accepted)
     */
    StoredFile upload(MultipartFile upload, Visibility visibility);

    /**
     * Step 1 of a presigned upload: checks the name and size, and returns a short-lived URL for
     * exactly {@code size} bytes. The file is PENDING until {@link #completeUpload} validates it.
     */
    UploadUrl createUpload(String filename, long size, Visibility visibility);

    /**
     * Step 3 of a presigned upload, after the client's PUT: validates the uploaded bytes and makes
     * the file READY, or deletes them and marks it REJECTED (415).
     */
    StoredFile completeUpload(UUID id);

    /** READY files only. */
    Page<StoredFile> list(Pageable pageable);

    StoredFile get(UUID id);

    Download download(UUID id);

    DownloadUrl downloadUrl(UUID id);

    /** The permanent URL of a READY public file. */
    Optional<String> publicUrl(StoredFile file);

    void delete(UUID id);
}
