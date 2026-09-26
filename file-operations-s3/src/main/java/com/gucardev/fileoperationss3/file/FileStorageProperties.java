package com.gucardev.fileoperationss3.file;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/** The {@code file-storage} section of application.yaml. Types and extensions are lower-cased. */
@ConfigurationProperties("file-storage")
public record FileStorageProperties(
        @DefaultValue("10MB") DataSize maxFileSize,
        @DefaultValue("255") int maxFilenameLength,
        Map<String, Set<String>> allowedTypes,
        Set<String> blockedTypes,
        Set<String> blockedExtensions,
        @DefaultValue S3 s3,
        @DefaultValue Presign presign) {

    /**
     * @param endpoint      only for S3-compatible stores such as MinIO; empty for AWS
     * @param accessKey     only for local use; empty means the AWS default credentials chain (IAM role)
     * @param pathStyle     {@code http://host/bucket/key} instead of {@code http://bucket.host/key} (MinIO)
     * @param createBucket  create the bucket, public policy and lifecycle rule at startup (local, tests)
     * @param publicBaseUrl base of public file URLs; in production usually a CloudFront domain
     */
    public record S3(URI endpoint,
                     @DefaultValue("eu-central-1") String region,
                     @DefaultValue("files") String bucket,
                     String accessKey,
                     String secretKey,
                     @DefaultValue("false") boolean pathStyle,
                     @DefaultValue("false") boolean createBucket,
                     String publicBaseUrl) {
    }

    /** How long presigned URLs stay valid. Keep them short. */
    public record Presign(@DefaultValue("10m") Duration uploadTtl,
                          @DefaultValue("5m") Duration downloadTtl) {
    }

    public FileStorageProperties {
        allowedTypes = allowedTypes == null ? Map.of() : allowedTypes.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(e -> lower(e.getKey()), e -> lower(e.getValue())));
        blockedTypes = lower(blockedTypes);
        blockedExtensions = lower(blockedExtensions);
    }

    private static Set<String> lower(Set<String> values) {
        return values == null ? Set.of()
                : values.stream().map(FileStorageProperties::lower).collect(Collectors.toUnmodifiableSet());
    }

    private static String lower(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
