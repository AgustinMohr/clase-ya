package com.claseya;

import com.claseya.academic.repository.AcademicUnitRepository;
import com.claseya.academic.repository.CareerRepository;
import com.claseya.academic.repository.CareerSubjectRepository;
import com.claseya.academic.repository.SubjectRepository;
import com.claseya.academic.repository.UniversityRepository;
import com.claseya.availability.repository.AvailabilityWindowRepository;
import com.claseya.model.AcademicUnit;
import com.claseya.model.AvailabilityWindow;
import com.claseya.model.Career;
import com.claseya.model.CareerSubject;
import com.claseya.model.Subject;
import com.claseya.model.TeacherModality;
import com.claseya.model.TeacherProfile;
import com.claseya.model.TeacherSubject;
import com.claseya.model.University;
import com.claseya.model.User;
import com.claseya.model.enums.TeachingModality;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.claseya.model.enums.VerificationStatus;
import com.claseya.teacher.repository.TeacherModalityRepository;
import com.claseya.teacher.repository.TeacherProfileRepository;
import com.claseya.teacher.repository.TeacherSubjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SEO-001: the sitemap is public, uses the canonical D1 visibility rule and never leaks a teacher
 * that is not published. {@code chunk-size=2} keeps the multi-chunk behaviour testable.
 */
@TestPropertySource(properties = {
        "app.sitemap.chunk-size=2",
        "app.public-url=https://claseya.test"
})
@Transactional
class SitemapIntegrationTest extends AbstractWebIntegrationTest {

    private static final String SITE = "https://claseya.test";

    @Autowired private UniversityRepository universityRepository;
    @Autowired private AcademicUnitRepository academicUnitRepository;
    @Autowired private CareerRepository careerRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private CareerSubjectRepository careerSubjectRepository;
    @Autowired private TeacherProfileRepository teacherProfileRepository;
    @Autowired private TeacherSubjectRepository teacherSubjectRepository;
    @Autowired private TeacherModalityRepository teacherModalityRepository;
    @Autowired private AvailabilityWindowRepository availabilityWindowRepository;

    private record Catalog(Subject subject, CareerSubject careerSubject) {
    }

    private Catalog seedCatalog() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        University university = new University();
        university.setName("UNL " + suffix);
        university.setSlug("unl-" + suffix);
        university.setActive(true);
        universityRepository.saveAndFlush(university);

        AcademicUnit unit = new AcademicUnit();
        unit.setUniversity(university);
        unit.setName("FICH " + suffix);
        unit.setActive(true);
        academicUnitRepository.saveAndFlush(unit);

        Career career = new Career();
        career.setAcademicUnit(unit);
        career.setName("Ingenieria " + suffix);
        career.setSlug("ingenieria-" + suffix);
        career.setActive(true);
        careerRepository.saveAndFlush(career);

        Subject subject = new Subject();
        subject.setName("Matematica " + suffix);
        subject.setNormalizedName("matematica " + suffix);
        subject.setSlug("matematica-" + suffix);
        subject.setActive(true);
        subjectRepository.saveAndFlush(subject);

        CareerSubject careerSubject = new CareerSubject();
        careerSubject.setCareer(career);
        careerSubject.setSubject(subject);
        careerSubject.setActive(true);
        careerSubject.setMandatory(true);
        careerSubjectRepository.saveAndFlush(careerSubject);

