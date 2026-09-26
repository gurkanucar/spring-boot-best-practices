package com.gucardev.fileoperationss3.config;

import com.gucardev.fileoperationss3.file.FileStorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ExpirationStatus;
import software.amazon.awssdk.services.s3.model.LifecycleRule;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

/**
 * Local and test setup of the bucket. In AWS the bucket, its policy and lifecycle rule belong to the
 * infrastructure code (Terraform, CloudFormation...), so this is off unless
 * {@code file-storage.s3.create-bucket=true}. Every step is idempotent.
 */
@Component
@ConditionalOnProperty("file-storage.s3.create-bucket")
@RequiredArgsConstructor
@Slf4j
public class BucketInitializer implements ApplicationRunner {

    // Anyone may read public/*. Nothing else in the bucket is readable without credentials.
    private static final String PUBLIC_READ_POLICY = """
            {
              "Version": "2012-10-17",
              "Statement": [{
                "Sid": "PublicReadForPublicPrefix",
                "Effect": "Allow",
                "Principal": "*",
                "Action": ["s3:GetObject"],
                "Resource": ["arn:aws:s3:::%s/public/*"]
              }]
            }""";

    private final S3Client s3;
    private final FileStorageProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        String bucket = properties.s3().bucket();
        try {
            s3.headBucket(request -> request.bucket(bucket));
        } catch (NoSuchBucketException e) {
            s3.createBucket(request -> request.bucket(bucket));
            log.info("Created bucket {}", bucket);
        }
        s3.putBucketPolicy(request -> request.bucket(bucket).policy(PUBLIC_READ_POLICY.formatted(bucket)));
        // Presigned uploads that were never completed are removed after a day.
        s3.putBucketLifecycleConfiguration(request -> request.bucket(bucket)
                .lifecycleConfiguration(lifecycle -> lifecycle.rules(LifecycleRule.builder()
                        .id("expire-pending-uploads")
                        .filter(filter -> filter.prefix("pending/"))
                        .expiration(expiration -> expiration.days(1))
                        .status(ExpirationStatus.ENABLED)
                        .build())));
    }
}
