package com.claseya.verification.service;

import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.model.VerificationDocument;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** STOR-01: adapter behavior without a real bucket (R2 is verified at deployment). */
class S3DocumentStorageTest {

    private final S3Client s3 = mock(S3Client.class);
    private final S3DocumentStorage storage = new S3DocumentStorage(s3, "evidence");

    @Test
    void storesWithAPrivateKeyAndContentType() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        byte[] content = "%PDF-1.7 evidence".getBytes(StandardCharsets.UTF_8);

        String key = storage.store(content, "application/pdf");

        assertThat(key).startsWith("verification/");
        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("evidence");
        assertThat(request.getValue().key()).isEqualTo(key);
        assertThat(request.getValue().contentType()).isEqualTo("application/pdf");
    }

    @Test
    void loadsAndDeletesByKey() {
        byte[] content = "evidence".getBytes(StandardCharsets.UTF_8);
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), content));
        VerificationDocument document = new VerificationDocument();
        document.setStorageKey("verification/abc");

        assertThat(storage.load(document)).isEqualTo(content);

        storage.delete(document);
        verify(s3).deleteObject(any(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.class));
    }

    @Test
    void missingObjectIsA404() {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());
        VerificationDocument document = new VerificationDocument();
        document.setStorageKey("verification/missing");

        assertThatThrownBy(() -> storage.load(document))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void legacyRowsStillReadTheirContentColumn() {
        VerificationDocument document = new VerificationDocument();
        document.setContent("legacy".getBytes(StandardCharsets.UTF_8));

        assertThat(storage.load(document)).isEqualTo("legacy".getBytes(StandardCharsets.UTF_8));
    }
}
