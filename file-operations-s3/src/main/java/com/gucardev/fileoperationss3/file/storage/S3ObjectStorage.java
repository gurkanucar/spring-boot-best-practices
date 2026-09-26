package com.gucardev.fileoperationss3.file.storage;

import com.gucardev.fileoperationss3.file.FileStorageProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Component
public class S3ObjectStorage implements ObjectStorage {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;
    private final String publicBaseUrl;

    public S3ObjectStorage(S3Client s3, S3Presigner presigner, FileStorageProperties properties) {
        this.s3 = s3;
        this.presigner = presigner;
        FileStorageProperties.S3 config = properties.s3();
        this.bucket = config.bucket();
        this.publicBaseUrl = config.publicBaseUrl() != null
                ? config.publicBaseUrl().replaceAll("/+$", "")
                : "https://%s.s3.%s.amazonaws.com".formatted(config.bucket(), config.region());
    }

    @Override
    public String put(String key, InputStream content, long size, String contentType, String contentDisposition) {
        try (content) {
            return s3.putObject(request -> request.bucket(bucket).key(key)
                            .contentType(contentType).contentDisposition(contentDisposition),
                    RequestBody.fromInputStream(content, size)).eTag();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<ObjectInfo> head(String key) {
        try {
            HeadObjectResponse head = s3.headObject(request -> request.bucket(bucket).key(key));
            return Optional.of(new ObjectInfo(head.contentLength(), head.eTag()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public byte[] readHead(String key, int maxBytes) {
        return s3.getObjectAsBytes(request -> request.bucket(bucket).key(key).range("bytes=0-" + (maxBytes - 1)))
                .asByteArray();
    }

    @Override
    public String copy(String sourceKey, String targetKey, String contentType, String contentDisposition) {
        return s3.copyObject(request -> request
                        .sourceBucket(bucket).sourceKey(sourceKey)
                        .destinationBucket(bucket).destinationKey(targetKey)
                        // REPLACE: the client-chosen Content-Type of the pending upload is not carried over.
                        .metadataDirective(MetadataDirective.REPLACE)
                        .contentType(contentType)
                        .contentDisposition(contentDisposition))
                .copyObjectResult().eTag();
    }

    @Override
    public InputStream open(String key) {
        return s3.getObject(request -> request.bucket(bucket).key(key));
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(request -> request.bucket(bucket).key(key));
    }

    // Content-Length is part of the signature: S3 refuses a body of any other size.
    @Override
    public PresignedRequest presignPut(String key, long size, Duration ttl) {
        PutObjectRequest put = PutObjectRequest.builder().bucket(bucket).key(key).contentLength(size).build();
        return toPresignedRequest(presigner.presignPutObject(request -> request
                .signatureDuration(ttl).putObjectRequest(put)));
    }

    @Override
    public PresignedRequest presignGet(String key, String contentType, String contentDisposition, Duration ttl) {
        GetObjectRequest get = GetObjectRequest.builder().bucket(bucket).key(key)
                .responseContentType(contentType)
                .responseContentDisposition(contentDisposition)
                .build();
        return toPresignedRequest(presigner.presignGetObject(request -> request
                .signatureDuration(ttl).getObjectRequest(get)));
    }

    @Override
    public String publicUrl(String key) {
        return publicBaseUrl + "/" + key;
    }

    // "host" is set by every HTTP client itself; the other signed headers must be sent by the caller.
    private static PresignedRequest toPresignedRequest(
            software.amazon.awssdk.awscore.presigner.PresignedRequest presigned) {
        Map<String, String> headers = presigned.signedHeaders().entrySet().stream()
                .filter(header -> !header.getKey().equalsIgnoreCase("host"))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, header -> String.join(",", header.getValue())));
        return new PresignedRequest(presigned.url().toString(), presigned.httpRequest().method().name(),
                headers, presigned.expiration());
    }
}
