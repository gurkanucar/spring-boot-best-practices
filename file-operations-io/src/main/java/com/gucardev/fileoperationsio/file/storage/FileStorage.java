package com.gucardev.fileoperationsio.file.storage;

import java.io.InputStream;
import java.util.UUID;
import org.springframework.core.io.Resource;

/**
 * Where accepted file bytes are kept, addressed by the file's UUID only. User input (such as the
 * original filename) never reaches an implementation. {@link LocalFileStorage} keeps files on disk;
 * an S3 implementation could replace it without touching the rest of the code.
 */
public interface FileStorage {

    /** Streams {@code content} into storage under {@code id}. Nothing is kept if it fails. */
    void store(UUID id, InputStream content);

    /** The stored content. {@link Resource#exists()} is false when nothing is stored under {@code id}. */
    Resource open(UUID id);

    /** Deletes the content if present. */
    void delete(UUID id);
}
