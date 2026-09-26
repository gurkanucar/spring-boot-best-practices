package com.gucardev.fileoperationss3.file;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Metadata of a file in S3. The object key is built from the id only; the original filename is
 * kept here, so user input never becomes part of a key.
 */
@Entity
@Table(name = "stored_file")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoredFile {

    public enum Visibility {
        /** Stored under {@code public/}: readable by anyone through a permanent URL. */
        PUBLIC,
        /** Stored under {@code private/}: only through the API or a short-lived presigned URL. */
        PRIVATE
    }

    public enum Status {
        /** A presigned upload URL was issued; the bytes are not validated yet. */
        PENDING,
        /** Validated and stored under its final key. Only READY files can be downloaded. */
        READY,
        /** A presigned upload failed validation; its object was deleted. */
        REJECTED
    }

    @Id
    private UUID id;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    /** Detected from the content by Tika; null until the file is READY. */
    @Column(name = "content_type", length = 100)
    private String contentType;

    /** For a PENDING file: the size the client declared (and that the upload URL is signed for). */
    @Column(name = "file_size", nullable = false)
    private long size;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Visibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "object_key", nullable = false, length = 100)
    private String objectKey;

    private String etag;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** An upload through the API: validated before it was stored. */
    public static StoredFile ready(UUID id, String originalFilename, String contentType, long size,
                                   Visibility visibility, String objectKey, String etag) {
        StoredFile file = create(id, originalFilename, size, visibility, objectKey);
        file.markReady(objectKey, contentType, etag);
        return file;
    }

    /** A presigned upload: the client sends the bytes to {@code pendingKey} next. */
    public static StoredFile pending(UUID id, String originalFilename, long declaredSize, Visibility visibility,
                                     String pendingKey) {
        StoredFile file = create(id, originalFilename, declaredSize, visibility, pendingKey);
        file.status = Status.PENDING;
        return file;
    }

    public void markReady(String objectKey, String contentType, String etag) {
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.etag = etag;
        this.status = Status.READY;
        this.completedAt = Instant.now();
    }

    public void markRejected() {
        this.status = Status.REJECTED;
        this.completedAt = Instant.now();
    }

    private static StoredFile create(UUID id, String originalFilename, long size, Visibility visibility,
                                     String objectKey) {
        StoredFile file = new StoredFile();
        file.id = id;
        file.originalFilename = originalFilename;
        file.size = size;
        file.visibility = visibility;
        file.objectKey = objectKey;
        file.createdAt = Instant.now();
        return file;
    }
}
