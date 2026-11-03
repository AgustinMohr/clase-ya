package com.claseya;

import com.claseya.model.TeacherEducation;
import com.claseya.model.TeacherProfile;
import com.claseya.model.User;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.claseya.model.enums.VerificationStatus;
import com.claseya.teacher.repository.TeacherEducationRepository;
import com.claseya.teacher.repository.TeacherProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * VERIF-001: per-document review mark and the decision gate. The gate is the point of the
 * checkboxes: a credential is never verified with evidence nobody looked at.
 */
@Transactional
class AdminVerificationReviewIntegrationTest extends AbstractWebIntegrationTest {

    private static final byte[] PDF = "%PDF-1.4\nreview test\n".getBytes(StandardCharsets.UTF_8);

    @Autowired private TeacherProfileRepository teacherProfileRepository;
    @Autowired private TeacherEducationRepository teacherEducationRepository;

    private record Fixture(User teacher, TeacherProfile profile, TeacherEducation credential) {
    }

    private Fixture fixture() {
        User teacher = createUser("review-" + UUID.randomUUID() + "@test.com",
                UserRole.TEACHER, UserStatus.ACTIVE);
        TeacherProfile profile = new TeacherProfile();
        profile.setUser(teacher);
        profile.setVerificationStatus(VerificationStatus.UNDER_REVIEW);
        teacherProfileRepository.saveAndFlush(profile);

        TeacherEducation credential = new TeacherEducation();
        credential.setTeacher(profile);
        credential.setInstitution("UTN");
        credential.setDegree("Ingeniero en Sistemas");
        credential.setStartYear(2005);
        credential.setEndYear(2010);
        credential.setVerificationStatus(VerificationStatus.UNDER_REVIEW);
        credential.setSubmittedAt(Instant.now());
        teacherEducationRepository.saveAndFlush(credential);
        return new Fixture(teacher, profile, credential);
    }

    private String upload(User teacher, UUID educationId) throws Exception {
        String response = mockMvc.perform(multipart("/api/teachers/me/education/" + educationId + "/documents")
                        .file(new MockMultipartFile("file", "diploma.pdf", "application/pdf", PDF))
                        .param("type", "DIPLOMA")
                        .header("Authorization", bearer(teacher)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return toJson(response).get("id").asText();
    }

    private void markReviewed(String admin, String documentId) throws Exception {
        mockMvc.perform(post("/api/admin/documents/" + documentId + "/reviewed")
                        .header("Authorization", admin))
                .andExpect(status().isNoContent());
    }

    private JsonNode detail(String admin, UUID teacherProfileId) throws Exception {
        return toJson(getJson("/api/admin/verifications/" + teacherProfileId, admin, 200));
    }

    private JsonNode documentOf(JsonNode detail, String documentId) {
        for (JsonNode document : detail.get("documents")) {
            if (document.get("id").asText().equals(documentId)) {
                return document;
            }
        }
        throw new AssertionError("document not present in the detail");
    }

    @Test
    void markingIsIdempotentAndUnmarkingClearsTheReviewer() throws Exception {
        Fixture fixture = fixture();
        User reviewer = createUser("admin+" + UUID.randomUUID() + "@test.com",
                UserRole.ADMIN, UserStatus.ACTIVE);
        reviewer.setName("Admin Revisor");
        userRepository.saveAndFlush(reviewer);
        String admin = bearer(reviewer);
        String documentId = upload(fixture.teacher(), fixture.credential().getId());

        markReviewed(admin, documentId);
        markReviewed(admin, documentId);

        JsonNode document = documentOf(detail(admin, fixture.profile().getId()), documentId);
        assertThat(document.get("scanStatus").asText()).isEqualTo("UNSCANNED");
        assertThat(document.has("reviewedAt")).isTrue();
        assertThat(document.get("reviewedByName").asText()).isEqualTo("Admin Revisor");

        deleteJson("/api/admin/documents/" + documentId + "/reviewed", admin, 204);
        deleteJson("/api/admin/documents/" + documentId + "/reviewed", admin, 204);

        JsonNode cleared = documentOf(detail(admin, fixture.profile().getId()), documentId);
        assertThat(cleared.has("reviewedAt")).isFalse();
        assertThat(cleared.has("reviewedByName")).isFalse();
    }

    @Test
    void aTeacherCannotMarkDocuments() throws Exception {
        Fixture fixture = fixture();
        String documentId = upload(fixture.teacher(), fixture.credential().getId());

        mockMvc.perform(post("/api/admin/documents/" + documentId + "/reviewed")
                        .header("Authorization", bearer(fixture.teacher())))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnknownDocumentAnswers404() throws Exception {
        mockMvc.perform(post("/api/admin/documents/" + UUID.randomUUID() + "/reviewed")
                        .header("Authorization", adminBearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void verifyingWithUnreviewedEvidenceIsRejected() throws Exception {
        Fixture fixture = fixture();
        String admin = adminBearer();
        upload(fixture.teacher(), fixture.credential().getId());

        postJson("/api/admin/verifications/" + fixture.profile().getId()
                        + "/credentials/" + fixture.credential().getId() + "/decision",
                admin,
                "{\"decision\":\"VERIFIED\",\"method\":\"DOCUMENT_ANALYSIS\"}", 400);

        JsonNode credential = detail(admin, fixture.profile().getId()).get("credentials").get(0);
        assertThat(credential.get("status").asText()).isEqualTo("UNDER_REVIEW");
    }

    @Test
    void verifyingAfterReviewingEveryDocumentSucceeds() throws Exception {
        Fixture fixture = fixture();
        String admin = adminBearer();
        String documentId = upload(fixture.teacher(), fixture.credential().getId());
        markReviewed(admin, documentId);

        postJson("/api/admin/verifications/" + fixture.profile().getId()
                        + "/credentials/" + fixture.credential().getId() + "/decision",
                admin,
                "{\"decision\":\"VERIFIED\",\"method\":\"DOCUMENT_ANALYSIS\"}", 200);

        JsonNode detail = detail(admin, fixture.profile().getId());
        assertThat(detail.get("credentials").get(0).get("status").asText()).isEqualTo("VERIFIED");
        assertThat(detail.get("profileStatus").asText()).isEqualTo("VERIFIED");
    }

    @Test
    void rejectingDoesNotRequireReview() throws Exception {
        Fixture fixture = fixture();
        String admin = adminBearer();
        upload(fixture.teacher(), fixture.credential().getId());

        postJson("/api/admin/verifications/" + fixture.profile().getId()
                        + "/credentials/" + fixture.credential().getId() + "/decision",
                admin,
                "{\"decision\":\"REJECTED\",\"method\":\"DOCUMENT_ANALYSIS\"}", 200);

        assertThat(detail(admin, fixture.profile().getId())
                .get("credentials").get(0).get("status").asText()).isEqualTo("REJECTED");
    }
}
