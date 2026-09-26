package com.gucardev.fileoperationsio.file;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Metadata of an uploaded file. The bytes live on disk under {@code <directory>/<id>}; the original
 * filename is kept here only, so user input never becomes part of a disk path.
 */
@Entity
@Table(name = "stored_file")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoredFile {

    @Id
    private UUID id;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(nullable = false, length = 20)
    private String extension;

    /** Detected from the content by Tika, never taken from the client. */
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long size;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    public static StoredFile create(UUID id, String originalFilename, String extension, String contentType,
                                    long size, String sha256) {
        StoredFile file = new StoredFile();
        file.id = id;
        file.originalFilename = originalFilename;
        file.extension = extension;
        file.contentType = contentType;
        file.size = size;
        file.sha256 = sha256;
        file.uploadedAt = Instant.now();
        return file;
    }
}
