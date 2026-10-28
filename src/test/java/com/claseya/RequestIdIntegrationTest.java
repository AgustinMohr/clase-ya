package com.claseya;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** OPS-001: request correlation id. */
@Transactional
class RequestIdIntegrationTest extends AbstractWebIntegrationTest {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Test
    void echoesAValidIncomingRequestId() throws Exception {
        mockMvc.perform(get("/api/teachers").header("X-Request-Id", "test-request-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "test-request-123"));
    }

    @Test
    void generatesOneWhenTheHeaderIsMissing() throws Exception {
        mockMvc.perform(get("/api/teachers"))
                .andExpect(header().string("X-Request-Id", matchesPattern(UUID_PATTERN)));
    }

    @Test
    void rejectsAnUnsafeIncomingHeader() throws Exception {
        mockMvc.perform(get("/api/teachers").header("X-Request-Id", "bad id with spaces"))
                .andExpect(header().string("X-Request-Id", matchesPattern(UUID_PATTERN)));
    }
}
