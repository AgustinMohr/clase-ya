package com.claseya;

import com.claseya.model.User;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADMIN-002: the directory only answers ADMIN, filters and paginates server-side, and suspending
 * a live account takes effect on its next request (the JWT filter reloads the user).
 *
 * <p>{@code @Transactional}: the shared test database is reused by every class in the JVM, so a
 * committed published teacher or a suspended account would leak into unrelated tests. The cases
 * that expect a 409 still work: they run inside the test transaction, which is rolled back as a
 * whole.
 */
@Transactional
class AdminDirectoryIntegrationTest extends AbstractWebIntegrationTest {

    private static final String DIRECTORY = "/api/admin/users";

    private String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private void adminPost(String uri, String token, int expectedStatus) throws Exception {
        mockMvc.perform(post(uri).header("Authorization", token))
                .andExpect(status().is(expectedStatus));
    }

    private JsonNode directory(String query, String token) throws Exception {
        return toJson(getJson(DIRECTORY + "?query=" + query + "&size=50", token, 200));
    }

    @Test
    void anonymousIsRejected() throws Exception {
        getJson(DIRECTORY, null, 401);
    }

    @Test
    void aStudentCannotReadTheDirectory() throws Exception {
        User student = createUser("dir-student-" + suffix() + "@test.com",
                UserRole.STUDENT, UserStatus.ACTIVE);

        getJson(DIRECTORY, bearer(student), 403);
    }

    @Test
    void adminFiltersByRoleAndStatus() throws Exception {
        String s = suffix();
        createUser("dir-a-" + s + "@test.com", UserRole.STUDENT, UserStatus.ACTIVE);
        createUser("dir-b-" + s + "@test.com", UserRole.TEACHER, UserStatus.ACTIVE);
        createUser("dir-c-" + s + "@test.com", UserRole.TEACHER, UserStatus.SUSPENDED);
        String admin = adminBearer();

        JsonNode all = toJson(getJson(DIRECTORY + "?query=" + s + "&size=50", admin, 200));
        assertThat(all.get("content")).hasSize(3);

        JsonNode teachers = toJson(getJson(
                DIRECTORY + "?query=" + s + "&role=TEACHER&size=50", admin, 200));
        assertThat(teachers.get("content")).hasSize(2);
        assertThat(teachers.get("content")).allSatisfy(
                row -> assertThat(row.get("role").asText()).isEqualTo("TEACHER"));

        JsonNode suspended = toJson(getJson(
                DIRECTORY + "?query=" + s + "&role=TEACHER&status=SUSPENDED&size=50", admin, 200));
        assertThat(suspended.get("content")).hasSize(1);
        assertThat(suspended.get("content").get(0).get("email").asText())
                .isEqualTo("dir-c-" + s + "@test.com");
    }

    @Test
    void invalidParametersAreRejected() throws Exception {
        String admin = adminBearer();

        getJson(DIRECTORY + "?page=-1", admin, 400);
        getJson(DIRECTORY + "?size=0", admin, 400);
        getJson(DIRECTORY + "?size=51", admin, 400);
        getJson(DIRECTORY + "?query=a", admin, 400);
    }

    @Test
    void queryWildcardsAreEscaped() throws Exception {
        createUser("dir-wild-" + suffix() + "@test.com", UserRole.STUDENT, UserStatus.ACTIVE);
        String admin = adminBearer();

        // If % were not escaped, this would match every account.
        JsonNode page = directory("%%", admin);

        assertThat(page.get("totalElements").asLong()).isZero();
    }

    @Test
    void theResponseDoesNotLeakSensitiveFields() throws Exception {
        String s = suffix();
        createUser("dir-safe-" + s + "@test.com", UserRole.STUDENT, UserStatus.ACTIVE);
        String admin = adminBearer();

        JsonNode row = directory(s, admin).get("content").get(0);

        assertThat(row.get("email").asText()).isEqualTo("dir-safe-" + s + "@test.com");
        assertThat(row.has("passwordHash")).isFalse();
        assertThat(row.has("googleSub")).isFalse();
        assertThat(row.has("emailVerifiedAt")).isFalse();
        assertThat(row.get("studentProfileComplete").asBoolean()).isFalse();
        // Jackson omits nulls globally (`default-property-inclusion: non_null`).
        assertThat(row.has("teacherVerificationStatus")).isFalse();
    }

    @Test
    void suspendingBlocksTheNextRequestEvenWithAValidToken() throws Exception {
        User student = createUser("dir-live-" + suffix() + "@test.com",
                UserRole.STUDENT, UserStatus.ACTIVE);
        String studentToken = bearer(student);
        String admin = adminBearer();

        getJson("/api/auth/me", studentToken, 200);

        adminPost(DIRECTORY + "/" + student.getId() + "/suspend", admin, 204);

        getJson("/api/auth/me", studentToken, 401);
    }

    @Test
    void anAdminCannotSuspendItself() throws Exception {
        User admin = createUser("dir-self-" + suffix() + "@test.com",
                UserRole.ADMIN, UserStatus.ACTIVE);

        adminPost(DIRECTORY + "/" + admin.getId() + "/suspend", bearer(admin), 409);
    }

    @Test
    void suspendAndRestoreAreIdempotent() throws Exception {
        String s = suffix();
        User student = createUser("dir-idem-" + s + "@test.com",
                UserRole.STUDENT, UserStatus.ACTIVE);
        String admin = adminBearer();

        adminPost(DIRECTORY + "/" + student.getId() + "/suspend", admin, 204);
        adminPost(DIRECTORY + "/" + student.getId() + "/suspend", admin, 204);
        assertThat(directory(s, admin).get("content").get(0).get("status").asText())
                .isEqualTo("SUSPENDED");

        adminPost(DIRECTORY + "/" + student.getId() + "/restore", admin, 204);
        adminPost(DIRECTORY + "/" + student.getId() + "/restore", admin, 204);
        assertThat(directory(s, admin).get("content").get(0).get("status").asText())
                .isEqualTo("ACTIVE");
    }

    @Test
    void anUnknownAccountAnswers404() throws Exception {
        adminPost(DIRECTORY + "/" + UUID.randomUUID() + "/suspend", adminBearer(), 404);
    }

    @Test
    void aSuspendedTeacherDisappearsFromThePublicProfile() throws Exception {
        String s = suffix();
        User teacher = createUser("dir-pub-" + s + "@test.com", UserRole.TEACHER, UserStatus.ACTIVE);
        String token = bearer(teacher);
        String profile = postJson("/api/teachers/profile", token,
                "{\"name\":\"Dir Profe " + s + "\",\"bio\":\"Bio\"}", 201);
        String teacherId = idOf(profile);
        makePublished(teacher);
        String admin = adminBearer();

        getJson("/api/teachers/" + teacherId, null, 200);

        adminPost(DIRECTORY + "/" + teacher.getId() + "/suspend", admin, 204);
        getJson("/api/teachers/" + teacherId, null, 404);

        adminPost(DIRECTORY + "/" + teacher.getId() + "/restore", admin, 204);
        getJson("/api/teachers/" + teacherId, null, 200);
    }
}