        return new Catalog(subject, careerSubject);
    }

    private TeacherProfile seedTeacher(String name, UserStatus status, Catalog catalog,
                                       boolean withAvailability) {
        User user = new User();
        user.setEmail("sitemap-" + UUID.randomUUID() + "@test.com");
        user.setName(name);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(UserRole.TEACHER);
        user.setStatus(status);
        userRepository.saveAndFlush(user);

        TeacherProfile profile = new TeacherProfile();
        profile.setUser(user);
        profile.setBio("Bio de " + name);
        profile.setCity("Santa Fe");
        profile.setAddress("Calle privada 123");
        profile.setLatitude(new java.math.BigDecimal("-31.6333"));
        profile.setLongitude(new java.math.BigDecimal("-60.7000"));
        profile.setVerificationStatus(VerificationStatus.VERIFIED);
        teacherProfileRepository.saveAndFlush(profile);

        TeacherSubject link = new TeacherSubject();
        link.setTeacher(profile);
        link.setCareerSubject(catalog.careerSubject());
        link.setActive(true);
        teacherSubjectRepository.saveAndFlush(link);

        TeacherModality modality = new TeacherModality();
        modality.setTeacher(profile);
        modality.setModality(TeachingModality.ONLINE);
        teacherModalityRepository.saveAndFlush(modality);

        if (withAvailability) {
            AvailabilityWindow window = new AvailabilityWindow();
            window.setTeacher(profile);
            window.setDayOfWeek(1);
            window.setStartMinutes(9 * 60);
            window.setEndMinutes(11 * 60);
            window.setMode(TeachingModality.ONLINE);
            availabilityWindowRepository.saveAndFlush(window);
        }
        return profile;
    }

    @Test
    void index_listsStaticAndTeacherChunks_andCountsOnlyVisibleTeachers() throws Exception {
        Catalog catalog = seedCatalog();
        seedTeacher("Ana Visible", UserStatus.ACTIVE, catalog, true);
        seedTeacher("Bea Visible", UserStatus.ACTIVE, catalog, true);
        seedTeacher("Caro Visible", UserStatus.ACTIVE, catalog, true);
        seedTeacher("Inactiva", UserStatus.INACTIVE, catalog, true);
        seedTeacher("Suspendida", UserStatus.SUSPENDED, catalog, true);
        seedTeacher("Sin Anuncio", UserStatus.ACTIVE, catalog, false);

        String xml = getXml("/sitemap.xml");

        assertThat(xml)
                .contains("<sitemapindex")
                .contains(SITE + "/sitemaps/static.xml")
                .contains(SITE + "/sitemaps/teachers-1.xml")
                .contains(SITE + "/sitemaps/teachers-2.xml")
                .doesNotContain("teachers-3.xml")
                .contains("<lastmod>");
        assertWellFormed(xml);
    }

    @Test
    void staticSitemap_listsHomeSearchAndPopularSubjects() throws Exception {
        Catalog catalog = seedCatalog();
        seedTeacher("Ana Visible", UserStatus.ACTIVE, catalog, true);

        String xml = getXml("/sitemaps/static.xml");

        assertThat(xml)
                .contains("<loc>" + SITE + "/</loc>")
                .contains("<loc>" + SITE + "/buscar</loc>")
                .contains(SITE + "/buscar?materia=" + catalog.subject().getName().replace(" ", "%20"));
        assertWellFormed(xml);
    }

    @Test
    void teachersChunk_containsVisibleTeachersOnly_andNoPrivateData() throws Exception {
        Catalog catalog = seedCatalog();
        TeacherProfile visible = seedTeacher("Ana Visible", UserStatus.ACTIVE, catalog, true);
        TeacherProfile inactive = seedTeacher("Inactiva", UserStatus.INACTIVE, catalog, true);
        TeacherProfile suspended = seedTeacher("Suspendida", UserStatus.SUSPENDED, catalog, true);
        TeacherProfile unpublished = seedTeacher("Sin Anuncio", UserStatus.ACTIVE, catalog, false);

        String xml = getXml("/sitemaps/teachers-1.xml");

        assertThat(xml)
                .contains(SITE + "/profesores/" + visible.getId())
                .contains("<lastmod>")
                .doesNotContain(inactive.getId().toString())
                .doesNotContain(suspended.getId().toString())
                .doesNotContain(unpublished.getId().toString())
                .doesNotContain("@test.com")
                .doesNotContain("Calle privada")
                .doesNotContain("-31.6333")
                .doesNotContain("-60.7000");
        assertWellFormed(xml);
    }

    @Test
    void teacherChunks_splitWithoutDuplicatesOrLosses() throws Exception {
        Catalog catalog = seedCatalog();
        TeacherProfile first = seedTeacher("Ana Visible", UserStatus.ACTIVE, catalog, true);
        TeacherProfile second = seedTeacher("Bea Visible", UserStatus.ACTIVE, catalog, true);
        TeacherProfile third = seedTeacher("Caro Visible", UserStatus.ACTIVE, catalog, true);

        String page1 = getXml("/sitemaps/teachers-1.xml");
        String page2 = getXml("/sitemaps/teachers-2.xml");

        String all = page1 + page2;
        assertThat(all)
                .contains(first.getId().toString(), second.getId().toString(), third.getId().toString());
        assertThat(countOccurrences(all, "/profesores/")).isEqualTo(3);
        assertThat(countOccurrences(page1, "/profesores/")).isEqualTo(2);
        assertThat(countOccurrences(page2, "/profesores/")).isEqualTo(1);
    }

    private int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    @Test
    void invalidTeacherChunksReturn404() throws Exception {
        seedCatalog();

        getXml("/sitemaps/teachers-0.xml", 404);
        getXml("/sitemaps/teachers-999.xml", 404);
        getXml("/sitemaps/teachers-abc.xml", 404);
    }

    @Test
    void sitemapIsPublic_andCacheable() throws Exception {
        seedCatalog();

        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andExpect(header().string("Cache-Control",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("max-age=3600"),
                                org.hamcrest.Matchers.containsString("s-maxage=86400"))));
    }

    private String getXml(String uri) throws Exception {
        return getXml(uri, 200);
    }

    private String getXml(String uri, int expectedStatus) throws Exception {
        return mockMvc.perform(get(uri))
                .andExpect(status().is(expectedStatus))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private void assertWellFormed(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertThat(document.getDocumentElement()).isNotNull();
    }
}
