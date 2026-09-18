package com.claseya;

import com.claseya.academic.repository.AcademicUnitRepository;
import com.claseya.academic.repository.CareerRepository;
import com.claseya.academic.repository.CareerSubjectRepository;
import com.claseya.academic.repository.SubjectRepository;
import com.claseya.academic.repository.UniversityRepository;
import com.claseya.model.AcademicUnit;
import com.claseya.model.Career;
import com.claseya.model.CareerSubject;
import com.claseya.model.Subject;
import com.claseya.model.TeacherEducation;
import com.claseya.model.TeacherModality;
import com.claseya.model.TeacherProfile;
import com.claseya.model.TeacherSubject;
import com.claseya.model.University;
import com.claseya.model.User;
import com.claseya.model.enums.TeachingModality;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.claseya.model.enums.VerificationStatus;
import com.claseya.teacher.repository.TeacherEducationRepository;
import com.claseya.teacher.repository.TeacherModalityRepository;
import com.claseya.teacher.repository.TeacherProfileRepository;
import com.claseya.teacher.repository.TeacherSubjectRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class TeacherSearchIntegrationTest extends AbstractWebIntegrationTest {

    private static final double CENTER_LAT = -31.6333;
    private static final double CENTER_LON = -60.7000;

    @Autowired
    private UniversityRepository universityRepository;
    @Autowired
    private AcademicUnitRepository academicUnitRepository;
    @Autowired
    private CareerRepository careerRepository;
    @Autowired
    private SubjectRepository subjectRepository;
    @Autowired
    private CareerSubjectRepository careerSubjectRepository;
    @Autowired
    private TeacherProfileRepository teacherProfileRepository;
    @Autowired
    private TeacherSubjectRepository teacherSubjectRepository;
    @Autowired
    private TeacherModalityRepository teacherModalityRepository;
    @Autowired
    private TeacherEducationRepository teacherEducationRepository;

    private record Catalog(University univ, Career career1, Career career2,
                           Subject subject1, Subject subject2,
                           CareerSubject csA, CareerSubject csB, CareerSubject csC) {
    }

    private Catalog seedCatalog() {
        University univ = new University();
        univ.setName("UNL");
        univ.setSlug("unl");
        univ.setActive(true);
        universityRepository.saveAndFlush(univ);

        AcademicUnit unit = new AcademicUnit();
        unit.setUniversity(univ);
        unit.setName("FICH");
        unit.setActive(true);
        academicUnitRepository.saveAndFlush(unit);

        Career c1 = new Career();
        c1.setAcademicUnit(unit);
        c1.setName("Ingenieria en Informatica");
        c1.setSlug("ingenieria-en-informatica");
        c1.setActive(true);
        careerRepository.saveAndFlush(c1);
        Career c2 = new Career();
        c2.setAcademicUnit(unit);
        c2.setName("Licenciatura en Sistemas");
        c2.setSlug("licenciatura-en-sistemas");
        c2.setActive(true);
        careerRepository.saveAndFlush(c2);

        Subject s1 = new Subject();
        s1.setName("Matematica I");
        s1.setNormalizedName("matematica i");
        s1.setSlug("matematica-i");
        s1.setActive(true);
        subjectRepository.saveAndFlush(s1);
        Subject s2 = new Subject();
        s2.setName("Fisica I");
        s2.setNormalizedName("fisica i");
        s2.setSlug("fisica-i");
        s2.setActive(true);
        subjectRepository.saveAndFlush(s2);

        CareerSubject csA = saveCareerSubject(c1, s1);
        CareerSubject csB = saveCareerSubject(c2, s2);
        CareerSubject csC = saveCareerSubject(c1, s2);
        return new Catalog(univ, c1, c2, s1, s2, csA, csB, csC);
    }

    private CareerSubject saveCareerSubject(Career career, Subject subject) {
        CareerSubject cs = new CareerSubject();
        cs.setCareer(career);
        cs.setSubject(subject);
        cs.setActive(true);
        cs.setMandatory(true);
        return careerSubjectRepository.saveAndFlush(cs);
    }

    private TeacherProfile seedTeacher(String name, UserStatus userStatus,
                                       VerificationStatus verification, BigDecimal rating,
                                       int ratingCount, Double lat, Double lon,
                                       List<CareerSubject> careerSubjects,
                                       TeachingModality... modalities) {
        User user = new User();
        user.setEmail("t" + UUID.randomUUID() + "@test.com");
        user.setName(name);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(UserRole.TEACHER);
        user.setStatus(userStatus);
        userRepository.saveAndFlush(user);

        TeacherProfile profile = new TeacherProfile();
        profile.setUser(user);
        profile.setBio("Bio de " + name);
        profile.setVerificationStatus(verification);
        profile.setRatingAverage(rating);
        profile.setRatingCount(ratingCount);
        if (lat != null) {
            profile.setLatitude(BigDecimal.valueOf(lat));
            profile.setLongitude(BigDecimal.valueOf(lon));
        }
        profile.setAddress("Privada");
        teacherProfileRepository.saveAndFlush(profile);

        for (CareerSubject cs : careerSubjects) {
            TeacherSubject ts = new TeacherSubject();
            ts.setTeacher(profile);
            ts.setCareerSubject(cs);
            ts.setActive(true);
            teacherSubjectRepository.saveAndFlush(ts);
        }
        for (TeachingModality m : modalities) {
            TeacherModality tm = new TeacherModality();
            tm.setTeacher(profile);
            tm.setModality(m);
            teacherModalityRepository.saveAndFlush(tm);
        }
        return profile;
    }

    private JsonNode search(String query) throws Exception {
        return toJson(getJson("/api/teachers" + query, null, 200));
    }

    private List<String> contentNames(JsonNode page) {
        List<String> names = new ArrayList<>();
        page.get("content").forEach(node -> names.add(node.get("displayName").asText()));
        return names;
    }

    // ------------------------------------------------------------------ visibility

    @Test
    void search_onlyReturnsVerifiedAndActive() throws Exception {
        Catalog cat = seedCatalog();
        seedTeacher("Ana Activa", UserStatus.ACTIVE, VerificationStatus.VERIFIED,
                new BigDecimal("4.5"), 3, null, null, List.of(cat.csA), TeachingModality.ONLINE);
        seedTeacher("Pedro Pending", UserStatus.ACTIVE, VerificationStatus.PENDING,
                new BigDecimal("5.0"), 1, null, null, List.of(cat.csA), TeachingModality.ONLINE);
        seedTeacher("Rosa Rejected", UserStatus.ACTIVE, VerificationStatus.REJECTED,
                new BigDecimal("4.9"), 9, null, null, List.of(cat.csA), TeachingModality.ONLINE);
        seedTeacher("Luis Inactivo", UserStatus.INACTIVE, VerificationStatus.VERIFIED,
                new BigDecimal("4.8"), 8, null, null, List.of(cat.csA), TeachingModality.ONLINE);
        seedTeacher("Sofi Suspendida", UserStatus.SUSPENDED, VerificationStatus.VERIFIED,
                new BigDecimal("4.8"), 8, null, null, List.of(cat.csA), TeachingModality.ONLINE);

        JsonNode page = search("");
        assertThat(contentNames(page)).containsExactly("Ana Activa");
        assertThat(page.get("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void search_excludesPendingDetail() throws Exception {
        Catalog cat = seedCatalog();
        TeacherProfile pending = seedTeacher("Pedro", UserStatus.ACTIVE, VerificationStatus.PENDING,
                new BigDecimal("4.0"), 1, null, null, List.of(cat.csA), TeachingModality.ONLINE);
        TeacherProfile verified = seedTeacher("Ana", UserStatus.ACTIVE, VerificationStatus.VERIFIED,
                new BigDecimal("4.0"), 1, null, null, List.of(cat.csA), TeachingModality.ONLINE);

        getJson("/api/teachers/" + pending.getId(), null, 404);
        getJson("/api/teachers/" + verified.getId(), null, 200);
    }

    // ------------------------------------------------------------------ academic filters

    @Test
    void filter_bySubject() throws Exception {
        Catalog cat = seedCatalog();
        TeacherProfile t1 = seedTeacher("Ana", UserStatus.ACTIVE, VerificationStatus.VERIFIED,
                new BigDecimal("4.5"), 3, null, null, List.of(cat.csA), TeachingModality.ONLINE);
        seedTeacher("Bea", UserStatus.ACTIVE, VerificationStatus.VERIFIED,
                new BigDecimal("4.5"), 3, null, null, List.of(cat.csB), TeachingModality.ONLINE);

        JsonNode page = search("?subjectId=" + cat.subject1.getId());
        assertThat(contentNames(page)).containsExactly("Ana");
        assertThat(page.get("content").get(0).get("id").asText()).isEqualTo(t1.getId().toString());
        assertThat(page.get("totalElements").asLong()).isEqualTo(1);

        JsonNode none = search("?subjectId=" + UUID.randomUUID());
        assertThat(none.get("totalElements").asLong()).isZero();
    }

    @Test
    void filter_byCareer() throws Exception {
        Catalog cat = seedCatalog();
        seedTeacher("Ana", UserStatus.ACTIVE, VerificationStatus.VERIFIED,
                new BigDecimal("4.5"), 3, null, null, List.of(cat.csA), TeachingModality.ONLINE);
        seedTeacher("Bea", UserStatus.ACTIVE, VerificationStatus.VERIFIED,
                new BigDecimal("4.5"), 3, null, null, List.of(cat.csB), TeachingModality.ONLINE);

        JsonNode page = search("?careerId=" + cat.career1.getId());
        assertThat(contentNames(page)).containsExactlyInAnyOrder("Ana");
        JsonNode none = search("?careerId=" + UUID.randomUUID());
        assertThat(none.get("totalElements").asLong()).isZero();
    }

    @Test
    void filter_byUniversity() throws Exception {
        Catalog cat = seedCatalog();
        seedTeacher("Ana", UserStatus.ACTIVE, VerificationStatus.VERIFIED,
                new BigDecimal("4.5"), 3, null, null, List.of(cat.csA), TeachingModality.ONLINE);
        // Teacher at a different university (own career/subject/unit).
        University other = new University();
        other.setName("UTN");
        other.setSlug("utn-" + UUID.randomUUID());
        other.setActive(true);
        universityRepository.saveAndFlush(other);
        AcademicUnit otherUnit = new AcademicUnit();
        otherUnit.setUniversity(other);
        otherUnit.setName("FR");
        otherUnit.setActive(true);
        academicUnitRepository.saveAndFlush(otherUnit);
        Career otherCareer = new Career();
        otherCareer.setAcademicUnit(otherUnit);
        otherCareer.setName("Sistemas");
        otherCareer.setSlug("sistemas-" + UUID.randomUUID());
        otherCareer.setActive(true);
        careerRepository.saveAndFlush(otherCareer);
        CareerSubject otherCs = saveCareerSubject(otherCareer, cat.subject1);
        seedTeacher("Bea", UserStatus.ACTIVE, VerificationStatus.VERIFIED,
                new BigDecimal("4.5"), 3, null, null, List.of(otherCs), TeachingModality.ONLINE);

        JsonNode page = search("?universityId=" + cat.univ.getId());
        assertThat(contentNames(page)).containsExactlyInAnyOrder("Ana");
    }

    @Test
    void filter_combinedAcademicProducesIntersection() throws Exception {
        Catalog cat = seedCatalog();
        // X teaches csA (C1,S1) and csB (C2,S2). Y teaches csC (C1,S2).
        TeacherProfile x = seedTeacher("X", UserStatus.ACTIVE, VerificationStatus.VERIFIED,
                new BigDecimal("4.5"), 3, null, null, List.of(cat.csA, cat.csB), TeachingModality.ONLINE);
        TeacherProfile y = seedTeacher("Y", UserStatus.ACTIVE, VerificationStatus.VERIFIED,
                new BigDecimal("4.5"), 3, null, null, List.of(cat.csC), TeachingModality.ONLINE);

        // university + career + subject: only X matches (csA).
        JsonNode comb = search("?universityId=" + cat.univ.getId()
                + "&careerId=" + cat.career1.getId()
                + "&subjectId=" + cat.subject1.getId());
        assertThat(contentNames(comb)).containsExactly("X");

        // career C1 + subject S2: only Y matches (csC).
        JsonNode comb2 = search("?careerId=" + cat.career1.getId() + "&subjectId=" + cat.subject2.getId());
        assertThat(contentNames(comb2)).containsExactly("Y");

        // university + subject S1: only X.
        JsonNode comb3 = search("?universityId=" + cat.univ.getId() + "&subjectId=" + cat.subject1.getId());
        assertThat(contentNames(comb3)).containsExactlyInAnyOrder("X");

        // sanity: X and Y both teach at U1.
        JsonNode univAll = search("?universityId=" + cat.univ.getId());
        assertThat(contentNames(univAll)).containsExactlyInAnyOrder("X", "Y");
        assertThat(x.getId()).isNotNull();
        assertThat(y.getId()).isNotNull();
    }

    // ------------------------------------------------------------------ modality

    @Test
    void filter_byModality_andNeverDuplicates() throws Exception {
        Catalog cat = seedCatalog();
        // One teacher with two subjects and two modalities.
        TeacherProfile both = seedTeacher("Ana Multi", UserStatus.ACTIVE, VerificationStatus.VERIFIED,
                new BigDecimal("4.5"), 3, null, null,
                List.of(cat.csA, cat.csB), TeachingModality.ONLINE, TeachingModality.IN_PERSON);
        seedTeacher("Solo Online", UserStatus.ACTIVE, VerificationStatus.VERIFIED,
                new BigDecimal("4.5"), 3, null, null, List.of(cat.csA), TeachingModality.ONLINE);
        seedTeacher("Solo Presencial", UserStatus.ACTIVE, VerificationStatus.VERIFIED,
                new BigDecimal("4.5"), 3, null, null, List.of(cat.csA), TeachingModality.IN_PERSON);

        JsonNode online = search("?modality=ONLINE");
        assertThat(contentNames(online)).containsExactlyInAnyOrder("Ana Multi", "Solo Online");
        JsonNode presencial = search("?modality=IN_PERSON");
        assertThat(contentNames(presencial)).containsExactlyInAnyOrder("Ana Multi", "Solo Presencial");

        // Modality + subject joining multiple rows must yield the teacher once.
        JsonNode combo = search("?modality=ONLINE&subjectId=" + cat.subject1.getId()
                + "&careerId=" + cat.career1.getId());
        assertThat(combo.get("totalElements").asLong()).isEqualTo(2);
        List<String> ids = new ArrayList<>();
        combo.get("content").forEach(n -> ids.add(n.get("id").asText()));
        assertThat(ids).filteredOn(id -> id.equals(both.getId().toString())).hasSize(1);
    }

    // ------------------------------------------------------------------ rating / sort / page

    @Test
    void filter_minRating() throws Exception {
        Catalog cat = seedCatalog();
        seedTeacher("Alta", UserStatus.ACTIVE, VerificationStatus.VERIFIED,
                new BigDecimal("4.8"), 10, null, null, List.of(cat.csA), TeachingModality.ONLINE);
        seedTeacher("Baja", UserStatus.ACTIVE, VerificationStatus.VERIFIED,
                new BigDecimal("3.2"), 4, null, null, List.of(cat.csA), TeachingModality.ONLINE);

        JsonNode page = search("?minRating=4.0");
        assertThat(contentNames(page)).containsExactlyInAnyOrder("Alta");

        getJson("/api/teachers?minRating=5.5", null, 400);
        getJson("/api/teachers?minRating=-1", null, 400);
    }

    @Test
    void sortByRating_stableByCount() throws Exception {
        Catalog cat = seedCatalog();
        seedTeacher("A", UserStatus.ACTIVE, VerificationStatus.VERIFIED,
                new BigDecimal("4.5"), 10, null, null, List.of(cat.csA), TeachingModality.ONLINE);
        seedTeacher("B", UserStatus.ACTIVE, VerificationStatus.VERIFIED,
                new BigDecimal("4.8"), 1, null, null, List.of(cat.csA), TeachingModality.ONLINE);
        seedTeacher("C", UserStatus.ACTIVE, VerificationStatus.VERIFIED,
                new BigDecimal("4.5"), 30, null, null, List.of(cat.csA), TeachingModality.ONLINE);

        JsonNode desc = search("?sort=rating");
        assertThat(contentNames(desc)).containsExactly("B", "C", "A");

        JsonNode asc = search("?sort=ratingAsc");
        List<String> ascNames = contentNames(asc);
        assertThat(ascNames.get(ascNames.size() - 1)).isEqualTo("B");

        JsonNode name = search("?sort=name");
        assertThat(contentNames(name)).containsExactly("A", "B", "C");

        getJson("/api/teachers?sort=banana", null, 400);
    }

    @Test
    void pagination_clampsAndValidates() throws Exception {
        Catalog cat = seedCatalog();
        for (int i = 0; i < 3; i++) {
            seedTeacher("P" + i, UserStatus.ACTIVE, VerificationStatus.VERIFIED,
                    new BigDecimal("4.0"), 1, null, null, List.of(cat.csA), TeachingModality.ONLINE);
        }
        JsonNode page0 = search("?page=0&size=2");
        assertThat(page0.get("content").size()).isEqualTo(2);
        assertThat(page0.get("totalElements").asLong()).isEqualTo(3);
        assertThat(page0.get("totalPages").asInt()).isEqualTo(2);

        JsonNode page1 = search("?page=1&size=2");
        assertThat(page1.get("content").size()).isEqualTo(1);

        JsonNode big = search("?size=2&page=2");
        assertThat(big.get("content").size()).isZero();

        getJson("/api/teachers?size=51", null, 400);
        getJson("/api/teachers?size=0", null, 400);
        getJson("/api/teachers?page=-1", null, 400);
    }

    @Test
    void search_isPublic_butPrivateRoutesProtected() throws Exception {
        search("");
        getJson("/api/teachers/00000000-0000-0000-0000-000000000000", null, 404);
        getJson("/api/teachers/me", null, 401);
    }

    // ------------------------------------------------------------------ geo

    @Test
    void geoSearch_withinRadius_returnsNearAndExcludesFar() throws Exception {
        Catalog cat = seedCatalog();
        TeacherProfile a = seedTeacher("Cerca", UserStatus.ACTIVE, VerificationStatus.VERIFIED,
                new BigDecimal("4.5"), 3, CENTER_LAT + (1.0 / 111.32), CENTER_LON,
                List.of(cat.csA), TeachingModality.ONLINE);
        TeacherProfile b = seedTeacher("Medio", UserStatus.ACTIVE, VerificationStatus.VERIFIED,
                new BigDecimal("4.5"), 3, CENTER_LAT + (3.0 / 111.32), CENTER_LON,
                List.of(cat.csA), TeachingModality.ONLINE);
        TeacherProfile c = seedTeacher("Lejos", UserStatus.ACTIVE, VerificationStatus.VERIFIED,
                new BigDecimal("4.5"), 3, CENTER_LAT + (10.0 / 111.32), CENTER_LON,
                List.of(cat.csA), TeachingModality.ONLINE);

        String query = "?latitude=" + CENTER_LAT + "&longitude=" + CENTER_LON + "&radius=5&sort=distance";
        JsonNode page = search(query);
        assertThat(contentNames(page)).containsExactly("Cerca", "Medio");

        // Distance is present and ordered near -> far.
        double d0 = page.get("content").get(0).get("distanceKm").asDouble();
        double d1 = page.get("content").get(1).get("distanceKm").asDouble();
        assertThat(d0).isLessThan(d1);
        assertThat(d0).isBetween(0.5, 1.5);
        assertThat(d1).isBetween(2.5, 3.5);
        assertThat(a.getId()).isNotNull();
        assertThat(b.getId()).isNotNull();
        assertThat(c.getId()).isNotNull();
    }

    @Test
    void geoSearch_validatesCoordinatesAndRadius() throws Exception {
        getJson("/api/teachers?latitude=" + CENTER_LAT, null, 400);
        getJson("/api/teachers?longitude=" + CENTER_LON, null, 400);
        getJson("/api/teachers?latitude=91&longitude=0&radius=5", null, 400);
        getJson("/api/teachers?latitude=0&longitude=-181&radius=5", null, 400);
        getJson("/api/teachers?latitude=" + CENTER_LAT + "&longitude=" + CENTER_LON + "&radius=0", null, 400);
        getJson("/api/teachers?latitude=" + CENTER_LAT + "&longitude=" + CENTER_LON + "&radius=101", null, 400);
        getJson("/api/teachers?radius=5", null, 400);
        getJson("/api/teachers?latitude=" + CENTER_LAT + "&longitude=" + CENTER_LON
                + "&radius=5&sort=distance", null, 200);
    }

    // ------------------------------------------------------------------ public detail

    @Test
    void publicDetail_exposesSafeDataOnly() throws Exception {
        Catalog cat = seedCatalog();
        TeacherProfile teacher = seedTeacher("Ana Completa", UserStatus.ACTIVE,
                VerificationStatus.VERIFIED, new BigDecimal("4.8"), 32,
                CENTER_LAT, CENTER_LON, List.of(cat.csA, cat.csB),
                TeachingModality.ONLINE, TeachingModality.IN_PERSON);

        TeacherEducation edu = new TeacherEducation();
        edu.setTeacher(teacher);
        edu.setInstitution("UTN");
        edu.setDegree("Ingeniero en Sistemas");
        edu.setStartYear(2005);
        edu.setEndYear(2010);
        edu.setIsVerified(false);
        teacherEducationRepository.saveAndFlush(edu);

        String body = getJson("/api/teachers/" + teacher.getId(), null, 200);
        JsonNode detail = toJson(body);
        assertThat(detail.get("displayName").asText()).isEqualTo("Ana Completa");
        assertThat(detail.get("modalities").size()).isEqualTo(2);
        assertThat(detail.get("subjects").size()).isEqualTo(2);
        assertThat(detail.get("education").size()).isEqualTo(1);
        assertThat(detail.get("education").get(0).get("degree").asText()).isEqualTo("Ingeniero en Sistemas");
        // No private/sensitive fields leak.
        assertThat(body).doesNotContain("passwordHash").doesNotContain("address")
                .doesNotContain("latitude").doesNotContain("longitude").doesNotContain("@test.com");
    }

    @Test
    void publicDetail_inactiveUserHidden() throws Exception {
        Catalog cat = seedCatalog();
        TeacherProfile teacher = seedTeacher("Invisible", UserStatus.INACTIVE,
                VerificationStatus.VERIFIED, new BigDecimal("4.8"), 3,
                null, null, List.of(cat.csA), TeachingModality.ONLINE);
        getJson("/api/teachers/" + teacher.getId(), null, 404);
    }
}
