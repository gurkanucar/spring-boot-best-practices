package com.gucardev.fileoperationss3.config;

import com.gucardev.fileoperationss3.file.FileStorageProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * The S3 client (server-side calls) and presigner (URLs for clients). Both use the same endpoint,
 * region and credentials, so presigned URLs point where the server writes.
 */
@Configuration
public class S3Config {

    @Bean(destroyMethod = "close")
    S3Client s3Client(FileStorageProperties properties) {
        FileStorageProperties.S3 s3 = properties.s3();
        var builder = S3Client.builder()
                .region(Region.of(s3.region()))
                .credentialsProvider(credentials(s3))
                .httpClientBuilder(ApacheHttpClient.builder())
                .serviceConfiguration(serviceConfiguration(s3));
        if (s3.endpoint() != null) {
            builder.endpointOverride(s3.endpoint());
        }
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner(FileStorageProperties properties) {
        FileStorageProperties.S3 s3 = properties.s3();
        var builder = S3Presigner.builder()
                .region(Region.of(s3.region()))
                .credentialsProvider(credentials(s3))
                .serviceConfiguration(serviceConfiguration(s3));
        if (s3.endpoint() != null) {
            builder.endpointOverride(s3.endpoint());
        }
        return builder.build();
    }

    // Static keys only for local MinIO; on AWS use the default chain (environment, IAM role, ...).
    private static AwsCredentialsProvider credentials(FileStorageProperties.S3 s3) {
        if (s3.accessKey() != null && !s3.accessKey().isBlank()) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(s3.accessKey(), s3.secretKey()));
        }
        return DefaultCredentialsProvider.builder().build();
    }

    private static S3Configuration serviceConfiguration(FileStorageProperties.S3 s3) {
        return S3Configuration.builder().pathStyleAccessEnabled(s3.pathStyle()).build();
    }
}
