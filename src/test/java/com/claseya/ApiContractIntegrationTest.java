package com.claseya;

import com.claseya.academic.repository.AcademicUnitRepository;
import com.claseya.academic.repository.CareerRepository;
import com.claseya.academic.repository.SubjectRepository;
import com.claseya.academic.repository.UniversityRepository;
import com.claseya.model.AcademicUnit;
import com.claseya.model.Career;
import com.claseya.model.StudentProfile;
import com.claseya.model.Subject;
import com.claseya.model.TeacherProfile;
import com.claseya.model.University;
import com.claseya.model.User;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.claseya.model.enums.VerificationStatus;
import com.claseya.student.repository.StudentProfileRepository;
import com.claseya.teacher.repository.TeacherProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TEST-01: freezes the JSON field sets of the main DTOs. A payload change that is not reflected
 * here fails the suite instead of reaching the SPA or a third-party consumer by accident.
 */
@Transactional
class ApiContractIntegrationTest extends AbstractWebIntegrationTest {

    @Autowired
    private SubjectRepository subjectRepository;
    @Autowired
    private TeacherProfileRepository teacherProfileRepository;
    @Autowired
    private StudentProfileRepository studentProfileRepository;
    @Autowired
    private UniversityRepository universityRepository;
    @Autowired
    private AcademicUnitRepository academicUnitRepository;
    @Autowired
    private CareerRepository careerRepository;

    private static Set<String> fields(JsonNode node) {
        Set<String> names = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    void sessionContract_exposesOnlyIdNameAndRole() throws Exception {
        User student = createUser("contract-session@example.com", UserRole.STUDENT, UserStatus.ACTIVE);
        student.setName("Estudiante Contract");
        userRepository.saveAndFlush(student);

        String body = getJson("/api/auth/me", bearer(student), 200);
        JsonNode session = toJson(body);

        assertThat(fields(session)).containsExactlyInAnyOrder("id", "name", "role");
        assertThat(body).doesNotContain("contract-session@example.com")
                .doesNotContain("passwordHash");
    }

    @Test
    void teacherCardsContract_freezesSummaryAndDetailFields() throws Exception {
        User user = createUser("contract-teacher@example.com", UserRole.TEACHER, UserStatus.ACTIVE);
        user.setName("Profesor Contract");
        userRepository.saveAndFlush(user);

        TeacherProfile profile = new TeacherProfile();
        profile.setUser(user);
        profile.setBio("Bio contract");
        profile.setVerificationStatus(VerificationStatus.VERIFIED);
        profile.setCity("Santa Fe");
        profile.setPricePerHour(new BigDecimal("15000.00"));
        profile.setPhotoUrl("https://example.com/contract.jpg");
        profile.setAvailabilityNote("Tardes");
        profile.setAddress("Calle privada 123");
        profile.setRatingAverage(new BigDecimal("4.5"));
        profile.setRatingCount(3);
        teacherProfileRepository.saveAndFlush(profile);
        makePublished(user);

        JsonNode search = toJson(getJson("/api/teachers?size=50", null, 200));
        assertThat(fields(search)).containsExactlyInAnyOrder(
                "content", "page", "size", "totalElements", "totalPages");

        JsonNode card = null;
        for (JsonNode item : search.get("content")) {
            if (profile.getId().toString().equals(item.get("id").asText())) {
                card = item;
                break;
            }
        }
        assertThat(card).as("the contract teacher appears in search").isNotNull();
        assertThat(fields(card)).containsExactlyInAnyOrder(
                "id", "displayName", "bio", "ratingAverage", "ratingCount",
                "verificationStatus", "pricePerHour", "city", "photoUrl",
                "modalities", "subjects");
        assertThat(fields(card.get("subjects").get(0))).containsExactlyInAnyOrder(
                "careerSubjectId", "subjectId", "subjectName", "careerId", "careerName",
                "universityId", "universityName");

        String detailBody = getJson("/api/teachers/" + profile.getId(), null, 200);
        JsonNode detail = toJson(detailBody);
        assertThat(fields(detail)).containsExactlyInAnyOrder(
                "id", "displayName", "bio", "verificationStatus", "ratingAverage", "ratingCount",
                "pricePerHour", "city", "photoUrl", "availabilityNote", "modalities", "subjects",
                "education");
        assertThat(detailBody).doesNotContain("contract-teacher@example.com")
                .doesNotContain("Calle privada 123")
                .doesNotContain("latitude")
                .doesNotContain("longitude");
    }

    @Test
    void subjectPageContract_freezesPageAndSubjectFields() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Subject subject = new Subject();
        subject.setName("Contract " + suffix);
        subject.setNormalizedName("contract-" + suffix);
        subject.setSlug("contract-" + suffix);
        subject.setDescription("Materia de contrato");
        subject.setActive(true);
        subjectRepository.saveAndFlush(subject);

        JsonNode page = toJson(getJson("/api/subjects?query=" + suffix + "&size=1", null, 200));

        assertThat(fields(page)).containsExactlyInAnyOrder(
                "content", "page", "size", "totalElements", "totalPages");
        assertThat(page.get("content")).hasSize(1);
        assertThat(fields(page.get("content").get(0))).containsExactlyInAnyOrder(
                "id", "name", "slug", "description", "active", "createdAt", "updatedAt");
    }

