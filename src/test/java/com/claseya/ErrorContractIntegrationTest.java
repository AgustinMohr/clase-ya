package com.claseya;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** The public /error endpoint keeps the ApiError contract instead of Boot's status 999. */
@Transactional
class ErrorContractIntegrationTest extends AbstractWebIntegrationTest {

    @Test
    void directErrorRequestReturnsTheSharedContractWith404() throws Exception {
        String body = getJson("/error", null, 404);

        assertThat(body).contains("\"status\":404")
                .contains("\"error\":\"Not Found\"")
                .contains("\"path\":\"/error\"")
                .doesNotContain("999");
    }
}
