package com.claseya.verification.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class S3DocumentStorageStartupTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> context.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(S3DocumentStorage.class)
            .withPropertyValues(
                    "app.storage.provider=s3",
                    "app.storage.s3.endpoint=https://account.r2.cloudflarestorage.com",
                    "app.storage.s3.region=auto",
                    "app.storage.s3.bucket=evidence",
                    "app.storage.s3.access-key=key",
                    "app.storage.s3.secret-key=secret",
                    "app.storage.s3.path-style=true");

    @Test
    void s3StorageStartsWithTheConfiguredConstructor() {
        runner.run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(S3DocumentStorage.class));
    }
}
