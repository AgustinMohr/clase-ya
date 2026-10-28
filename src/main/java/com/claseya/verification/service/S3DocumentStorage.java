package com.claseya.verification.service;

import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.common.exception.ServiceUnavailableException;
import com.claseya.model.VerificationDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.util.UUID;

/**
 * STOR-01: private S3-compatible bucket (Cloudflare R2 by default). The API keeps receiving the
 * multipart upload; only the binary leaves PostgreSQL. Downloads go through the authorized backend
 * endpoint (the key is never exposed to the browser).
 */
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3")
public class S3DocumentStorage implements DocumentStorage {

    private final S3Client s3;
    private final String bucket;

    public S3DocumentStorage(@Value("${app.storage.s3.endpoint:}") String endpoint,
                             @Value("${app.storage.s3.region:auto}") String region,
                             @Value("${app.storage.s3.bucket:}") String bucket,
                             @Value("${app.storage.s3.access-key:}") String accessKey,
                             @Value("${app.storage.s3.secret-key:}") String secretKey,
                             @Value("${app.storage.s3.path-style:false}") boolean pathStyle) {
        if (bucket == null || bucket.isBlank()
                || accessKey == null || accessKey.isBlank()
                || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "STORAGE_BUCKET, STORAGE_ACCESS_KEY and STORAGE_SECRET_KEY are required for STORAGE_PROVIDER=s3");
        }
        var builder = S3Client.builder()
                .region(Region.of(region == null || region.isBlank() ? "auto" : region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyle)
                        .build());
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        this.s3 = builder.build();
        this.bucket = bucket;
    }

    /** Test seam: inject a pre-built client (no container/network needed). */
    S3DocumentStorage(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public String store(byte[] content, String contentType) {
        String key = "verification/" + UUID.randomUUID();
        try {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(content));
            return key;
        } catch (SdkException ex) {
            throw new ServiceUnavailableException("Evidence storage is unavailable");
        }
    }

    @Override
    public byte[] load(VerificationDocument document) {
        if (document.getStorageKey() == null) {
            return document.getContent();
        }
        try {
            return s3.getObjectAsBytes(GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(document.getStorageKey())
                            .build())
                    .asByteArray();
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new ResourceNotFoundException("Document content not found");
            }
            throw new ServiceUnavailableException("Evidence storage is unavailable");
        } catch (SdkException ex) {
            throw new ServiceUnavailableException("Evidence storage is unavailable");
        }
    }

    @Override
    public void delete(VerificationDocument document) {
        if (document.getStorageKey() == null) {
            return;
        }
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(document.getStorageKey())
                    .build());
        } catch (SdkException ex) {
            throw new ServiceUnavailableException("Evidence storage is unavailable");
        }
    }
}
