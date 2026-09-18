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
}
