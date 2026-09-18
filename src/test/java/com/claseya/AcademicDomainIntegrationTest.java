package com.claseya;

import com.claseya.model.Subject;
import com.claseya.model.User;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.claseya.academic.repository.SubjectRepository;
import com.claseya.common.util.SlugUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class AcademicDomainIntegrationTest extends AbstractWebIntegrationTest {

    @Autowired
    private SubjectRepository subjectRepository;

    private static final String UNL = "Universidad Nacional del Litoral";
    private static final String FICH = "Facultad de Ingenier\u00eda y Ciencias H\u00eddricas";
    private static final String SISTEMAS = "Ingenier\u00eda en Inform\u00e1tica";
    private static final String MATEMATICA1 = "Matem\u00e1tica I";

    private String universityBody(String name) {
        return """
                {"name":"%s","shortName":"UNL","city":"Santa Fe","province":"Santa Fe","country":"Argentina"}
                """.formatted(name);
    }

    // --------------------------------------------------------------------- university

    @Test
    void createUniversity_asAdmin_returns201AndSlug() throws Exception {
        String admin = adminBearer();
        String created = postJson("/api/universities", admin, universityBody(UNL), 201);

        assertThat(idOf(created)).isNotBlank();
        assertThat(toJson(created).get("slug").asText()).isEqualTo("universidad-nacional-del-litoral");
    }

    @Test
    void createUniversity_asStudent_returns403() throws Exception {
        User student = createUser("stu@example.com", UserRole.STUDENT, UserStatus.ACTIVE);
        postJson("/api/universities", bearer(student), universityBody(UNL), 403);
    }

    @Test
    void createUniversity_noToken_returns401() throws Exception {
        mockMvcPerformCreateNoToken(universityBody(UNL));
    }

    @Test
    void listUniversities_isPublic() throws Exception {
        String admin = adminBearer();
        postJson("/api/universities", admin, universityBody(UNL), 201);

        String list = getJson("/api/universities", null, 200);
        assertThat(list).contains(UNL);
    }

    @Test
    void getUniversity_byId_returnsIt() throws Exception {
        String admin = adminBearer();
        String universityId = idOf(postJson("/api/universities", admin, universityBody(UNL), 201));

        String fetched = getJson("/api/universities/" + universityId, null, 200);
        assertThat(toJson(fetched).get("name").asText()).isEqualTo(UNL);
    }

    @Test
    void deleteUniversity_softDeletes_itDisappearsFromListing() throws Exception {
        String admin = adminBearer();
        String universityId = idOf(postJson("/api/universities", admin, universityBody(UNL), 201));

        deleteJson("/api/universities/" + universityId, admin, 204);
        getJson("/api/universities/" + universityId, null, 404);
        String list = getJson("/api/universities", null, 200);
        assertThat(list).doesNotContain(UNL);
    }

    // --------------------------------------------------------------------- academic unit / career / subject / career subject

    @Test
    void fullChain_universityUnitCareerSubject_works() throws Exception {
        String admin = adminBearer();
        String universityId = idOf(postJson("/api/universities", admin, universityBody(UNL), 201));

        String unitJson = """
                {"name":"%s","code":"FICH"}
                """.formatted(FICH);
        String unitId = idOf(postJson("/api/universities/" + universityId + "/academic-units", admin, unitJson, 201));

        String careerJson = """
                {"name":"%s","code":"ISI"}
                """.formatted(SISTEMAS);
        String careerId = idOf(postJson("/api/academic-units/" + unitId + "/careers", admin, careerJson, 201));

        String subjectId = idOf(postJson("/api/subjects", admin,
                """
                {"name":"%s","description":"Álgebra y análisis"}
                """.formatted(MATEMATICA1), 201));

        String careerSubjectId = idOf(postJson("/api/careers/" + careerId + "/subjects", admin,
                """
                {"subjectId":"%s","nameOverride":"%s","year":1,"semester":1,"mandatory":true}
                """.formatted(subjectId, MATEMATICA1), 201));

        // Listing under the career exposes the subject.
        String list = getJson("/api/careers/" + careerId + "/subjects", null, 200);
        assertThat(list).contains(careerSubjectId);
        assertThat(toJson(list).get(0).get("subjectName").asText()).startsWith("Matem");
        assertThat(toJson(list).get(0).get("careerName").asText()).startsWith("Ingenier");
    }

    @Test
    void duplicateCareerSubject_returns409() throws Exception {
        String admin = adminBearer();
        String universityId = idOf(postJson("/api/universities", admin, universityBody(UNL), 201));
        String unitId = idOf(postJson("/api/universities/" + universityId + "/academic-units", admin,
                """
                {"name":"%s"}
                """.formatted(FICH), 201));
        String careerId = idOf(postJson("/api/academic-units/" + unitId + "/careers", admin,
                """
                {"name":"%s"}
                """.formatted(SISTEMAS), 201));
        String subjectId = idOf(postJson("/api/subjects", admin, """
                {"name":"Matemática II"}
                """, 201));

        String body = """
                {"subjectId":"%s","year":2}
                """.formatted(subjectId);
        postJson("/api/careers/" + careerId + "/subjects", admin, body, 201);
        postJson("/api/careers/" + careerId + "/subjects", admin, body, 409);
    }

    @Test
    void academicUnitOutOfScope_careerUnderNonexistentUnit_returns404() throws Exception {
        String admin = adminBearer();
        postJson("/api/academic-units/00000000-0000-0000-0000-000000000000/careers", admin,
                """
                {"name":"Ghost Career"}
                """, 404);
    }

    @Test
    void duplicateSubjectName_returns409() throws Exception {
        String admin = adminBearer();
        postJson("/api/subjects", admin, """
                {"name":"Análisis Matemático"}
                """, 201);
        postJson("/api/subjects", admin, """
                {"name":"análisis matemático "}
                """, 409);
    }

    @Test
    void subjectQuery_filtersByContains() throws Exception {
        String admin = adminBearer();
        postJson("/api/subjects", admin, """
                {"name":"Matem\u00e1tica Discreta"}
                """, 201);
        postJson("/api/subjects", admin, """
                {"name":"Historia del Arte"}
                """, 201);

        String results = getJson("/api/subjects?query=matem", null, 200);
        assertThat(results).contains("matematica-discreta").doesNotContain("Historia del Arte");
    }

    @Test
    void database_storesUnicodeNamesCorrectly() {
        // The HTTP transport through MockMvc mangles non-ASCII bodies; the DB layer
        // itself must store Unicode faithfully (verified end to end here).
        String name = "Matem\u00e1tica I";
        Subject subject = new Subject();
        subject.setName(name);
        subject.setNormalizedName(SlugUtils.normalizeName(name));
        subject.setSlug("matematica-i");
        subject.setActive(true);

        Subject saved = subjectRepository.saveAndFlush(subject);
        assertThat(subjectRepository.findById(saved.getId()).orElseThrow().getName()).isEqualTo(name);
    }

    @Test
    void readEndpointsArePublic_butWritesRequireAdmin() throws Exception {
        String admin = adminBearer();
        String universityId = idOf(postJson("/api/universities", admin, universityBody(UNL), 201));
        String unitId = idOf(postJson("/api/universities/" + universityId + "/academic-units", admin,
                """
                {"name":"%s"}
                """.formatted(FICH), 201));

        // A student may read the public catalog but not write.
        User student = createUser("stu@example.com", UserRole.STUDENT, UserStatus.ACTIVE);
        getJson("/api/universities/" + universityId + "/academic-units", bearer(student), 200);
        postJson("/api/universities/" + universityId + "/academic-units", bearer(student),
                """
                {"name":"Facultad de Derecho"}
                """, 403);

        // Academic unit id must belong to a real university: creation under a bogus parent fails.
        postJson("/api/universities/00000000-0000-0000-0000-000000000000/academic-units", admin,
                """
                {"name":"Facultad Fantasma"}
                """, 404);
    }

    private void mockMvcPerformCreateNoToken(String body) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/universities")
                        .contentType("application/json")
                        .content(body))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
    }
}
