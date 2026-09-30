package com.claseya;

import com.claseya.model.User;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.claseya.security.AppUserDetails;
import com.claseya.security.JwtService;
import com.claseya.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.demo-seed.enabled=false")
@AutoConfigureMockMvc
public abstract class AbstractWebIntegrationTest extends AbstractPostgresTest {

    protected static final String PASSWORD = "Password123";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtService jwtService;

    @Autowired
    protected ObjectMapper objectMapper;

    private static final String APPLICATION_JSON_UTF8 = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8";

    protected User createUser(String email, UserRole role, UserStatus status) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setStatus(status);
        return userRepository.save(user);
    }

    protected String bearer(User user) {
        return "Bearer " + jwtService.generateToken(new AppUserDetails(user));
    }

    protected String adminBearer() {
        return bearer(createUser("admin+" + UUID.randomUUID() + "@test.com", UserRole.ADMIN, UserStatus.ACTIVE));
    }

    // --------------------------------------------------------------------- HTTP helpers

    protected String postJson(String uri, String bearerToken, String jsonBody, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post(uri)
                        .header("Authorization", bearerToken)
                        .contentType(APPLICATION_JSON_UTF8)
                        .content(jsonBody.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    protected String putJson(String uri, String bearerToken, String jsonBody, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(put(uri)
                        .header("Authorization", bearerToken)
                        .contentType(APPLICATION_JSON_UTF8)
                        .content(jsonBody.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    protected void deleteJson(String uri, String bearerToken, int expectedStatus) throws Exception {
        mockMvc.perform(delete(uri).header("Authorization", bearerToken))
                .andExpect(status().is(expectedStatus));
    }

    protected String getJson(String uri, String bearerToken, int expectedStatus) throws Exception {
        MvcResult result = bearerToken == null
                ? mockMvc.perform(get(uri)).andExpect(status().is(expectedStatus)).andReturn()
                : mockMvc.perform(get(uri).header("Authorization", bearerToken))
                        .andExpect(status().is(expectedStatus)).andReturn();
        return result.getResponse().getContentAsString();
    }

    protected JsonNode toJson(String content) throws Exception {
        return objectMapper.readTree(content);
    }

    protected String idOf(String content) throws Exception {
        return toJson(content).get("id").asText();
    }

    // --------------------------------------------------------------------- visibility (D1)

    /**
     * Makes a teacher's announcement PUBLISHED (TEACHER-001, D1): at least one active subject, one
     * modality and one availability window, on top of the name + bio the caller already set. Public
     * visibility no longer depends on verification, so tests that need a visible teacher call this.
     */
    protected void makePublished(User teacher) throws Exception {
        String token = bearer(teacher);
        postJson("/api/teachers/me/subjects", token,
                "{\"careerSubjectId\":\"%s\"}".formatted(createCareerSubject()), 201);
        postJson("/api/teachers/me/modalities", token,
                """
                {"modality":"ONLINE"}
                """, 201);
        postJson("/api/availability", token,
                """
                {"dayOfWeek":1,"startTime":"09:00","endTime":"11:00","mode":"ONLINE"}
                """, 201);
    }

    /** Creates an isolated academic catalog chain and returns the new career-subject id. */
    protected String createCareerSubject() throws Exception {
        String admin = adminBearer();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String universityId = idOf(postJson("/api/universities", admin,
                "{\"name\":\"UNL " + suffix + "\"}", 201));
        String unitId = idOf(postJson("/api/universities/" + universityId + "/academic-units", admin,
                "{\"name\":\"FICH " + suffix + "\"}", 201));
        String careerId = idOf(postJson("/api/academic-units/" + unitId + "/careers", admin,
                "{\"name\":\"Ingenieria " + suffix + "\"}", 201));
        String subjectId = idOf(postJson("/api/subjects", admin,
                "{\"name\":\"Matematica " + suffix + "\"}", 201));
        return idOf(postJson("/api/careers/" + careerId + "/subjects", admin,
                "{\"subjectId\":\"%s\",\"year\":1,\"semester\":1}".formatted(subjectId), 201));
    }
}
