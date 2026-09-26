package com.gucardev.fileoperationss3.file;

import com.gucardev.fileoperationss3.file.StoredFile.Status;
import com.gucardev.fileoperationss3.file.StoredFile.Visibility;
import com.gucardev.fileoperationss3.file.storage.ObjectStorage;
import com.gucardev.fileoperationss3.file.storage.ObjectStorage.ObjectInfo;
import com.gucardev.fileoperationss3.file.storage.ObjectStorage.PresignedRequest;
import com.gucardev.fileoperationss3.file.validation.FileValidator;
import com.gucardev.fileoperationss3.file.validation.FileValidator.AcceptedFile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    /** Enough for every magic-byte signature Tika checks. */
    private static final int DETECTION_BYTES = 8192;

    private final StoredFileRepository repository;
    private final FileValidator validator;
    private final ObjectStorage storage;
    private final FileStorageProperties properties;

    /** Validate first, then store: a rejected upload never reaches S3. */
    @Override
    public StoredFile upload(MultipartFile upload, Visibility visibility) {
        if (upload.isEmpty()) {
            throw new FileRejectedException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        String filename = validator.sanitizeFilename(upload.getOriginalFilename());
        AcceptedFile accepted;
        try (InputStream content = upload.getInputStream()) {
            accepted = validator.validate(content, filename);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        UUID id = UUID.randomUUID();
        String key = finalKey(visibility, id);
        String etag;
        try {
            etag = storage.put(key, upload.getInputStream(), upload.getSize(), accepted.contentType(),
                    attachment(filename));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            return repository.save(StoredFile.ready(id, filename, accepted.contentType(), upload.getSize(),
                    visibility, key, etag));
        } catch (RuntimeException e) {
            storage.delete(key);
            throw e;
        }
    }

    /**
     * Only the checks that need no content can run here; the bytes do not exist yet. The URL is for
     * {@code pending/}, which is never readable, and only accepts exactly {@code size} bytes.
     */
    @Override
    public UploadUrl createUpload(String filename, long size, Visibility visibility) {
        String safeName = validator.sanitizeFilename(filename);
        validator.checkFilename(safeName);
        if (size <= 0) {
            throw new FileRejectedException(HttpStatus.BAD_REQUEST, "Size must be positive");
        }
        long max = properties.maxFileSize().toBytes();
        if (size > max) {
            throw new FileRejectedException(HttpStatus.CONTENT_TOO_LARGE, "File is larger than " + max + " bytes");
        }

        UUID id = UUID.randomUUID();
        String pendingKey = pendingKey(id);
        repository.save(StoredFile.pending(id, safeName, size, visibility, pendingKey));
        PresignedRequest put = storage.presignPut(pendingKey, size, properties.presign().uploadTtl());
        return new UploadUrl(id, put.url(), put.method(), put.headers(), put.expiresAt());
    }

    /**
     * The security gate of a presigned upload: nothing becomes readable before its bytes pass the
     * same rules as an API upload. Only the first bytes are downloaded for type detection.
     */
    @Override
    public StoredFile completeUpload(UUID id) {
        StoredFile file = get(id);
        if (file.getStatus() != Status.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload is not pending");
        }
        String pendingKey = pendingKey(id);
        ObjectInfo uploaded = storage.head(pendingKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "File has not been uploaded yet"));

        AcceptedFile accepted;
        try {
            if (uploaded.size() != file.getSize()) { // S3 enforces the signed size; this is a second check
                throw new FileRejectedException(HttpStatus.BAD_REQUEST, "Uploaded size does not match");
            }
            byte[] head = storage.readHead(pendingKey, DETECTION_BYTES);
            accepted = validator.validate(new ByteArrayInputStream(head), file.getOriginalFilename());
        } catch (FileRejectedException e) {
            storage.delete(pendingKey);
            file.markRejected();
            repository.save(file);
            throw e;
        }

        // The copy gets the detected Content-Type; whatever the client sent with its PUT is dropped.
        String key = finalKey(file.getVisibility(), id);
        String etag = storage.copy(pendingKey, key, accepted.contentType(), attachment(file.getOriginalFilename()));
        storage.delete(pendingKey);
        file.markReady(key, accepted.contentType(), etag);
        return repository.save(file);
    }

    @Override
    public Page<StoredFile> list(Pageable pageable) {
        return repository.findByStatus(Status.READY, pageable);
    }

    @Override
    public StoredFile get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
    }

    @Override
    public Download download(UUID id) {
        StoredFile file = getReady(id);
        return new Download(file, storage.open(file.getObjectKey()));
    }

    @Override
    public DownloadUrl downloadUrl(UUID id) {
        StoredFile file = getReady(id);
        if (file.getVisibility() == Visibility.PUBLIC) {
            return new DownloadUrl(storage.publicUrl(file.getObjectKey()), null);
        }
        PresignedRequest get = storage.presignGet(file.getObjectKey(), file.getContentType(),
                attachment(file.getOriginalFilename()), properties.presign().downloadTtl());
        return new DownloadUrl(get.url(), get.expiresAt());
    }

    @Override
    public Optional<String> publicUrl(StoredFile file) {
        return file.getStatus() == Status.READY && file.getVisibility() == Visibility.PUBLIC
                ? Optional.of(storage.publicUrl(file.getObjectKey()))
                : Optional.empty();
    }

    @Override
    public void delete(UUID id) {
        StoredFile file = get(id);
        storage.delete(file.getObjectKey());
        repository.delete(file);
    }

    private StoredFile getReady(UUID id) {
        StoredFile file = get(id);
        if (file.getStatus() != Status.READY) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
        }
        return file;
    }

    // Keys are built from the id only. The prefix decides who can read the object (bucket policy).
    private static String finalKey(Visibility visibility, UUID id) {
        return visibility.name().toLowerCase(Locale.ROOT) + "/" + id;
    }

    private static String pendingKey(UUID id) {
        return "pending/" + id;
    }

    /** Always attachment; the builder encodes the name so it cannot break the header. */
    private static String attachment(String filename) {
        return ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString();
    }
}
