package com.gucardev.fileoperationss3.file.storage;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Object storage addressed by keys the server builds from UUIDs. {@link S3ObjectStorage} is the only
 * class outside {@code config/} that uses the AWS SDK.
 */
public interface ObjectStorage {

    record ObjectInfo(long size, String etag) {
    }

    /**
     * A URL the client calls directly, without our credentials.
     *
     * @param headers headers the client must send exactly as given (they are part of the signature)
     */
    record PresignedRequest(String url, String method, Map<String, String> headers, Instant expiresAt) {
    }

    /** Stores {@code content} with the given response headers; returns the ETag. */
    String put(String key, InputStream content, long size, String contentType, String contentDisposition);

    Optional<ObjectInfo> head(String key);

    /** The first {@code maxBytes} bytes, enough for type detection without downloading the file. */
    byte[] readHead(String key, int maxBytes);

    /** Copies an object and replaces its Content-Type and Content-Disposition; returns the new ETag. */
    String copy(String sourceKey, String targetKey, String contentType, String contentDisposition);

    /** The object content; the caller closes the stream. */
    InputStream open(String key);

    void delete(String key);

    /** A PUT URL that only accepts a body of exactly {@code size} bytes. */
    PresignedRequest presignPut(String key, long size, Duration ttl);

    /** A GET URL whose response carries the given Content-Type and Content-Disposition. */
    PresignedRequest presignGet(String key, String contentType, String contentDisposition, Duration ttl);

    /** The permanent URL of an object that the bucket policy makes publicly readable. */
    String publicUrl(String key);
}
