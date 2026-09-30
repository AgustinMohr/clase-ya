package com.claseya;

import com.claseya.model.User;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** "Mi anuncio" completeness (TEACHER-001, RF-2): informative, never blocking. */
@Transactional
class TeacherAnnouncementIntegrationTest extends AbstractWebIntegrationTest {

    @Test
    void completeness_isIncompleteThenPublished() throws Exception {
        User teacher = createUser("ann+" + java.util.UUID.randomUUID() + "@test.com",
                UserRole.TEACHER, UserStatus.ACTIVE);
        String token = bearer(teacher);
        postJson("/api/teachers/profile", token,
                """
                {"name":"Ana Profesora","bio":"Profesora de matematica"}
                """, 201);

        JsonNode initial = toJson(getJson("/api/teachers/me/announcement", token, 200));
        assertThat(initial.get("completeness").asText()).isEqualTo("INCOMPLETE");
        assertThat(initial.get("hasName").asBoolean()).isTrue();
        assertThat(initial.get("hasSubject").asBoolean()).isFalse();

        makePublished(teacher);

        JsonNode published = toJson(getJson("/api/teachers/me/announcement", token, 200));
        assertThat(published.get("completeness").asText()).isEqualTo("PUBLISHED");
        assertThat(published.get("hasSubject").asBoolean()).isTrue();
        assertThat(published.get("hasModality").asBoolean()).isTrue();
        assertThat(published.get("hasAvailability").asBoolean()).isTrue();
    }
}
