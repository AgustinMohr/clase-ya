package com.claseya;

import com.claseya.model.StudentProfile;
import com.claseya.model.TeacherProfile;
import com.claseya.model.User;
import com.claseya.model.enums.StudentLevel;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.claseya.model.enums.VerificationStatus;
import com.claseya.student.repository.StudentProfileRepository;
import com.claseya.teacher.repository.TeacherProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** REPORT-001: reports queue, blocking and the contact/message guard. */
@Transactional
class SafetyIntegrationTest extends AbstractWebIntegrationTest {

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private TeacherProfileRepository teacherProfileRepository;

    @Test
    void studentCanReportAndDuplicateOpenReportConflicts() throws Exception {
        User student = newStudent("s@example.com");
        TeacherProfile teacher = visibleTeacher("Ana", "ana@example.com");
        String token = bearer(student);
        String body = reportBody(teacher.getUser().getId().toString(), "HARASSMENT", "Insultos");

        JsonNode created = toJson(postJson("/api/reports", token, body, 201));
        assertThat(created.get("status").asText()).isEqualTo("OPEN");
        assertThat(created.get("reason").asText()).isEqualTo("HARASSMENT");

        postJson("/api/reports", token, body, 409);
    }

    @Test
    void selfReportIsRejected() throws Exception {
        User student = newStudent("s@example.com");

        postJson("/api/reports", bearer(student),
                reportBody(student.getId().toString(), "SPAM", null), 400);
    }

    @Test
    void onlyAdminCanListAndResolveReports() throws Exception {
        User student = newStudent("s@example.com");
        TeacherProfile teacher = visibleTeacher("Ana", "ana@example.com");
        String createdAt = idOf(postJson("/api/reports", bearer(student),
                reportBody(teacher.getUser().getId().toString(), "FAKE_PROFILE", "Perfil falso"), 201));

        getJson("/api/admin/reports", bearer(student), 403);

        String admin = adminBearer();
        JsonNode queue = toJson(getJson("/api/admin/reports?status=OPEN", admin, 200));
        assertThat(queue.get("content").size()).isEqualTo(1);
        assertThat(queue.get("content").get(0).get("reportedDisplayName").asText()).isEqualTo("Ana");
        assertThat(queue.toString()).doesNotContain("ana@example.com");

        patchJson("/api/admin/reports/" + createdAt + "/resolution", admin,
                "{\"status\":\"RESOLVED\"}", 204);
        patchJson("/api/admin/reports/" + createdAt + "/resolution", admin,
                "{\"status\":\"REJECTED\"}", 409);
    }

    @Test
    void blockStopsNewContactsAndMessagesInBothDirections() throws Exception {
        User student = newStudent("s@example.com");
        TeacherProfile teacher = visibleTeacher("Ana", "ana@example.com");
        String studentToken = bearer(student);
        String teacherToken = bearer(teacher.getUser());

        postEmpty("/api/blocks/" + teacher.getUser().getId(), studentToken, 204);
        JsonNode blocked = toJson(getJson("/api/blocks", studentToken, 200));
        assertThat(blocked.get(0).get("userId").asText())
                .isEqualTo(teacher.getUser().getId().toString());

        createContact(studentToken, teacher.getId().toString(), 403);

        deleteJson("/api/blocks/" + teacher.getUser().getId(), studentToken, 204);
        String conversationId = toJson(createContact(studentToken,
                teacher.getId().toString(), 201)).get("id").asText();
        sendMessage(studentToken, conversationId, "hola", 201);

        postEmpty("/api/blocks/" + student.getId(), teacherToken, 204);
        sendMessage(studentToken, conversationId, "bloqueado", 403);
        sendMessage(teacherToken, conversationId, "bloqueado", 403);

        deleteJson("/api/blocks/" + student.getId(), teacherToken, 204);
        sendMessage(studentToken, conversationId, "de nuevo", 201);
    }

    @Test
    void selfBlockIsRejected() throws Exception {
        User student = newStudent("s@example.com");

        postEmpty("/api/blocks/" + student.getId(), bearer(student), 400);
    }

    // ------------------------------------------------------------------ helpers

    private User newStudent(String email) {
        User user = createUser(email, UserRole.STUDENT, UserStatus.ACTIVE);
        user.setName("Student " + email);
        userRepository.saveAndFlush(user);
        StudentProfile profile = new StudentProfile();
        profile.setUser(user);
        profile.setLevel(StudentLevel.ADULTO);
        studentProfileRepository.saveAndFlush(profile);
        return user;
    }

    private TeacherProfile visibleTeacher(String name, String email) throws Exception {
        User user = createUser(email, UserRole.TEACHER, UserStatus.ACTIVE);
        user.setName(name);
        userRepository.saveAndFlush(user);
        TeacherProfile teacher = new TeacherProfile();
        teacher.setUser(user);
        teacher.setBio("Bio " + name);
        teacher.setVerificationStatus(VerificationStatus.VERIFIED);
        teacher.setRatingCount(0);
        teacher.setRatingAverage(java.math.BigDecimal.ZERO);
        teacherProfileRepository.saveAndFlush(teacher);
        makePublished(user);
        return teacher;
    }

    private static String reportBody(String reportedUserId, String reason, String description) {
        return description == null
                ? "{\"reportedUserId\":\"%s\",\"reason\":\"%s\"}".formatted(reportedUserId, reason)
                : "{\"reportedUserId\":\"%s\",\"reason\":\"%s\",\"description\":\"%s\"}"
                        .formatted(reportedUserId, reason, description);
    }

    private String createContact(String token, String teacherId, int expected) throws Exception {
        return postJson("/api/conversations", token,
                "{\"teacherId\":\"%s\"}".formatted(teacherId), expected);
    }

    private void sendMessage(String token, String conversationId, String content, int expected)
            throws Exception {
        postJson("/api/conversations/" + conversationId + "/messages", token,
                "{\"content\":\"%s\"}".formatted(content), expected);
    }

    private void postEmpty(String uri, String token, int expected) throws Exception {
        mockMvc.perform(post(uri).header("Authorization", token))
                .andExpect(status().is(expected));
    }

    private void patchJson(String uri, String token, String body, int expected) throws Exception {
        mockMvc.perform(patch(uri)
                        .header("Authorization", token)
                        .contentType("application/json;charset=UTF-8")
                        .content(body))
                .andExpect(status().is(expected));
    }
}
