package com.claseya;

import com.claseya.bootstrap.DemoDataSeeder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the development seed ({@code db/seed/demo-data.sql}) applied on startup by
 * {@link DemoDataSeeder}, and that the public endpoints actually serve that data.
 *
 * <p>Runs on its OWN container on purpose: {@link AbstractPostgresTest} shares a single
 * database across the whole suite, and this seed inserts dozens of publicly visible
 * teachers that would break the exact-count assertions of the other test classes.
 */
@SpringBootTest(properties = "app.demo-seed.enabled=true")
@AutoConfigureMockMvc
class DemoSeedIntegrationTest {

    private static final String TEST_SECRET =
            "test-secret-that-is-at-least-thirty-two-bytes-long-for-hmac-sha-256";

    private static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("claseya_seed")
                .withUsername("claseya")
                .withPassword("claseya");
        POSTGRES.start();
        Runtime.getRuntime().addShutdownHook(new Thread(POSTGRES::stop));
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("jwt.secret", () -> TEST_SECRET);
        registry.add("jwt.expiration", () -> "3600");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DemoDataSeeder seeder;

    // ------------------------------------------------------------------ seed content

    @Test
    void seedsEveryRelevantEntityOnStartup() {
        assertThat(count("users")).isGreaterThanOrEqualTo(90);
        assertThat(count("student_profiles")).isGreaterThanOrEqualTo(30);
        assertThat(count("teacher_profiles")).isGreaterThanOrEqualTo(55);
        assertThat(count("subjects")).isGreaterThanOrEqualTo(20);
        assertThat(count("career_subjects")).isGreaterThanOrEqualTo(40);
        assertThat(count("teacher_subjects")).isGreaterThanOrEqualTo(100);
        assertThat(count("teacher_modalities")).isGreaterThanOrEqualTo(100);
        assertThat(count("teacher_education")).isPositive();
        assertThat(count("availability_windows")).isGreaterThanOrEqualTo(100);
        assertThat(count("bookings")).isGreaterThanOrEqualTo(100);
        assertThat(count("reviews")).isGreaterThanOrEqualTo(100);
        assertThat(count("favorites")).isGreaterThanOrEqualTo(100);
        assertThat(count("conversations")).isGreaterThanOrEqualTo(20);
        assertThat(count("conversation_participants")).isGreaterThanOrEqualTo(40);
        assertThat(count("messages")).isGreaterThanOrEqualTo(100);
    }

    @Test
    void applyingTheSeedAgainChangesNothing() {
        Map<String, Integer> before = counts();
        seeder.apply();
        assertThat(counts()).isEqualTo(before);
    }

    // ------------------------------------------------------------------ public API

    @Test
    void publicTeacherSearchIsPaginated() throws Exception {
        JsonNode page = getJson("/api/teachers?page=0&size=24");
        assertThat(page.get("content").size()).isEqualTo(24);
        // El seed demo crea ~50 profesores visibles, pero el número exacto varía con los UUID
        // aleatorios; la aserción se ancla a "hay más de una página" y a que la última página sirve
        // datos, sin depender de un total exacto (que volvía el test flaky).
        long totalElements = page.get("totalElements").asLong();
        int totalPages = page.get("totalPages").asInt();
        assertThat(totalElements).isGreaterThan(24);
        assertThat(totalPages).isGreaterThanOrEqualTo(2);

        int lastIndex = totalPages - 1;
        JsonNode lastPage = getJson("/api/teachers?page=" + lastIndex + "&size=24");
        assertThat(lastPage.get("page").asInt()).isEqualTo(lastIndex);
        assertThat(lastPage.get("content").size()).isPositive();
    }

    @Test
    void subjectWithNoTeachersHasNoResults() throws Exception {
        assertThat(teacherSearchTotalFor("química general")).isZero();
        assertThat(teacherSearchTotalFor("portugués")).isZero();
    }

    @Test
    void subjectWithASingleTeacherReturnsOneResult() throws Exception {
        assertThat(teacherSearchTotalFor("estadística")).isEqualTo(1);
        assertThat(teacherSearchTotalFor("derecho civil")).isEqualTo(1);
    }

    @Test
    void publicCatalogIsServed() throws Exception {
        assertThat(getJson("/api/subjects?size=50").size()).isGreaterThanOrEqualTo(20);
    }

    @Test
    void availabilityIsServedForASeededTeacher() throws Exception {
        String teacherId = seededTeacherId("profe01@claseya.dev");
        JsonNode windows = getJson("/api/availability?teacherId=" + teacherId + "&page=0&size=50");
        assertThat(windows.get("totalElements").asLong()).isPositive();
    }

    @Test
    void verifiedTeacherWhoseUserIsNotActiveIsHidden() throws Exception {
        // profe51 tiene perfil VERIFIED pero usuario PENDING: no debe ser visible.
        mockMvc.perform(get("/api/teachers/" + seededTeacherId("profe51@claseya.dev")))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ helpers

    private long teacherSearchTotalFor(String subjectName) throws Exception {
        String subjectId = jdbc.queryForObject(
                "SELECT id::text FROM subjects WHERE normalized_name = ?", String.class, subjectName);
        return getJson("/api/teachers?subjectId=" + subjectId + "&page=0&size=24")
                .get("totalElements").asLong();
    }

    private String seededTeacherId(String email) {
        return jdbc.queryForObject("""
                SELECT tp.id::text FROM teacher_profiles tp
                JOIN users u ON u.id = tp.user_id
                WHERE u.email = ?
                """, String.class, email);
    }

    private int count(String table) {
        Integer total = jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        return total == null ? 0 : total;
    }

    private Map<String, Integer> counts() {
        // Map.of() tops out at 10 pairs, hence Map.ofEntries for the full table list.
        return Map.ofEntries(
                Map.entry("users", count("users")),
                Map.entry("student_profiles", count("student_profiles")),
                Map.entry("teacher_profiles", count("teacher_profiles")),
                Map.entry("subjects", count("subjects")),
                Map.entry("career_subjects", count("career_subjects")),
                Map.entry("teacher_subjects", count("teacher_subjects")),
                Map.entry("teacher_modalities", count("teacher_modalities")),
                Map.entry("teacher_education", count("teacher_education")),
                Map.entry("availability_windows", count("availability_windows")),
                Map.entry("bookings", count("bookings")),
                Map.entry("reviews", count("reviews")),
                Map.entry("favorites", count("favorites")),
                Map.entry("conversations", count("conversations")),
                Map.entry("conversation_participants", count("conversation_participants")),
                Map.entry("messages", count("messages")));
    }

    private JsonNode getJson(String uri) throws Exception {
        String body = mockMvc.perform(get(uri))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }
}
