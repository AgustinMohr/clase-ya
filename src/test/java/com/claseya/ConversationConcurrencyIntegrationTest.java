package com.claseya;

import com.claseya.academic.repository.AcademicUnitRepository;
import com.claseya.academic.repository.CareerRepository;
import com.claseya.academic.repository.CareerSubjectRepository;
import com.claseya.academic.repository.SubjectRepository;
import com.claseya.academic.repository.UniversityRepository;
import com.claseya.availability.repository.AvailabilityWindowRepository;
import com.claseya.messaging.repository.ConversationRepository;
import com.claseya.messaging.service.ConversationService;
import com.claseya.model.AcademicUnit;
import com.claseya.model.AvailabilityWindow;
import com.claseya.model.Career;
import com.claseya.model.CareerSubject;
import com.claseya.model.StudentProfile;
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
import com.claseya.student.repository.StudentProfileRepository;
import com.claseya.teacher.repository.TeacherModalityRepository;
import com.claseya.teacher.repository.TeacherProfileRepository;
import com.claseya.teacher.repository.TeacherSubjectRepository;
import com.claseya.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RACE-01: two simultaneous contacts for the same student&lt;-&gt;teacher pair must resolve to the
 * same conversation. Before V12 the read-then-create flow could insert two.
 *
 * <p>Runs on its own container on purpose: this test commits outside a rollback transaction, and
 * the classes that assert {@code conversationRepository.count()} expect a clean database.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConversationConcurrencyIntegrationTest {

    private static final String TEST_SECRET =
            "test-secret-that-is-at-least-thirty-two-bytes-long-for-hmac-sha-256";

    private static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("claseya_race")
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

    @Autowired
    private ConversationService conversationService;
    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private StudentProfileRepository studentProfileRepository;
    @Autowired
    private TeacherProfileRepository teacherProfileRepository;
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
    private TeacherSubjectRepository teacherSubjectRepository;
    @Autowired
    private TeacherModalityRepository teacherModalityRepository;
    @Autowired
    private AvailabilityWindowRepository availabilityWindowRepository;

    @Test
    void twoConcurrentContactsCreateExactlyOneConversation() throws Exception {
        User student = createUser("student", UserRole.STUDENT);
        Career career = createStudentProfile(student);

        User teacherUser = createUser("teacher", UserRole.TEACHER);
        TeacherProfile teacher = createPublishedTeacher(teacherUser, career);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<UUID> contact = () -> {
            start.await();
            return conversationService.start(student.getId(), teacher.getId(), null)
                    .conversation().id();
        };

        UUID firstId;
        UUID secondId;
        try {
            Future<UUID> first = pool.submit(contact);
            Future<UUID> second = pool.submit(contact);
            start.countDown();
            firstId = first.get(30, TimeUnit.SECONDS);
            secondId = second.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(firstId).isEqualTo(secondId);
        assertThat(conversationRepository.findBetween(student.getId(), teacherUser.getId()))
                .hasSize(1);
    }

    private User createUser(String prefix, UserRole role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setEmail(prefix + "+" + suffix + "@race.test");
        user.setName("Race " + prefix + " " + suffix);
        user.setPasswordHash(passwordEncoder.encode("Password123"));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private Career createStudentProfile(User user) {
        University university = new University();
        university.setName("UNL " + UUID.randomUUID());
        university.setSlug("unl-race-" + UUID.randomUUID());
        university.setActive(true);
        universityRepository.saveAndFlush(university);

        AcademicUnit unit = new AcademicUnit();
        unit.setUniversity(university);
        unit.setName("FICH");
        unit.setActive(true);
        academicUnitRepository.saveAndFlush(unit);

        Career career = new Career();
        career.setAcademicUnit(unit);
        career.setName("Informatica");
        career.setSlug("isi-race-" + UUID.randomUUID());
        career.setActive(true);
        careerRepository.saveAndFlush(career);

        StudentProfile profile = new StudentProfile();
        profile.setUser(user);
        profile.setUniversity(university);
        profile.setCareer(career);
        profile.setCurrentYear(2);
        studentProfileRepository.saveAndFlush(profile);
        return career;
    }

    /** The minimum D1 announcement: name, bio, one active subject, one modality, one window. */
    private TeacherProfile createPublishedTeacher(User user, Career career) {
        TeacherProfile teacher = new TeacherProfile();
        teacher.setUser(user);
        teacher.setBio("Bio de prueba");
        teacher.setVerificationStatus(VerificationStatus.PENDING);
        teacher.setRatingAverage(BigDecimal.ZERO);
        teacher.setRatingCount(0);
        teacherProfileRepository.saveAndFlush(teacher);

        Subject subject = new Subject();
        subject.setName("Matematica " + UUID.randomUUID());
        subject.setNormalizedName("matematica-" + UUID.randomUUID());
        subject.setSlug("matematica-" + UUID.randomUUID());
        subject.setActive(true);
        subjectRepository.saveAndFlush(subject);

        CareerSubject careerSubject = new CareerSubject();
        careerSubject.setCareer(career);
        careerSubject.setSubject(subject);
        careerSubject.setActive(true);
        careerSubjectRepository.saveAndFlush(careerSubject);

        TeacherSubject teacherSubject = new TeacherSubject();
        teacherSubject.setTeacher(teacher);
        teacherSubject.setCareerSubject(careerSubject);
        teacherSubject.setActive(true);
        teacherSubjectRepository.saveAndFlush(teacherSubject);

        TeacherModality modality = new TeacherModality();
        modality.setTeacher(teacher);
        modality.setModality(TeachingModality.ONLINE);
        teacherModalityRepository.saveAndFlush(modality);

        AvailabilityWindow window = new AvailabilityWindow();
        window.setTeacher(teacher);
        window.setDayOfWeek(1);
        window.setStartMinutes(9 * 60);
        window.setEndMinutes(11 * 60);
        availabilityWindowRepository.saveAndFlush(window);

        return teacher;
    }
}
