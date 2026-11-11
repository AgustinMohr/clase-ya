package com.claseya;

import com.claseya.academic.repository.AcademicUnitRepository;
import com.claseya.academic.repository.CareerRepository;
import com.claseya.academic.repository.UniversityRepository;
import com.claseya.model.AcademicUnit;
import com.claseya.model.Career;
import com.claseya.model.StudentProfile;
import com.claseya.model.TeacherProfile;
import com.claseya.model.University;
import com.claseya.model.User;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.claseya.review.repository.ReviewModerationEventRepository;
import com.claseya.review.repository.ReviewRepository;
import com.claseya.student.repository.StudentProfileRepository;
import com.claseya.teacher.repository.TeacherProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class ReviewIntegrationTest extends AbstractWebIntegrationTest {

    @Autowired private UniversityRepository universityRepository;
    @Autowired private AcademicUnitRepository academicUnitRepository;
    @Autowired private CareerRepository careerRepository;
    @Autowired private StudentProfileRepository studentProfileRepository;
    @Autowired private TeacherProfileRepository teacherProfileRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private ReviewModerationEventRepository moderationEventRepository;

    private University university;
    private Career career;

    private University university() {
        if (university != null) {
            return university;
        }
        university = new University();
        university.setName("UNL");
        university.setSlug("unl-" + UUID.randomUUID());
        university.setActive(true);
        universityRepository.saveAndFlush(university);
        return university;
    }

    private Career career() {
        if (career != null) {
            return career;
        }
        AcademicUnit unit = new AcademicUnit();
        unit.setUniversity(university());
        unit.setName("FICH");
        unit.setActive(true);
        academicUnitRepository.saveAndFlush(unit);
        career = new Career();
        career.setAcademicUnit(unit);
        career.setName("Ingenieria en Informatica");
        career.setSlug("isi-" + UUID.randomUUID());
        career.setActive(true);
        careerRepository.saveAndFlush(career);
        return career;
    }

    private User newStudent(String email) {
        User user = createUser(email, UserRole.STUDENT, UserStatus.ACTIVE);
        user.setName("Student " + email);
        userRepository.saveAndFlush(user);
        StudentProfile profile = new StudentProfile();
        profile.setUser(user);
        profile.setUniversity(university());
        profile.setCareer(career());
        profile.setCurrentYear(2);
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
        teacher.setRatingAverage(BigDecimal.ZERO);
        teacher.setRatingCount(0);
        teacherProfileRepository.saveAndFlush(teacher);
        makePublished(user);
        return teacher;
    }

    private String startConversation(User student, TeacherProfile teacher) throws Exception {
        return idOf(postJson("/api/conversations", bearer(student),
                "{\"teacherId\":\"%s\"}".formatted(teacher.getId()), 201));
    }

    private void teacherReplies(TeacherProfile teacher, String conversationId) throws Exception {
        postJson("/api/conversations/" + conversationId + "/messages", bearer(teacher.getUser()),
                "{\"content\":\"Hola, puedo ayudarte\"}", 201);
    }

    private String createReview(User student, TeacherProfile teacher, int rating, String comment)
            throws Exception {
        return idOf(postJson("/api/teachers/" + teacher.getId() + "/reviews", bearer(student),
                "{\"rating\":%d,\"comment\":\"%s\"}".formatted(rating, comment), 201));
    }

    @Test
    void studentWithTeacherReply_createsReview_andUpdatesAggregate() throws Exception {
        User student = newStudent("student@example.com");
        TeacherProfile teacher = visibleTeacher("Ana", "ana@example.com");
        String conversationId = startConversation(student, teacher);
        teacherReplies(teacher, conversationId);

        String reviewId = createReview(student, teacher, 5, "Muy clara");

        JsonNode body = toJson(getJson("/api/teachers/" + teacher.getId() + "/reviews", null, 200));
        assertThat(body.get("totalElements").asInt()).isEqualTo(1);
        assertThat(body.get("content").get(0).get("id").asText()).isEqualTo(reviewId);
        assertThat(body.get("content").get(0).get("authorDisplayName").asText()).isEqualTo("Estudiante");

        TeacherProfile reloaded = teacherProfileRepository.findById(teacher.getId()).orElseThrow();
        assertThat(reloaded.getRatingCount()).isEqualTo(1);
        assertThat(reloaded.getRatingAverage()).isEqualByComparingTo("5.00");
    }

    @Test
    void studentWithoutTeacherReply_cannotReview() throws Exception {
        User student = newStudent("student@example.com");
        TeacherProfile teacher = visibleTeacher("Ana", "ana@example.com");
        startConversation(student, teacher);

        postJson("/api/teachers/" + teacher.getId() + "/reviews", bearer(student),
                "{\"rating\":5,\"comment\":\"Muy clara\"}", 403);

        assertThat(reviewRepository.count()).isZero();
    }

    @Test
    void duplicateReview_returns409() throws Exception {
        User student = newStudent("student@example.com");
        TeacherProfile teacher = visibleTeacher("Ana", "ana@example.com");
        String conversationId = startConversation(student, teacher);
        teacherReplies(teacher, conversationId);
        createReview(student, teacher, 5, "Muy clara");

        postJson("/api/teachers/" + teacher.getId() + "/reviews", bearer(student),
                "{\"rating\":4,\"comment\":\"Otra\"}", 409);
    }

    @Test
    void authorCanEdit_otherStudentGets404() throws Exception {
        User author = newStudent("author@example.com");
        User other = newStudent("other@example.com");
        TeacherProfile teacher = visibleTeacher("Ana", "ana@example.com");
        String conversationId = startConversation(author, teacher);
        teacherReplies(teacher, conversationId);
        String reviewId = createReview(author, teacher, 3, "Ok");

        putJson("/api/reviews/" + reviewId, bearer(author),
                "{\"rating\":5,\"comment\":\"Mejoró\"}", 200);
        putJson("/api/reviews/" + reviewId, bearer(other),
                "{\"rating\":1,\"comment\":\"No\"}", 404);

        TeacherProfile reloaded = teacherProfileRepository.findById(teacher.getId()).orElseThrow();
        assertThat(reloaded.getRatingAverage()).isEqualByComparingTo("5.00");
    }

    @Test
    void teacherCanRespond_andHiddenReviewLeavesPublicListAndAggregate() throws Exception {
        User student = newStudent("student@example.com");
        TeacherProfile teacher = visibleTeacher("Ana", "ana@example.com");
        String conversationId = startConversation(student, teacher);
        teacherReplies(teacher, conversationId);
        String reviewId = createReview(student, teacher, 4, "Buena");

        postJson("/api/reviews/" + reviewId + "/response", bearer(teacher.getUser()),
                "{\"response\":\"Gracias\"}", 200);
        JsonNode afterResponse = toJson(getJson("/api/teachers/" + teacher.getId() + "/reviews", null, 200));
        assertThat(afterResponse.get("content").get(0).get("teacherResponse").asText())
                .isEqualTo("Gracias");

        String admin = adminBearer();
        postJson("/api/admin/reviews/" + reviewId + "/hide", admin,
                "{\"reason\":\"Contenido ofensivo\"}", 204);

        JsonNode hidden = toJson(getJson("/api/teachers/" + teacher.getId() + "/reviews", null, 200));
        assertThat(hidden.get("totalElements").asInt()).isZero();
        TeacherProfile hiddenTeacher = teacherProfileRepository.findById(teacher.getId()).orElseThrow();
        assertThat(hiddenTeacher.getRatingCount()).isZero();

        postJson("/api/admin/reviews/" + reviewId + "/restore", admin, "", 204);
        JsonNode restored = toJson(getJson("/api/teachers/" + teacher.getId() + "/reviews", null, 200));
        assertThat(restored.get("totalElements").asInt()).isEqualTo(1);
        assertThat(moderationEventRepository.count()).isEqualTo(2);
    }

    @Test
    void nonOwnerCannotRespond_andHiddenTeacherIs404() throws Exception {
        User student = newStudent("student@example.com");
        TeacherProfile teacher = visibleTeacher("Ana", "ana@example.com");
        TeacherProfile otherTeacher = visibleTeacher("Beto", "beto@example.com");
        String conversationId = startConversation(student, teacher);
        teacherReplies(teacher, conversationId);
        String reviewId = createReview(student, teacher, 5, "Muy clara");

        postJson("/api/reviews/" + reviewId + "/response", bearer(otherTeacher.getUser()),
                "{\"response\":\"No soy el dueño\"}", 404);

        User suspended = teacher.getUser();
        suspended.setStatus(UserStatus.SUSPENDED);
        userRepository.saveAndFlush(suspended);
        getJson("/api/teachers/" + teacher.getId() + "/reviews", null, 404);
    }

    @Test
    void invalidRatingAndTooLongComment_areRejected() throws Exception {
        User student = newStudent("student@example.com");
        TeacherProfile teacher = visibleTeacher("Ana", "ana@example.com");
        String conversationId = startConversation(student, teacher);
        teacherReplies(teacher, conversationId);

        postJson("/api/teachers/" + teacher.getId() + "/reviews", bearer(student),
                "{\"rating\":6}", 400);
        postJson("/api/teachers/" + teacher.getId() + "/reviews", bearer(student),
                "{\"rating\":5,\"comment\":\"" + "x".repeat(1001) + "\"}", 400);
    }

    @Test
    void publicReads_doNotRequireAuthentication_butOwnReviewDoes() throws Exception {
        User student = newStudent("student@example.com");
        TeacherProfile teacher = visibleTeacher("Ana", "ana@example.com");
        getJson("/api/teachers/" + teacher.getId() + "/reviews", null, 200);

        mockMvc.perform(get("/api/teachers/" + teacher.getId() + "/reviews/me"))
                .andExpect(status().isUnauthorized());
        getJson("/api/teachers/" + teacher.getId() + "/reviews/me", bearer(student), 404);
    }
}
