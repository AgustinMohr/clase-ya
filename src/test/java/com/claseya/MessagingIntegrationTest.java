package com.claseya;

import com.claseya.academic.repository.AcademicUnitRepository;
import com.claseya.academic.repository.CareerRepository;
import com.claseya.academic.repository.UniversityRepository;
import com.claseya.messaging.repository.ConversationParticipantRepository;
import com.claseya.messaging.repository.ConversationRepository;
import com.claseya.messaging.repository.MessageRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class MessagingIntegrationTest extends AbstractWebIntegrationTest {

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
    private ConversationRepository conversationRepository;
    @Autowired
    private ConversationParticipantRepository participantRepository;
    @Autowired
    private MessageRepository messageRepository;

    private void seedCareerForProfiles() {
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
    }

    private User newStudent(String email) {
        User user = createUser(email, UserRole.STUDENT, UserStatus.ACTIVE);
        seedCareerForProfiles();
        StudentProfile profile = new StudentProfile();
        profile.setUser(user);
        profile.setUniversity(universityRepository.findAll().get(0));
        profile.setCareer(careerRepository.findAll().get(0));
        profile.setCurrentYear(2);
        studentProfileRepository.saveAndFlush(profile);
        return user;
    }

    private TeacherProfile newTeacher(String name, String email,
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
        teacher.setAddress("Privada " + name);
        teacher.setVerificationStatus(verification);
        teacher.setRatingCount(0);
        teacher.setRatingAverage(java.math.BigDecimal.ZERO);
        return teacherProfileRepository.saveAndFlush(teacher);
    }

    private TeacherProfile visibleTeacher(String name, String email) {
        return newTeacher(name, email, VerificationStatus.VERIFIED, UserStatus.ACTIVE);
    }

    private String createConversation(String token, String teacherId, int expected) throws Exception {
        return postJson("/api/conversations", token,
                "{\"teacherId\":\"%s\"}".formatted(teacherId), expected);
    }

    private String sendMessage(String token, String conversationId, String content, int expected) throws Exception {
        return postJson("/api/conversations/" + conversationId + "/messages", token,
                "{\"content\":\"%s\"}".formatted(content), expected);
    }

    private JsonNode listConversations(String token, int expected) throws Exception {
        return toJson(getJson("/api/conversations", token, expected));
    }

    private JsonNode listMessages(String token, String conversationId, int expected) throws Exception {
        return toJson(getJson("/api/conversations/" + conversationId + "/messages", token, expected));
    }

    // ------------------------------------------------------------------ creation

    @Test
    void student_canCreateConversation_returns201() throws Exception {
        User student = newStudent("s@example.com");
        TeacherProfile teacher = visibleTeacher("Ana Prof", "ana@example.com");

        JsonNode body = toJson(createConversation(bearer(student), teacher.getId().toString(), 201));
        String convId = body.get("id").asText();
        // Participants are Users; the "other participant" is the teacher's user.
        assertThat(body.get("otherParticipant").get("id").asText())
                .isEqualTo(teacher.getUser().getId().toString());
        assertThat(body.get("otherParticipant").get("role").asText()).isEqualTo("TEACHER");
        assertThat(conversationRepository.findById(UUID.fromString(convId))).isPresent();
        assertThat(participantRepository.findByConversation_IdAndUser_Id(
                UUID.fromString(convId), student.getId())).isPresent();
    }

    @Test
    void student_contactsSameTeacherAgain_returnsExistingConversation() throws Exception {
        User student = newStudent("s@example.com");
        TeacherProfile teacher = visibleTeacher("Ana", "ana@example.com");
        String token = bearer(student);

        String first = createConversation(token, teacher.getId().toString(), 201);
        String second = createConversation(token, teacher.getId().toString(), 200);
        assertThat(toJson(second).get("id").asText()).isEqualTo(toJson(first).get("id").asText());
        assertThat(conversationRepository.count()).isEqualTo(1);
    }

    @Test
    void teacher_cannotStartConversation_returns403() throws Exception {
        User teacherUser = createUser("prof@example.com", UserRole.TEACHER, UserStatus.ACTIVE);
        TeacherProfile target = visibleTeacher("Otro", "otro@example.com");
        createConversation(bearer(teacherUser), target.getId().toString(), 403);
    }

    @Test
    void studentWithoutProfile_cannotStart_returns409() throws Exception {
        User student = createUser("noprofile@example.com", UserRole.STUDENT, UserStatus.ACTIVE);
        TeacherProfile teacher = visibleTeacher("Ana", "ana@example.com");
        createConversation(bearer(student), teacher.getId().toString(), 409);
    }

    @Test
    void cannotContactNonVisibleTeachers_returns404() throws Exception {
        User student = newStudent("s@example.com");
        String token = bearer(student);

        createConversation(token, UUID.randomUUID().toString(), 404);
        TeacherProfile pending = newTeacher("P", "p@example.com", VerificationStatus.PENDING, UserStatus.ACTIVE);
        TeacherProfile rejected = newTeacher("R", "r@example.com", VerificationStatus.REJECTED, UserStatus.ACTIVE);
        TeacherProfile inactive = newTeacher("I", "i@example.com", VerificationStatus.VERIFIED, UserStatus.INACTIVE);
        TeacherProfile suspended = newTeacher("S", "su@example.com", VerificationStatus.VERIFIED, UserStatus.SUSPENDED);

        createConversation(token, pending.getId().toString(), 404);
        createConversation(token, rejected.getId().toString(), 404);
        createConversation(token, inactive.getId().toString(), 404);
        createConversation(token, suspended.getId().toString(), 404);
        assertThat(conversationRepository.count()).isZero();
    }

    // ------------------------------------------------------------------ access / ownership

    @Test
    void nonParticipant_cannotAccessConversation_returns404() throws Exception {
        User studentA = newStudent("a@example.com");
        User studentC = newStudent("c@example.com");
        TeacherProfile teacher = visibleTeacher("Ana", "ana@example.com");

        String conv = createConversation(bearer(studentA), teacher.getId().toString(), 201);
        String convId = toJson(conv).get("id").asText();
        String otherToken = bearer(studentC);

        getJson("/api/conversations/" + convId, otherToken, 404);
        listMessages(otherToken, convId, 404);
        sendMessage(otherToken, convId, "hola", 404);
        getJson("/api/conversations/" + convId + "/read", otherToken, 405);
    }

    // ------------------------------------------------------------------ messages

    @Test
    void participant_sendsMessage_senderIsAuthenticatedUser() throws Exception {
        User student = newStudent("s@example.com");
        TeacherProfile teacher = visibleTeacher("Ana", "ana@example.com");
        String conv = createConversation(bearer(student), teacher.getId().toString(), 201);
        String convId = toJson(conv).get("id").asText();

        JsonNode message = toJson(sendMessage(bearer(student), convId, "  Hola profe  ", 201));
        assertThat(message.get("senderId").asText()).isEqualTo(student.getId().toString());
        assertThat(message.get("content").asText()).isEqualTo("Hola profe");
        assertThat(message.get("conversationId").asText()).isEqualTo(convId);
        // readAt is null on send and omitted by the API (non-null serialization).
        assertThat(message.has("readAt")).isFalse();
        assertThat(message.get("createdAt").asText()).isNotBlank();
    }

    @Test
    void senderSpoofing_isIgnored() throws Exception {
        User student = newStudent("s@example.com");
        User attacker = createUser("attacker@example.com", UserRole.STUDENT, UserStatus.ACTIVE);
        TeacherProfile teacher = visibleTeacher("Ana", "ana@example.com");
        String conv = createConversation(bearer(student), teacher.getId().toString(), 201);
        String convId = toJson(conv).get("id").asText();

        // Extra senderId field is not part of the DTO and must be ignored.
        String body = """
                {"senderId":"%s","content":"hola"}
                """.formatted(attacker.getId());
        JsonNode message = toJson(postJson("/api/conversations/" + convId + "/messages",
                bearer(student), body, 201));
        assertThat(message.get("senderId").asText()).isEqualTo(student.getId().toString());
    }

    @Test
    void teacherResponds_andStudentContinues() throws Exception {
        User student = newStudent("s@example.com");
        TeacherProfile teacher = visibleTeacher("Ana", "ana@example.com");
        String convId = toJson(createConversation(bearer(student),
                teacher.getId().toString(), 201)).get("id").asText();

        String teacherToken = bearer(teacher.getUser());
        sendMessage(teacherToken, convId, "Hola, tengo disponibilidad", 201);
        JsonNode studentReply = toJson(sendMessage(bearer(student), convId, "Perfecto", 201));
        assertThat(studentReply.get("senderId").asText()).isEqualTo(student.getId().toString());

        // Teacher can read the conversation; its "other participant" is the student.
        JsonNode conv = toJson(getJson("/api/conversations/" + convId, teacherToken, 200));
        assertThat(conv.get("otherParticipant").get("role").asText()).isEqualTo("STUDENT");
    }

    @Test
    void blankAndOversizedContent_areRejected() throws Exception {
        User student = newStudent("s@example.com");
        TeacherProfile teacher = visibleTeacher("Ana", "ana@example.com");
        String convId = toJson(createConversation(bearer(student),
                teacher.getId().toString(), 201)).get("id").asText();
        String token = bearer(student);

        sendMessage(token, convId, "   ", 400);
        sendMessage(token, convId, "x".repeat(5001), 400);
        sendMessage(token, convId, "ok", 201);
    }

    @Test
    void messages_arePaginatedAscending() throws Exception {
        User student = newStudent("s@example.com");
        TeacherProfile teacher = visibleTeacher("Ana", "ana@example.com");
        String convId = toJson(createConversation(bearer(student),
                teacher.getId().toString(), 201)).get("id").asText();
        String token = bearer(student);
        for (int i = 0; i < 5; i++) {
            sendMessage(token, convId, "msg " + i, 201);
        }

        JsonNode page0 = toJson(getJson(
                "/api/conversations/" + convId + "/messages?page=0&size=2", token, 200));
        assertThat(page0.get("content").size()).isEqualTo(2);
        assertThat(page0.get("totalElements").asLong()).isEqualTo(5);
        assertThat(page0.get("totalPages").asInt()).isEqualTo(3);
        assertThat(page0.get("content").get(0).get("content").asText()).isEqualTo("msg 0");

        JsonNode page2 = toJson(getJson(
                "/api/conversations/" + convId + "/messages?page=2&size=2", token, 200));
        assertThat(page2.get("content").size()).isEqualTo(1);
        assertThat(page2.get("content").get(0).get("content").asText()).isEqualTo("msg 4");
    }

    // ------------------------------------------------------------------ list, last message, unread, read

    @Test
    void list_showsLatestConversationFirst_andLastMessage() throws Exception {
        User student = newStudent("s@example.com");
        TeacherProfile t1 = visibleTeacher("Ana", "ana@example.com");
        TeacherProfile t2 = visibleTeacher("Bea", "bea@example.com");
        String token = bearer(student);

        String c1 = toJson(createConversation(token, t1.getId().toString(), 201)).get("id").asText();
        Thread.sleep(2);
        String c2 = toJson(createConversation(token, t2.getId().toString(), 201)).get("id").asText();

        JsonNode first = listConversations(token, 200);
        assertThat(first.get("content").get(0).get("id").asText()).isEqualTo(c2);

        // Sending in the older conversation moves it to the top.
        Thread.sleep(3);
        sendMessage(token, c1, "nuevo mensaje", 201);
        JsonNode after = listConversations(token, 200);
        assertThat(after.get("content").get(0).get("id").asText()).isEqualTo(c1);
        assertThat(after.get("content").get(0).get("lastMessage").get("content").asText())
                .isEqualTo("nuevo mensaje");
        assertThat(after.get("content").get(1).has("lastMessage")).isFalse();
    }

    @Test
    void unreadCount_countsOtherSenderMessages_andMarkReadClearsThem() throws Exception {
        User student = newStudent("s@example.com");
        TeacherProfile teacher = visibleTeacher("Ana", "ana@example.com");
        String studentToken = bearer(student);
        String teacherToken = bearer(teacher.getUser());
        String convId = toJson(createConversation(studentToken,
                teacher.getId().toString(), 201)).get("id").asText();

        sendMessage(studentToken, convId, "yo", 201);
        sendMessage(teacherToken, convId, "t1", 201);
        sendMessage(teacherToken, convId, "t2", 201);

        // Student sees 2 unread (from teacher); teacher sees 1 (the student's message).
        JsonNode studentView = listConversations(studentToken, 200);
        assertThat(studentView.get("content").get(0).get("unreadCount").asLong()).isEqualTo(2);
        JsonNode teacherView = listConversations(teacherToken, 200);
        assertThat(teacherView.get("content").get(0).get("unreadCount").asLong()).isEqualTo(1);

        // Mark read only clears messages from the other sender.
        getJson("/api/conversations/" + convId + "/messages", studentToken, 200);
        mockMvc.perform(patch("/api/conversations/" + convId + "/read")
                        .header("Authorization", studentToken))
                .andExpect(status().isNoContent());

        JsonNode messages = listMessages(studentToken, convId, 200);
        for (JsonNode m : messages.get("content")) {
            boolean mine = m.get("senderId").asText().equals(student.getId().toString());
            if (mine) {
                assertThat(m.has("readAt")).isFalse();
            } else {
                assertThat(m.has("readAt")).isTrue();
            }
        }
        JsonNode cleared = listConversations(studentToken, 200);
        assertThat(cleared.get("content").get(0).get("unreadCount").asLong()).isZero();
    }

    // ------------------------------------------------------------------ rules / constraints / privacy

    @Test
    void participantUniqueness_isEnforcedByDatabase() throws Exception {
        User student = newStudent("s@example.com");
        TeacherProfile teacher = visibleTeacher("Ana", "ana@example.com");
        String convId = toJson(createConversation(bearer(student),
                teacher.getId().toString(), 201)).get("id").asText();
        var conversation = conversationRepository.findById(UUID.fromString(convId)).orElseThrow();

        assertThatThrownBy(() -> {
            var dup = new com.claseya.model.ConversationParticipant();
            dup.setConversation(conversation);
            dup.setUser(student);
            participantRepository.saveAndFlush(dup);
        }).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void disabledAccounts_cannotUseMessaging_returns401() throws Exception {
        User suspended = createUser("sus@example.com", UserRole.STUDENT, UserStatus.SUSPENDED);
        mockMvc.perform(get("/api/conversations").header("Authorization", bearer(suspended)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void messaging_doesNotExposePrivateData() throws Exception {
        User student = newStudent("s@example.com");
        TeacherProfile teacher = newTeacher("Ana", "privada@example.com",
                VerificationStatus.VERIFIED, UserStatus.ACTIVE);
        String studentToken = bearer(student);
        String convId = toJson(createConversation(studentToken,
                teacher.getId().toString(), 201)).get("id").asText();
        sendMessage(studentToken, convId, "hola", 201);

        String conversationBody = getJson("/api/conversations/" + convId, studentToken, 200);
        assertThat(conversationBody).doesNotContain("privada@example.com").doesNotContain("passwordHash")
                .doesNotContain("Privada Ana");
        String messagesBody = getJson("/api/conversations/" + convId + "/messages", studentToken, 200);
        assertThat(messagesBody).doesNotContain("privada@example.com").doesNotContain("passwordHash");
    }
}
