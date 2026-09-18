package com.claseya;

import com.claseya.model.User;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class StudentProfileIntegrationTest extends AbstractWebIntegrationTest {

    private record Seed(String universityId, String careerId) {
    }

    private Seed seedUniversityCareer(String universityName, String unitName, String careerName) throws Exception {
        String admin = adminBearer();
        String universityId = idOf(postJson("/api/universities", admin,
                """
                {"name":"%s"}
                """.formatted(universityName), 201));
        String unitId = idOf(postJson("/api/universities/" + universityId + "/academic-units", admin,
                """
                {"name":"%s"}
                """.formatted(unitName), 201));
        String careerId = idOf(postJson("/api/academic-units/" + unitId + "/careers", admin,
                """
                {"name":"%s"}
                """.formatted(careerName), 201));
        return new Seed(universityId, careerId);
    }

    private String profileBody(String universityId, String careerId, int year) {
        return """
                {"universityId":"%s","careerId":"%s","currentYear":%d,"bio":"Estudiante"}
                """.formatted(universityId, careerId, year);
    }

    @Test
    void student_canCreateOwnProfile_thenGetMe() throws Exception {
        Seed unl = seedUniversityCareer("UNL", "FICH", "Ingeniería en Informática");
        User student = createUser("stu@example.com", UserRole.STUDENT, UserStatus.ACTIVE);

        String created = postJson("/api/students/profile", bearer(student),
                profileBody(unl.universityId, unl.careerId, 2), 201);
        assertThat(toJson(created).get("email").asText()).isEqualTo("stu@example.com");

        String me = getJson("/api/students/me", bearer(student), 200);
        assertThat(toJson(me).get("careerName").asText()).startsWith("Ingenier");
        assertThat(toJson(me).get("currentYear").asInt()).isEqualTo(2);
    }

    @Test
    void teacher_cannotCreateStudentProfile_returns403() throws Exception {
        Seed unl = seedUniversityCareer("UNL", "FICH", "Ingeniería en Informática");
        User teacher = createUser("tea@example.com", UserRole.TEACHER, UserStatus.ACTIVE);

        postJson("/api/students/profile", bearer(teacher),
                profileBody(unl.universityId, unl.careerId, 2), 403);
    }

    @Test
    void studentProfile_alreadyExists_returns409() throws Exception {
        Seed unl = seedUniversityCareer("UNL", "FICH", "Ingeniería en Informática");
        User student = createUser("stu@example.com", UserRole.STUDENT, UserStatus.ACTIVE);
        String token = bearer(student);

        postJson("/api/students/profile", token, profileBody(unl.universityId, unl.careerId, 2), 201);
        postJson("/api/students/profile", token, profileBody(unl.universityId, unl.careerId, 3), 409);
    }

    @Test
    void student_cannotUseCareerOfAnotherUniversity_returns400() throws Exception {
        Seed unl = seedUniversityCareer("UNL", "FICH", "Ingeniería en Informática");
        Seed utn = seedUniversityCareer("UTN", "FR Santa Fe", "Ingeniería en Sistemas");
        User student = createUser("stu@example.com", UserRole.STUDENT, UserStatus.ACTIVE);

        // university from UNL, career from UTN -> invalid association.
        postJson("/api/students/profile", bearer(student),
                profileBody(unl.universityId, utn.careerId, 2), 400);
    }

    @Test
    void student_cannotSeeAnotherStudentsProfile_returns404() throws Exception {
        Seed unl = seedUniversityCareer("UNL", "FICH", "Ingeniería en Informática");
        User alice = createUser("alice@example.com", UserRole.STUDENT, UserStatus.ACTIVE);
        User bob = createUser("bob@example.com", UserRole.STUDENT, UserStatus.ACTIVE);

        postJson("/api/students/profile", bearer(alice),
                profileBody(unl.universityId, unl.careerId, 2), 201);
        getJson("/api/students/me", bearer(bob), 404);
    }

    @Test
    void student_canUpdateOwnProfile() throws Exception {
        Seed unl = seedUniversityCareer("UNL", "FICH", "Ingeniería en Informática");
        User student = createUser("stu@example.com", UserRole.STUDENT, UserStatus.ACTIVE);
        String token = bearer(student);

        postJson("/api/students/profile", token, profileBody(unl.universityId, unl.careerId, 2), 201);

        String updated = putJson("/api/students/me", token,
                """
                {"universityId":"%s","careerId":"%s","currentYear":4,"bio":"Nueva bio"}
                """.formatted(unl.universityId, unl.careerId), 200);
        assertThat(toJson(updated).get("currentYear").asInt()).isEqualTo(4);
        assertThat(toJson(updated).get("bio").asText()).isEqualTo("Nueva bio");
    }

    @Test
    void studentProfile_nonexistentUniversity_returns404() throws Exception {
        Seed unl = seedUniversityCareer("UNL", "FICH", "Ingeniería en Informática");
        User student = createUser("stu@example.com", UserRole.STUDENT, UserStatus.ACTIVE);

        postJson("/api/students/profile", bearer(student),
                profileBody("00000000-0000-0000-0000-000000000000", unl.careerId, 2), 404);
    }

    @Test
    void studentProfile_invalidCurrentYear_returns400() throws Exception {
        Seed unl = seedUniversityCareer("UNL", "FICH", "Ingeniería en Informática");
        User student = createUser("stu@example.com", UserRole.STUDENT, UserStatus.ACTIVE);

        postJson("/api/students/profile", bearer(student),
                profileBody(unl.universityId, unl.careerId, 0), 400);
    }

    @Test
    void studentsEndpoints_requireAuthentication() throws Exception {
        getJson("/api/students/me", null, 401);
    }
}