    @Test
    void conversationThreadContract_freezesThreadAndMessageFields() throws Exception {
        User student = studentWithProfile("contract-student@example.com");
        TeacherProfile teacher = publishedTeacher("Profesor Thread", "contract-thread@example.com");

        String conversationId = idOf(postJson("/api/conversations", bearer(student),
                """
                {"teacherId":"%s","message":"Hola"}
                """.formatted(teacher.getId()), 201));

        JsonNode thread = toJson(getJson(
                "/api/conversations/" + conversationId + "/thread", bearer(student), 200));

        assertThat(fields(thread)).containsExactlyInAnyOrder(
                "id", "otherParticipant", "unreadCount", "updatedAt", "messages", "hasMore",
                "nextBefore");
        assertThat(fields(thread.get("otherParticipant")))
                .containsExactlyInAnyOrder("id", "displayName", "role");
        assertThat(fields(thread.get("messages").get(0))).containsExactlyInAnyOrder(
                "id", "conversationId", "senderId", "senderDisplayName", "content", "createdAt");
        assertThat(thread.get("messages").get(0).has("readAt")).isFalse();
    }

    // ------------------------------------------------------------------ helpers

    private User studentWithProfile(String email) {
        User student = createUser(email, UserRole.STUDENT, UserStatus.ACTIVE);
        student.setName("Estudiante Contract");
        userRepository.saveAndFlush(student);

        University university = new University();
        university.setName("UNL " + UUID.randomUUID());
        university.setSlug("unl-contract-" + UUID.randomUUID());
        university.setActive(true);
        universityRepository.saveAndFlush(university);

        AcademicUnit unit = new AcademicUnit();
        unit.setUniversity(university);
        unit.setName("FICH");
        unit.setActive(true);
        academicUnitRepository.saveAndFlush(unit);

        Career career = new Career();
        career.setAcademicUnit(unit);
        career.setName("Informatica");
        career.setSlug("isi-contract-" + UUID.randomUUID());
        career.setActive(true);
        careerRepository.saveAndFlush(career);

        StudentProfile profile = new StudentProfile();
        profile.setUser(student);
        profile.setUniversity(university);
        profile.setCareer(career);
        profile.setCurrentYear(2);
        studentProfileRepository.saveAndFlush(profile);
        return student;
    }

    private TeacherProfile publishedTeacher(String name, String email) throws Exception {
        User user = createUser(email, UserRole.TEACHER, UserStatus.ACTIVE);
        user.setName(name);
        userRepository.saveAndFlush(user);

        TeacherProfile teacher = new TeacherProfile();
        teacher.setUser(user);
        teacher.setBio("Bio de " + name);
        teacher.setVerificationStatus(VerificationStatus.VERIFIED);
        teacher.setRatingAverage(BigDecimal.ZERO);
        teacher.setRatingCount(0);
        teacherProfileRepository.saveAndFlush(teacher);
        makePublished(user);
        return teacher;
    }
}
