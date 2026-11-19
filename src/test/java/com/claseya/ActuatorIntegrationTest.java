package com.claseya;

import com.claseya.model.User;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** OPS-001: health is public, metrics are ADMIN-only. */
@Transactional
class ActuatorIntegrationTest extends AbstractWebIntegrationTest {

    @Test
    void livenessProbe_isPublicAndLeaksNoDetails() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void readinessProbe_isPublicAndLeaksNoDetails() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void otherHealthSubpathsStayProtected() throws Exception {
        mockMvc.perform(get("/actuator/health/db")).andExpect(status().isUnauthorized());
    }

    @Test
    void metrics_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    }

    @Test
    void metrics_rejectsNonAdmins() throws Exception {
        User student = createUser("metrics-student@example.com", UserRole.STUDENT, UserStatus.ACTIVE);

        mockMvc.perform(get("/actuator/metrics").header("Authorization", bearer(student)))
                .andExpect(status().isForbidden());
    }

    @Test
    void metrics_isAvailableToAdmins() throws Exception {
        mockMvc.perform(get("/actuator/metrics").header("Authorization", adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());
    }
}
