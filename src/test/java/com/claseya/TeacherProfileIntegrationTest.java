package com.claseya;

import com.claseya.model.User;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class TeacherProfileIntegrationTest extends AbstractWebIntegrationTest {

    private String careerSubjectSeed() throws Exception {
        String admin = adminBearer();
        String universityId = idOf(postJson("/api/universities", admin,
                """
                {"name":"UNL"}
                """, 201));
        String unitId = idOf(postJson("/api/universities/" + universityId + "/academic-units", admin,
                """
                {"name":"FICH"}
                """, 201));
        String careerId = idOf(postJson("/api/academic-units/" + unitId + "/careers", admin,
                """
                {"name":"Ingeniería en Informática"}
                """, 201));
        String subjectId = idOf(postJson("/api/subjects", admin,
                """
                {"name":"Matemática I"}
                """, 201));
        return idOf(postJson("/api/careers/" + careerId + "/subjects", admin,
                """
                {"subjectId":"%s","year":1,"semester":1}
                """.formatted(subjectId), 201));
    }

    private String profileBody() {
        return """
                {"bio":"Profesor de matemática","address":"Santa Fe Capital",
                 "latitude":-31.6333,"longitude":-60.7}
                """;
    }

    @Test
    void teacher_canCreateProfile_startsWithPendingAndZeroRating() throws Exception {
        User teacher = createUser("prof@example.com", UserRole.TEACHER, UserStatus.ACTIVE);
        String token = bearer(teacher);

        String created = postJson("/api/teachers/profile", token, profileBody(), 201);
        assertThat(toJson(created).get("verificationStatus").asText()).isEqualTo("PENDING");
        assertThat(toJson(created).get("ratingCount").asInt()).isZero();

        String me = getJson("/api/teachers/me", token, 200);
        assertThat(toJson(me).get("email").asText()).isEqualTo("prof@example.com");
        assertThat(toJson(me).get("verificationStatus").asText()).isEqualTo("PENDING");
    }

    @Test
    void teacher_cannotSelfVerify_orManipulateRating() throws Exception {
        User teacher = createUser("prof@example.com", UserRole.TEACHER, UserStatus.ACTIVE);
        String token = bearer(teacher);

        // verificationStatus, ratingAverage and ratingCount are not part of the DTO;
        // sending them must be ignored (the profile still starts PENDING with rating 0).
        String created = postJson("/api/teachers/profile", token,
                """
                {"bio":"x","verificationStatus":"VERIFIED","ratingAverage":5,"ratingCount":99}
                """, 201);
        assertThat(toJson(created).get("verificationStatus").asText()).isEqualTo("PENDING");
        assertThat(toJson(created).get("ratingCount").asInt()).isZero();

        String updated = putJson("/api/teachers/me", token,
                """
                {"bio":"nueva bio","ratingAverage":5,"ratingCount":99}
                """, 200);
        assertThat(toJson(updated).get("bio").asText()).isEqualTo("nueva bio");
        assertThat(toJson(updated).get("ratingAverage").asDouble()).isZero();
        assertThat(toJson(updated).get("ratingCount").asInt()).isZero();
    }

    @Test
    void student_cannotCreateTeacherProfile_returns403() throws Exception {
        User student = createUser("stu@example.com", UserRole.STUDENT, UserStatus.ACTIVE);
        postJson("/api/teachers/profile", bearer(student), profileBody(), 403);
    }

    @Test
    void teacherProfile_alreadyExists_returns409() throws Exception {
        User teacher = createUser("prof@example.com", UserRole.TEACHER, UserStatus.ACTIVE);
        String token = bearer(teacher);

        postJson("/api/teachers/profile", token, profileBody(), 201);
        postJson("/api/teachers/profile", token, profileBody(), 409);
    }

    @Test
    void teacher_cannotSeeAnotherTeachersProfile_returns404() throws Exception {
        User alice = createUser("alice@example.com", UserRole.TEACHER, UserStatus.ACTIVE);
        User bob = createUser("bob@example.com", UserRole.TEACHER, UserStatus.ACTIVE);

        postJson("/api/teachers/profile", bearer(alice), profileBody(), 201);
        getJson("/api/teachers/me", bearer(bob), 404);
    }

    @Test
    void teacher_canUpdateOwnProfile() throws Exception {
        User teacher = createUser("prof@example.com", UserRole.TEACHER, UserStatus.ACTIVE);
        String token = bearer(teacher);
        postJson("/api/teachers/profile", token, profileBody(), 201);

        String updated = putJson("/api/teachers/me", token,
                """
                {"bio":"Actualizado","latitude":-31.6,"longitude":-60.7}
                """, 200);
        assertThat(toJson(updated).get("bio").asText()).isEqualTo("Actualizado");
        assertThat(toJson(updated).has("address")).isFalse();
    }

    @Test
    void education_crud_isVerifiedStaysFalse() throws Exception {
        User teacher = createUser("prof@example.com", UserRole.TEACHER, UserStatus.ACTIVE);
        String token = bearer(teacher);
        postJson("/api/teachers/profile", token, profileBody(), 201);

        // isVerified is not accepted from the teacher; it is ignored.
        String educationId = idOf(postJson("/api/teachers/me/education", token,
                """
                {"institution":"UTN","degree":"Ingeniero en Sistemas","startYear":2005,"endYear":2010,"isVerified":true}
                """, 201));

        String list = getJson("/api/teachers/me/education", token, 200);
        assertThat(toJson(list).get(0).get("isVerified").asBoolean()).isFalse();

        String updated = putJson("/api/teachers/me/education/" + educationId, token,
                """
                {"institution":"UTN","degree":"Licenciado en Sistemas","startYear":2005,"endYear":2011}
                """, 200);
        assertThat(toJson(updated).get("degree").asText()).isEqualTo("Licenciado en Sistemas");

        deleteJson("/api/teachers/me/education/" + educationId, token, 204);
        String afterDelete = getJson("/api/teachers/me/education", token, 200);
        assertThat(afterDelete).isEqualTo("[]");
    }

    @Test
    void education_invalidYearRange_returns400() throws Exception {
        User teacher = createUser("prof@example.com", UserRole.TEACHER, UserStatus.ACTIVE);
        String token = bearer(teacher);
        postJson("/api/teachers/profile", token, profileBody(), 201);

        postJson("/api/teachers/me/education", token,
                """
                {"institution":"UTN","degree":"Ingeniero","startYear":2010,"endYear":2005}
                """, 400);
    }

    @Test
    void teacherSubjects_addDuplicateListRemove() throws Exception {
        String careerSubjectId = careerSubjectSeed();
        User teacher = createUser("prof@example.com", UserRole.TEACHER, UserStatus.ACTIVE);
        String token = bearer(teacher);
        postJson("/api/teachers/profile", token, profileBody(), 201);

        String added = postJson("/api/teachers/me/subjects", token,
                """
                {"careerSubjectId":"%s","yearsExperience":5}
                """.formatted(careerSubjectId), 201);
        assertThat(toJson(added).get("subjectName").asText()).startsWith("Matem");

        // duplicate
        postJson("/api/teachers/me/subjects", token,
                """
                {"careerSubjectId":"%s"}
                """.formatted(careerSubjectId), 409);

        String list = getJson("/api/teachers/me/subjects", token, 200);
        assertThat(list).contains(careerSubjectId);

        deleteJson("/api/teachers/me/subjects/" + careerSubjectId, token, 204);
        String afterDelete = getJson("/api/teachers/me/subjects", token, 200);
        assertThat(afterDelete).isEqualTo("[]");
    }

    @Test
    void teacherSubjects_nonexistentCareerSubject_returns404() throws Exception {
        User teacher = createUser("prof@example.com", UserRole.TEACHER, UserStatus.ACTIVE);
        String token = bearer(teacher);
        postJson("/api/teachers/profile", token, profileBody(), 201);

        postJson("/api/teachers/me/subjects", token,
                """
                {"careerSubjectId":"00000000-0000-0000-0000-000000000000"}
                """, 404);
    }

    @Test
    void modalities_addDuplicateRemove() throws Exception {
        User teacher = createUser("prof@example.com", UserRole.TEACHER, UserStatus.ACTIVE);
        String token = bearer(teacher);
        postJson("/api/teachers/profile", token, profileBody(), 201);

        postJson("/api/teachers/me/modalities", token, """
                {"modality":"ONLINE"}
                """, 201);
        postJson("/api/teachers/me/modalities", token, """
                {"modality":"ONLINE"}
                """, 409);
        postJson("/api/teachers/me/modalities", token, """
                {"modality":"IN_PERSON"}
                """, 201);

        String list = getJson("/api/teachers/me/modalities", token, 200);
        assertThat(list).contains("ONLINE").contains("IN_PERSON");

        // Profile response exposes the modalities.
        String me = getJson("/api/teachers/me", token, 200);
        assertThat(toJson(me).get("modalities").toString()).contains("ONLINE", "IN_PERSON");

        deleteJson("/api/teachers/me/modalities/ONLINE", token, 204);
        String after = getJson("/api/teachers/me/modalities", token, 200);
        assertThat(after).doesNotContain("ONLINE");
    }

    @Test
    void teacherEndpoints_requireAuthentication() throws Exception {
        getJson("/api/teachers/me", null, 401);
    }
}
