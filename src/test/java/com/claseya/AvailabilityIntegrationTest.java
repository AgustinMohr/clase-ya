package com.claseya;

import com.claseya.availability.repository.AvailabilityWindowRepository;
import com.claseya.model.AvailabilityWindow;
import com.claseya.model.TeacherProfile;
import com.claseya.model.User;
import com.claseya.model.enums.TeachingModality;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.claseya.model.enums.VerificationStatus;
import com.claseya.teacher.repository.TeacherProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class AvailabilityIntegrationTest extends AbstractWebIntegrationTest {

    @Autowired
    private TeacherProfileRepository teacherProfileRepository;
    @Autowired
    private AvailabilityWindowRepository windowRepository;

    private record Teacher(TeacherProfile profile, String token) {
    }

    private Teacher newTeacher(String email, String name, VerificationStatus verification,
                               UserStatus userStatus) {
        User user = new User();
        user.setEmail(email);
        user.setName(name);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(UserRole.TEACHER);
        user.setStatus(userStatus);
        userRepository.saveAndFlush(user);

        TeacherProfile profile = new TeacherProfile();
        profile.setUser(user);
        profile.setVerificationStatus(verification);
        profile.setBio("Bio " + name);
        teacherProfileRepository.saveAndFlush(profile);
        return new Teacher(profile, bearer(user));
    }

    private String body(int day, String start, String end) {
        return """
                {"dayOfWeek":%d,"startTime":"%s","endTime":"%s","mode":"ONLINE"}
                """.formatted(day, start, end);
    }

    private String create(String token, String json, int expected) throws Exception {
        return postJson("/api/availability", token, json, expected);
    }

    private JsonNode searchPublic(String query, int expected) throws Exception {
        return toJson(getJson("/api/availability" + query, null, expected));
    }

    // ------------------------------------------------------------------ security

    @Test
    void createWithoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/availability").contentType("application/json")
                        .content(body(1, "18:00", "19:00")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotPublish_returns403() throws Exception {
        User student = createUser("stu@example.com", UserRole.STUDENT, UserStatus.ACTIVE);
        create(bearer(student), body(1, "18:00", "19:00"), 403);
    }

    @Test
    void nonVerifiedTeacherCannotPublish_returns403() throws Exception {
        Teacher pending = newTeacher("pend@example.com", "P", VerificationStatus.PENDING,
                UserStatus.ACTIVE);
        create(pending.token(), body(1, "18:00", "19:00"), 403);
    }

    @Test
    void inactiveTeacher_cannotAuthenticate_returns401() throws Exception {
        Teacher inactive = newTeacher("inac@example.com", "I", VerificationStatus.VERIFIED,
                UserStatus.INACTIVE);
        mockMvc.perform(post("/api/availability").header("Authorization", inactive.token())
                        .contentType("application/json").content(body(1, "18:00", "19:00")))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ create / validation

    @Test
    void verifiedTeacher_createsWindow_returns201() throws Exception {
        Teacher t = newTeacher("a@example.com", "Ana", VerificationStatus.VERIFIED, UserStatus.ACTIVE);

        JsonNode created = toJson(create(t.token(), body(1, "18:00", "20:00"), 201));
        assertThat(created.get("dayOfWeek").asInt()).isEqualTo(1);
        assertThat(created.get("startTime").asText()).isEqualTo("18:00");
        assertThat(created.get("endTime").asText()).isEqualTo("20:00");
        assertThat(created.get("mode").asText()).isEqualTo("ONLINE");
        assertThat(created.get("status").asText()).isEqualTo("AVAILABLE");
        assertThat(created.get("dayPart").asText()).isEqualTo("AFTERNOON");
        assertThat(created.get("teacherId").asText()).isEqualTo(t.profile().getId().toString());
    }

    @Test
    void variableDurations_areAccepted() throws Exception {
        Teacher t = newTeacher("b@example.com", "Bea", VerificationStatus.VERIFIED, UserStatus.ACTIVE);
        create(t.token(), body(1, "08:00", "09:00"), 201);
        create(t.token(), body(2, "08:00", "09:30"), 201);
        create(t.token(), body(3, "08:00", "10:00"), 201);
        create(t.token(), body(4, "08:00", "11:00"), 201);
    }

    @Test
    void tooShortOrInvalidWindows_areRejected() throws Exception {
        Teacher t = newTeacher("c@example.com", "Ca", VerificationStatus.VERIFIED, UserStatus.ACTIVE);
        String token = t.token();

        create(token, body(1, "18:00", "18:30"), 400);          // < 1 hour
        create(token, body(1, "18:00", "18:00"), 400);          // zero
        create(token, body(1, "19:00", "18:00"), 400);          // end < start
        create(token, "{\"dayOfWeek\":8,\"startTime\":\"18:00\",\"endTime\":\"19:00\"}", 400);
    }

    @Test
    void clientControlledFields_areIgnored() throws Exception {
        Teacher t = newTeacher("d@example.com", "De", VerificationStatus.VERIFIED, UserStatus.ACTIVE);
        String json = """
                {"dayOfWeek":1,"startTime":"18:00","endTime":"19:00",
                 "teacherId":"%s","status":"DISABLED","createdAt":"x","updatedAt":"x"}
                """.formatted(UUID.randomUUID());
        JsonNode created = toJson(create(t.token(), json, 201));
        assertThat(created.get("status").asText()).isEqualTo("AVAILABLE");
        assertThat(created.get("teacherId").asText()).isEqualTo(t.profile().getId().toString());
    }

    // ------------------------------------------------------------------ overlap / contiguity

    @Test
    void overlappingWindows_areRejected_contiguousAllowed() throws Exception {
        Teacher t = newTeacher("e@example.com", "El", VerificationStatus.VERIFIED, UserStatus.ACTIVE);
        String token = t.token();

        create(token, body(1, "17:00", "19:00"), 201);
        create(token, body(1, "18:00", "20:00"), 409);     // partial overlap
        create(token, body(1, "17:30", "18:30"), 409);     // contained
        create(token, body(1, "17:00", "18:00"), 409);     // duplicate inside
        create(token, body(1, "19:00", "20:00"), 201);     // contiguous OK
        create(token, body(2, "17:00", "19:00"), 201);     // another day OK
    }

    @Test
    void update_mustRespectOverlap_andOwnership() throws Exception {
        Teacher a = newTeacher("f@example.com", "Fa", VerificationStatus.VERIFIED, UserStatus.ACTIVE);
        Teacher b = newTeacher("g@example.com", "Ga", VerificationStatus.VERIFIED, UserStatus.ACTIVE);

        String w1 = toJson(create(a.token(), body(1, "09:00", "10:00"), 201)).get("id").asText();
        String w2 = toJson(create(a.token(), body(1, "11:00", "12:00"), 201)).get("id").asText();

        // Move w2 over w1 -> 409.
        putJson("/api/availability/" + w2, a.token(), body(1, "09:30", "10:30"), 409);
        // Non-owner cannot update.
        putJson("/api/availability/" + w1, b.token(), body(1, "20:00", "21:00"), 404);
        // Legit move.
        putJson("/api/availability/" + w2, a.token(), body(1, "12:00", "13:00"), 200);
    }

    @Test
    void disableAndEnable_ownerOnly() throws Exception {
        Teacher a = newTeacher("h@example.com", "Ha", VerificationStatus.VERIFIED, UserStatus.ACTIVE);
        Teacher b = newTeacher("i@example.com", "Ia", VerificationStatus.VERIFIED, UserStatus.ACTIVE);

        String w = toJson(create(a.token(), body(3, "18:00", "20:00"), 201)).get("id").asText();
        postNoBody("/api/availability/" + w + "/disable", b.token(), 404);
        postNoBody("/api/availability/" + w + "/enable", b.token(), 404);

        postNoBody("/api/availability/" + w + "/disable", a.token(), 204);
        JsonNode disabled = searchPublic("?teacherId=" + a.profile().getId(), 200);
        assertThat(disabled.get("totalElements").asLong()).isZero();

        postNoBody("/api/availability/" + w + "/enable", a.token(), 204);
        JsonNode enabled = searchPublic("?teacherId=" + a.profile().getId(), 200);
        assertThat(enabled.get("totalElements").asLong()).isEqualTo(1);
    }

    private void postNoBody(String uri, String token, int expected) throws Exception {
        mockMvc.perform(post(uri).header("Authorization", token))
                .andExpect(status().is(expected));
    }

    // ------------------------------------------------------------------ public / privacy / listing

    @Test
    void publicSearch_onlyShowsAvailableEligibleTeachers() throws Exception {
        Teacher verified = newTeacher("j@example.com", "Ja", VerificationStatus.VERIFIED, UserStatus.ACTIVE);
        Teacher pending = newTeacher("k@example.com", "Ke", VerificationStatus.VERIFIED, UserStatus.ACTIVE);
        Teacher inactive = newTeacher("l@example.com", "Le", VerificationStatus.VERIFIED, UserStatus.ACTIVE);

        create(verified.token(), body(1, "18:00", "19:00"), 201);
        create(pending.token(), body(1, "18:00", "19:00"), 201);
        create(inactive.token(), body(1, "18:00", "19:00"), 201);

        // Make two teachers non-eligible AFTER publishing (their windows must be hidden).
        pending.profile().setVerificationStatus(VerificationStatus.PENDING);
        teacherProfileRepository.saveAndFlush(pending.profile());
        inactive.profile().getUser().setStatus(UserStatus.INACTIVE);
        userRepository.saveAndFlush(inactive.profile().getUser());

        JsonNode all = searchPublic("", 200);
        assertThat(all.get("totalElements").asLong()).isEqualTo(1);
        String body = all.get("content").get(0).toString();
        assertThat(body).contains(verified.profile().getId().toString());
        assertThat(body).doesNotContain("j@example.com").doesNotContain("passwordHash");
    }

    @Test
    void publicSearch_filtersByDayModeDayPart() throws Exception {
        Teacher t = newTeacher("m@example.com", "Ma", VerificationStatus.VERIFIED, UserStatus.ACTIVE);
        String id = t.profile().getId().toString();
        create(t.token(), body(1, "08:00", "09:00"), 201);   // Monday morning
        create(t.token(), body(1, "20:00", "21:00"), 201);   // Monday night
        create(t.token(), body(2, "08:00", "09:00"), 201);   // Tuesday morning

        assertThat(searchPublic("?teacherId=" + id + "&dayOfWeek=1", 200).get("totalElements").asLong())
                .isEqualTo(2);
        assertThat(searchPublic("?teacherId=" + id + "&dayPart=MORNING", 200).get("totalElements").asLong())
                .isEqualTo(2);
        assertThat(searchPublic("?teacherId=" + id + "&dayOfWeek=1&dayPart=NIGHT", 200)
                .get("totalElements").asLong()).isEqualTo(1);
        assertThat(searchPublic("?teacherId=" + id + "&mode=IN_PERSON", 200).get("totalElements").asLong())
                .isZero();
    }

    @Test
    void listMine_isPaginatedAndOwnOnly() throws Exception {
        Teacher a = newTeacher("n@example.com", "Na", VerificationStatus.VERIFIED, UserStatus.ACTIVE);
        Teacher b = newTeacher("o@example.com", "Oa", VerificationStatus.VERIFIED, UserStatus.ACTIVE);
        for (int d = 1; d <= 3; d++) {
            create(a.token(), body(d, "18:00", "19:00"), 201);
        }
        create(b.token(), body(1, "18:00", "19:00"), 201);

        JsonNode page0 = toJson(getJson("/api/availability/me?page=0&size=2", a.token(), 200));
        assertThat(page0.get("totalElements").asLong()).isEqualTo(3);
        assertThat(page0.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page0.get("content").size()).isEqualTo(2);

        JsonNode page1 = toJson(getJson("/api/availability/me?page=1&size=2", a.token(), 200));
        assertThat(page1.get("content").size()).isEqualTo(1);

        // Ordering dayOfWeek ASC.
        assertThat(page0.get("content").get(0).get("dayOfWeek").asInt()).isEqualTo(1);
        getJson("/api/availability/me?size=51", a.token(), 400);
    }

    @Test
    void database_exclusionConstraint_blocksOverlaps() throws Exception {
        Teacher a = newTeacher("p@example.com", "Pa", VerificationStatus.VERIFIED, UserStatus.ACTIVE);
        create(a.token(), body(1, "18:00", "19:00"), 201);

        AvailabilityWindow overlap = new AvailabilityWindow();
        overlap.setTeacher(a.profile());
        overlap.setDayOfWeek(1);
        overlap.setStartMinutes(18 * 60 + 30);
        overlap.setEndMinutes(19 * 60 + 30);
        overlap.setStatus(com.claseya.model.enums.AvailabilityStatus.AVAILABLE);

        assertThatThrownBy(() -> windowRepository.saveAndFlush(overlap))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void times_roundTripAsWallClock() throws Exception {
        Teacher t = newTeacher("q@example.com", "Qa", VerificationStatus.VERIFIED, UserStatus.ACTIVE);
        JsonNode created = toJson(create(t.token(), body(2, "09:00", "10:30"), 201));
        assertThat(created.get("startTime").asText()).isEqualTo("09:00");
        assertThat(created.get("endTime").asText()).isEqualTo("10:30");
        assertThat(created.get("dayPart").asText()).isEqualTo("MORNING");
    }
}
