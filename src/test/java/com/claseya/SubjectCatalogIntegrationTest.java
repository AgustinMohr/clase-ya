package com.claseya;

import com.claseya.academic.repository.SubjectRepository;
import com.claseya.common.util.SlugUtils;
import com.claseya.model.Subject;
import com.claseya.model.TeacherProfile;
import com.claseya.model.User;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.claseya.model.enums.VerificationStatus;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REVIEW2-001: {@code GET /api/subjects} is paginated, {@code /popular} serves the landing and
 * neither of them can be coerced into returning the whole catalog.
 */
@Transactional
class SubjectCatalogIntegrationTest extends AbstractWebIntegrationTest {

    @Autowired private SubjectRepository subjectRepository;
    @Autowired private TeacherProfileRepository teacherProfileRepository;

    @Test
    void catalog_returnsOnePageAndTheRealTotal() throws Exception {
        seedSubjects(120, "paged");

        JsonNode page = toJson(getJson("/api/subjects?page=0&size=50", null, 200));

        assertThat(page.get("content")).hasSize(50);
        assertThat(page.get("totalElements").asLong()).isGreaterThanOrEqualTo(120);
        assertThat(page.get("size").asInt()).isEqualTo(50);
        assertThat(page.get("page").asInt()).isZero();
        assertThat(page.get("totalPages").asInt())
                .isEqualTo((int) Math.ceil(page.get("totalElements").asDouble() / 50));
    }

    @Test
    void catalog_secondPageDoesNotRepeatTheFirst() throws Exception {
        seedSubjects(60, "pages");

        JsonNode first = toJson(getJson("/api/subjects?page=0&size=20", null, 200));
        JsonNode second = toJson(getJson("/api/subjects?page=1&size=20", null, 200));

        assertThat(first.get("content")).hasSize(20);
        assertThat(second.get("content")).hasSize(20);
        assertThat(nameList(second)).doesNotContainAnyElementsOf(nameList(first));
    }

    @Test
    void catalog_searchIsServerSideAndPaginated() throws Exception {
        String needle = "algebra-" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 3; i++) {
            subjectRepository.save(subject(needle + " " + i));
        }
        subjectRepository.save(subject("historia-" + UUID.randomUUID().toString().substring(0, 8)));

        JsonNode page = toJson(getJson("/api/subjects?query=" + needle + "&size=10", null, 200));

        assertThat(page.get("content")).hasSize(3);
        assertThat(page.get("totalElements").asLong()).isEqualTo(3);
    }

    @Test
    void catalog_rejectsPageAndSizeOutOfRange() throws Exception {
        getJson("/api/subjects?size=0", null, 400);
        getJson("/api/subjects?size=101", null, 400);
        getJson("/api/subjects?page=-1", null, 400);
        getJson("/api/subjects/popular?limit=0", null, 400);
        getJson("/api/subjects/popular?limit=51", null, 400);
    }

    @Test
    void catalog_responsesAreCacheableForAMinute() throws Exception {
        mockMvc.perform(get("/api/subjects"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=60, public"));
        mockMvc.perform(get("/api/subjects/popular"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=60, public"));
    }

    @Test
    void popular_ordersByTeacherCountThenNameAndSkipsUnusedSubjects() throws Exception {
        String sharedCareerSubject = createCareerSubject();
        String sharedName = subjectNameOf(sharedCareerSubject);
        addTeachingLink(sharedCareerSubject);
        addTeachingLink(sharedCareerSubject);

        String singleCareerSubject = createCareerSubject();
        String singleName = subjectNameOf(singleCareerSubject);
        addTeachingLink(singleCareerSubject);

        String unused = "sin-docentes-" + UUID.randomUUID().toString().substring(0, 8);
        postJson("/api/subjects", adminBearer(), "{\"name\":\"" + unused + "\"}", 201);

        JsonNode popular = toJson(getJson("/api/subjects/popular?limit=50", null, 200));
        List<String> names = nameList(popular);

        assertThat(names).contains(sharedName, singleName);
        assertThat(names).doesNotContain(unused);
        assertThat(names.indexOf(sharedName)).isLessThan(names.indexOf(singleName));
    }

    @Test
    void popular_respectsTheLimit() throws Exception {
        String careerSubject = createCareerSubject();
        addTeachingLink(careerSubject);

        JsonNode popular = toJson(getJson("/api/subjects/popular?limit=1", null, 200));

        assertThat(popular.get("content")).hasSize(1);
        assertThat(popular.get("size").asInt()).isEqualTo(1);
    }

    @Test
    void popular_neverIncludesInactiveSubjects() throws Exception {
        String careerSubject = createCareerSubject();
        String name = subjectNameOf(careerSubject);
        addTeachingLink(careerSubject);

        String admin = adminBearer();
        String subjectId = subjectIdOf(careerSubject);
        mockMvc.perform(delete("/api/subjects/" + subjectId).header("Authorization", admin))
                .andExpect(status().isNoContent());

        JsonNode popular = toJson(getJson("/api/subjects/popular?limit=50", null, 200));
        assertThat(nameList(popular)).doesNotContain(name);
    }

    // ------------------------------------------------------------------ helpers

    private void seedSubjects(int count, String prefix) {
        List<Subject> batch = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            batch.add(subject(prefix + " " + String.format("%03d", i) + " " + UUID.randomUUID()));
        }
        subjectRepository.saveAll(batch);
    }

    private Subject subject(String name) {
        Subject subject = new Subject();
        subject.setName(name);
        subject.setNormalizedName(SlugUtils.normalizeName(name));
        subject.setSlug(SlugUtils.slugify(name) + "-" + UUID.randomUUID().toString().substring(0, 6));
        subject.setActive(true);
        return subject;
    }

    /** An ACTIVE teacher who teaches the given career-subject (enough for the usage ranking). */
    private void addTeachingLink(String careerSubjectId) throws Exception {
        User teacher = createUser("catalog-teacher+" + UUID.randomUUID() + "@test.com",
                UserRole.TEACHER, UserStatus.ACTIVE);
        TeacherProfile profile = new TeacherProfile();
        profile.setUser(teacher);
        profile.setVerificationStatus(VerificationStatus.PENDING);
        teacherProfileRepository.saveAndFlush(profile);
        postJson("/api/teachers/me/subjects", bearer(teacher),
                "{\"careerSubjectId\":\"%s\"}".formatted(careerSubjectId), 201);
    }

    private String subjectNameOf(String careerSubjectId) throws Exception {
        return toJson(getJson("/api/career-subjects/" + careerSubjectId, null, 200))
                .get("subjectName").asText();
    }

    private String subjectIdOf(String careerSubjectId) throws Exception {
        return toJson(getJson("/api/career-subjects/" + careerSubjectId, null, 200))
                .get("subjectId").asText();
    }

    private List<String> nameList(JsonNode page) {
        List<String> names = new ArrayList<>();
        page.get("content").forEach(node -> names.add(node.get("name").asText()));
        return names;
    }
}
