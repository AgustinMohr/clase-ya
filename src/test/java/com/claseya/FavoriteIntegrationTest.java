package com.claseya;

import com.claseya.academic.repository.AcademicUnitRepository;
import com.claseya.academic.repository.CareerRepository;
import com.claseya.academic.repository.UniversityRepository;
import com.claseya.favorite.repository.FavoriteRepository;
import com.claseya.model.AcademicUnit;
import com.claseya.model.Career;
import com.claseya.model.StudentProfile;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class FavoriteIntegrationTest extends AbstractWebIntegrationTest {

    @Autowired
    private UniversityRepository universityRepository;
    @Autowired
    private AcademicUnitRepository academicUnitRepository;
    @Autowired
    private CareerRepository careerRepository;
    @Autowired
    private StudentProfileRepository studentProfileRepository;
    @Autowired
    private TeacherProfileRepository teacherProfileRepository;
    @Autowired
    private FavoriteRepository favoriteRepository;

    private User createStudentUser(String email) {
        return createUser(email, UserRole.STUDENT, UserStatus.ACTIVE);
    }

    private StudentProfile createStudentProfile(User user) {
        University university = new University();
        university.setName("UNL");
        university.setSlug("unl-" + UUID.randomUUID());
        university.setActive(true);
        universityRepository.saveAndFlush(university);
        AcademicUnit unit = new AcademicUnit();
        unit.setUniversity(university);
        unit.setName("FICH");
        unit.setActive(true);
        academicUnitRepository.saveAndFlush(unit);
        Career career = new Career();
        career.setAcademicUnit(unit);
        career.setName("Ingenieria en Informatica");
        career.setSlug("isi-" + UUID.randomUUID());
        career.setActive(true);
        careerRepository.saveAndFlush(career);

        StudentProfile profile = new StudentProfile();
        profile.setUser(user);
        profile.setUniversity(university);
        profile.setCareer(career);
        profile.setCurrentYear(2);
        return studentProfileRepository.saveAndFlush(profile);
    }

    private TeacherProfile createTeacher(String name, String email,
                                         VerificationStatus verification, UserStatus userStatus) {
        User user = new User();
        user.setEmail(email);
        user.setName(name);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(UserRole.TEACHER);
        user.setStatus(userStatus);
        userRepository.saveAndFlush(user);

        TeacherProfile teacher = new TeacherProfile();
        teacher.setUser(user);
        teacher.setBio("Bio " + name);
        teacher.setVerificationStatus(verification);
        teacher.setLatitude(new java.math.BigDecimal("-31.6333"));
        teacher.setLongitude(new java.math.BigDecimal("-60.7000"));
        teacher.setAddress("Privada " + name);
        teacher.setRatingCount(0);
        teacher.setRatingAverage(java.math.BigDecimal.ZERO);
        return teacherProfileRepository.saveAndFlush(teacher);
    }

    private TeacherProfile visibleTeacher(String name, String email) {
        return createTeacher(name, email, VerificationStatus.VERIFIED, UserStatus.ACTIVE);
    }

    private String doPost(String uri, String token, int expected) throws Exception {
        return mockMvc.perform(post(uri).header("Authorization", token))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
    }

    private void doDelete(String uri, String token, int expected) throws Exception {
        mockMvc.perform(delete(uri).header("Authorization", token))
                .andExpect(status().is(expected));
    }

    private String doGet(String uri, String token, int expected) throws Exception {
        return mockMvc.perform(get(uri).header("Authorization", token))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
    }

    private List<String> teacherNames(JsonNode page) {
        List<String> names = new ArrayList<>();
        page.get("content").forEach(n -> names.add(n.get("teacher").get("displayName").asText()));
        return names;
    }

    // ------------------------------------------------------------------ create / duplicate / role

    @Test
    void student_canAddFavorite_returns201AndPersists() throws Exception {
        User student = createStudentUser("s@example.com");
        createStudentProfile(student);
        TeacherProfile teacher = visibleTeacher("Ana Prof", "ana@example.com");

        String body = doPost("/api/favorites/" + teacher.getId(), bearer(student), 201);
        JsonNode created = toJson(body);
        assertThat(created.get("teacherId").asText()).isEqualTo(teacher.getId().toString());
        assertThat(created.get("id").asText()).isNotBlank();

        assertThat(favoriteRepository
                .findByStudent_IdAndTeacher_Id(studentProfileRepository
                        .findByUser_Id(student.getId()).orElseThrow().getId(), teacher.getId()))
                .isPresent();
    }

    @Test
    void duplicateFavorite_returns409() throws Exception {
        User student = createStudentUser("s@example.com");
        createStudentProfile(student);
        TeacherProfile teacher = visibleTeacher("Ana", "ana@example.com");
        String token = bearer(student);

        doPost("/api/favorites/" + teacher.getId(), token, 201);
        doPost("/api/favorites/" + teacher.getId(), token, 409);
    }

    @Test
    void teacherRole_cannotUseFavorites_returns403() throws Exception {
        User teacherUser = createUser("prof@example.com", UserRole.TEACHER, UserStatus.ACTIVE);
        doPost("/api/favorites/" + UUID.randomUUID(), bearer(teacherUser), 403);
    }

    @Test
    void onlyVisibleTeachers_canBeFavorited() throws Exception {
        User student = createStudentUser("s@example.com");
        createStudentProfile(student);
        String token = bearer(student);

        TeacherProfile pending = createTeacher("P", "p@example.com", VerificationStatus.PENDING, UserStatus.ACTIVE);
        TeacherProfile rejected = createTeacher("R", "r@example.com", VerificationStatus.REJECTED, UserStatus.ACTIVE);
        TeacherProfile inactiveUser = createTeacher("I", "i@example.com", VerificationStatus.VERIFIED, UserStatus.INACTIVE);
        TeacherProfile suspendedUser = createTeacher("S", "su@example.com", VerificationStatus.VERIFIED, UserStatus.SUSPENDED);

        doPost("/api/favorites/" + pending.getId(), token, 404);
        doPost("/api/favorites/" + rejected.getId(), token, 404);
        doPost("/api/favorites/" + inactiveUser.getId(), token, 404);
        doPost("/api/favorites/" + suspendedUser.getId(), token, 404);
        assertThat(favoriteRepository.count()).isZero();
    }

    @Test
    void nonexistentTeacher_returns404() throws Exception {
        User student = createStudentUser("s@example.com");
        createStudentProfile(student);
        doPost("/api/favorites/" + UUID.randomUUID(), bearer(student), 404);
    }

    // ------------------------------------------------------------------ list / pagination / order

    @Test
    void list_returnsOwnFavorites_newestFirst() throws Exception {
        User student = createStudentUser("s@example.com");
        createStudentProfile(student);
        String token = bearer(student);
        TeacherProfile t1 = visibleTeacher("Ana Uno", "a1@example.com");
        TeacherProfile t2 = visibleTeacher("Bea Dos", "b2@example.com");
        TeacherProfile t3 = visibleTeacher("Cec Tres", "c3@example.com");

        doPost("/api/favorites/" + t1.getId(), token, 201);
        Thread.sleep(3);
        doPost("/api/favorites/" + t2.getId(), token, 201);
        Thread.sleep(3);
        doPost("/api/favorites/" + t3.getId(), token, 201);

        JsonNode page = toJson(doGet("/api/favorites", token, 200));
        assertThat(page.get("totalElements").asLong()).isEqualTo(3);
        assertThat(teacherNames(page)).containsExactly("Cec Tres", "Bea Dos", "Ana Uno");

        // A different student sees none of these.
        User other = createStudentUser("other@example.com");
        createStudentProfile(other);
        JsonNode otherPage = toJson(doGet("/api/favorites", bearer(other), 200));
        assertThat(otherPage.get("totalElements").asLong()).isZero();
    }

    @Test
    void list_paginates() throws Exception {
        User student = createStudentUser("s@example.com");
        createStudentProfile(student);
        String token = bearer(student);
        for (int i = 0; i < 3; i++) {
            TeacherProfile t = visibleTeacher("Prof " + i, "p" + i + "@example.com");
            doPost("/api/favorites/" + t.getId(), token, 201);
            Thread.sleep(3);
        }

        JsonNode page0 = toJson(doGet("/api/favorites?page=0&size=2", token, 200));
        assertThat(page0.get("content").size()).isEqualTo(2);
        assertThat(page0.get("totalElements").asLong()).isEqualTo(3);
        assertThat(page0.get("totalPages").asInt()).isEqualTo(2);

        JsonNode page1 = toJson(doGet("/api/favorites?page=1&size=2", token, 200));
        assertThat(page1.get("content").size()).isEqualTo(1);

        doGet("/api/favorites?size=51", token, 400);
        doGet("/api/favorites?size=0", token, 400);
        doGet("/api/favorites?page=-1", token, 400);
    }

    // ------------------------------------------------------------------ delete

    @Test
    void delete_removesFavorite_thenListEmpty() throws Exception {
        User student = createStudentUser("s@example.com");
        createStudentProfile(student);
        String token = bearer(student);
        TeacherProfile teacher = visibleTeacher("Ana", "ana@example.com");

        doPost("/api/favorites/" + teacher.getId(), token, 201);
        doDelete("/api/favorites/" + teacher.getId(), token, 204);

        JsonNode page = toJson(doGet("/api/favorites", token, 200));
        assertThat(page.get("totalElements").asLong()).isZero();

        // Deleting again -> 404 (no silent idempotency).
        doDelete("/api/favorites/" + teacher.getId(), token, 404);
    }

    @Test
    void delete_nonFavoriteTeacher_returns404() throws Exception {
        User student = createStudentUser("s@example.com");
        createStudentProfile(student);
        TeacherProfile teacher = visibleTeacher("Ana", "ana@example.com");
        doDelete("/api/favorites/" + teacher.getId(), bearer(student), 404);
    }

    // ------------------------------------------------------------------ ownership / IDOR

    @Test
    void idor_userBCannotTouchUserAFavorites() throws Exception {
        User a = createStudentUser("a@example.com");
        createStudentProfile(a);
        User b = createStudentUser("b@example.com");
        createStudentProfile(b);
        TeacherProfile teacher = visibleTeacher("Ana", "ana@example.com");

        String favoriteBody = doPost("/api/favorites/" + teacher.getId(), bearer(a), 201);
        UUID favoriteId = UUID.fromString(toJson(favoriteBody).get("id").asText());

        // B cannot delete A's favorite (A/B same teacher: B has no favorite row).
        doDelete("/api/favorites/" + teacher.getId(), bearer(b), 404);
        // B cannot see A's favorites.
        JsonNode bPage = toJson(doGet("/api/favorites", bearer(b), 200));
        assertThat(bPage.get("totalElements").asLong()).isZero();
        // B status for the same teacher is false; A still holds the favorite.
        JsonNode bStatus = toJson(doGet("/api/favorites/" + teacher.getId(), bearer(b), 200));
        assertThat(bStatus.get("favorite").asBoolean()).isFalse();
        JsonNode aStatus = toJson(doGet("/api/favorites/" + teacher.getId(), bearer(a), 200));
        assertThat(aStatus.get("favorite").asBoolean()).isTrue();
        assertThat(aStatus.get("favoriteId").asText()).isEqualTo(favoriteId.toString());
    }

    @Test
    void studentWithoutProfile_returns409AndDoesNotCreate() throws Exception {
        User student = createStudentUser("noprofile@example.com");
        TeacherProfile teacher = visibleTeacher("Ana", "ana@example.com");

        doPost("/api/favorites/" + teacher.getId(), bearer(student), 409);
        assertThat(studentProfileRepository.existsByUser_Id(student.getId())).isFalse();
    }

    @Test
    void list_doesNotExposePrivateData() throws Exception {
        User student = createStudentUser("s@example.com");
        createStudentProfile(student);
        String token = bearer(student);
        TeacherProfile teacher = visibleTeacher("Ana Privada", "privada.email@example.com");
        doPost("/api/favorites/" + teacher.getId(), token, 201);

        String body = doGet("/api/favorites", token, 200);
        assertThat(body).doesNotContain("privada.email")
                .doesNotContain("passwordHash")
                .doesNotContain("Privada Ana")
                .doesNotContain("latitude")
                .doesNotContain("longitude");
    }

    @Test
    void favorites_requireAuthentication() throws Exception {
        mockMvc.perform(get("/api/favorites"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/favorites/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
