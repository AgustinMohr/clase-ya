package com.claseya;

import com.claseya.model.TeacherEducation;
import com.claseya.model.TeacherProfile;
import com.claseya.model.User;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.claseya.model.enums.VerificationStatus;
import com.claseya.teacher.repository.TeacherEducationRepository;
import com.claseya.teacher.repository.TeacherProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP contract for the teacher verification area and the admin review endpoints. */
@Transactional
class VerificationApiIntegrationTest extends AbstractWebIntegrationTest {

    private static final byte[] PDF = "%PDF-1.4\napi test evidence\n".getBytes(StandardCharsets.UTF_8);

    @Autowired private TeacherProfileRepository teacherProfileRepository;
    @Autowired private TeacherEducationRepository teacherEducationRepository;

    @Test
    void teacher_canUploadSubmitAndSeeOwnStatus() throws Exception {
        User teacher = createUser("api-teacher+" + UUID.randomUUID() + "@test.com", UserRole.TEACHER, UserStatus.ACTIVE);
        String token = bearer(teacher);
        TeacherEducation education = credentialFor(profileFor(teacher));

        upload(token, education.getId());
        String submitted = postJson("/api/teachers/me/education/" + education.getId() + "/submit", token, "{}", 200);
        assertThat(toJson(submitted).get("status").asText()).isEqualTo("UNDER_REVIEW");

        String view = getJson("/api/teachers/me/verification", token, 200);
        assertThat(toJson(view).get("profileStatus").asText()).isEqualTo("UNDER_REVIEW");
        assertThat(toJson(view).get("credentials").get(0).get("documentCount").asInt()).isEqualTo(1);
    }

    @Test
    void submitWithoutDocuments_returns400() throws Exception {
        User teacher = createUser("api-teacher+" + UUID.randomUUID() + "@test.com", UserRole.TEACHER, UserStatus.ACTIVE);
        String token = bearer(teacher);
        TeacherEducation education = credentialFor(profileFor(teacher));

        postJson("/api/teachers/me/education/" + education.getId() + "/submit", token, "{}", 400);
    }

    @Test
    void teacherVerificationEndpoints_requireAuthentication() throws Exception {
        getJson("/api/teachers/me/verification", null, 401);
    }

    @Test
    void adminRoutes_rejectNonAdmin() throws Exception {
        User student = createUser("api-student+" + UUID.randomUUID() + "@test.com", UserRole.STUDENT, UserStatus.ACTIVE);
        getJson("/api/admin/verifications", bearer(student), 403);
    }

    @Test
    void admin_seesTheQueueAndCanDecide() throws Exception {
        User teacher = createUser("api-teacher+" + UUID.randomUUID() + "@test.com", UserRole.TEACHER, UserStatus.ACTIVE);
        String teacherToken = bearer(teacher);
        TeacherProfile profile = profileFor(teacher);
        TeacherEducation education = credentialFor(profile);
        upload(teacherToken, education.getId());
        postJson("/api/teachers/me/education/" + education.getId() + "/submit", teacherToken, "{}", 200);

        String adminToken = adminBearer();
        String queue = getJson("/api/admin/verifications", adminToken, 200);
        assertThat(queue).contains(education.getId().toString());

        String decided = postJson(
                "/api/admin/verifications/" + profile.getId() + "/credentials/" + education.getId() + "/decision",
                adminToken,
                """
                {"decision":"VERIFIED","method":"INSTITUTION_CHECK","reason":"constancia institucional"}
                """, 200);
        assertThat(toJson(decided).get("newStatus").asText()).isEqualTo("VERIFIED");

        assertThat(toJson(getJson("/api/teachers/me/verification", teacherToken, 200))
                .get("profileStatus").asText()).isEqualTo("VERIFIED");
    }

    @Test
    void adminDetail_exposesCredentialsAndAuditHistory() throws Exception {
        User teacher = createUser("api-teacher+" + UUID.randomUUID() + "@test.com", UserRole.TEACHER, UserStatus.ACTIVE);
        String teacherToken = bearer(teacher);
        TeacherProfile profile = profileFor(teacher);
        TeacherEducation education = credentialFor(profile);
        upload(teacherToken, education.getId());
        postJson("/api/teachers/me/education/" + education.getId() + "/submit", teacherToken, "{}", 200);

        String adminToken = adminBearer();
        postJson("/api/admin/verifications/" + profile.getId() + "/credentials/" + education.getId() + "/decision",
                adminToken, """
                {"decision":"REJECTED","method":"DOCUMENT_ANALYSIS","reason":"ilegible"}
                """, 200);

        String detail = getJson("/api/admin/verifications/" + profile.getId(), adminToken, 200);
        assertThat(toJson(detail).get("history").size()).isEqualTo(1);
        assertThat(toJson(detail).get("documents").size()).isEqualTo(1);
    }

    @Test
    void revoke_requiresAReason() throws Exception {
        User teacher = createUser("api-teacher+" + UUID.randomUUID() + "@test.com", UserRole.TEACHER, UserStatus.ACTIVE);
        String teacherToken = bearer(teacher);
        TeacherProfile profile = profileFor(teacher);
        TeacherEducation education = credentialFor(profile);
        upload(teacherToken, education.getId());
        postJson("/api/teachers/me/education/" + education.getId() + "/submit", teacherToken, "{}", 200);

        String adminToken = adminBearer();
        postJson("/api/admin/verifications/" + profile.getId() + "/credentials/" + education.getId() + "/decision",
                adminToken, """
                {"decision":"VERIFIED","method":"INSTITUTION_CHECK"}
                """, 200);

        postJson("/api/admin/verifications/" + profile.getId() + "/revoke", adminToken, """
                {"method":"OFFICIAL_REGISTRY","reason":""}
                """, 400);
    }

    @Test
    void anotherTeacher_cannotDownloadTheDocument() throws Exception {
        User teacher = createUser("api-teacher+" + UUID.randomUUID() + "@test.com", UserRole.TEACHER, UserStatus.ACTIVE);
        TeacherEducation education = credentialFor(profileFor(teacher));
        String documentId = upload(bearer(teacher), education.getId());

        User other = createUser("api-other+" + UUID.randomUUID() + "@test.com", UserRole.TEACHER, UserStatus.ACTIVE);
        profileFor(other);

        getJson("/api/teachers/me/documents/" + documentId + "/content", bearer(other), 404);
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private TeacherProfile profileFor(User owner) {
        TeacherProfile profile = new TeacherProfile();
        profile.setUser(owner);
        profile.setVerificationStatus(VerificationStatus.PENDING);
        return teacherProfileRepository.saveAndFlush(profile);
    }

    private TeacherEducation credentialFor(TeacherProfile owner) {
        TeacherEducation education = new TeacherEducation();
        education.setTeacher(owner);
        education.setInstitution("UTN");
        education.setDegree("Ingeniero en Sistemas");
        education.setStartYear(2005);
        education.setEndYear(2010);
        return teacherEducationRepository.saveAndFlush(education);
    }

    private String upload(String token, UUID educationId) throws Exception {
        String response = mockMvc.perform(multipart("/api/teachers/me/education/" + educationId + "/documents")
                        .file(new MockMultipartFile("file", "diploma.pdf", "application/pdf", PDF))
                        .param("type", "DIPLOMA")
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return toJson(response).get("id").asText();
    }
}
