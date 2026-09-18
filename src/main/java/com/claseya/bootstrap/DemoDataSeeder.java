package com.claseya.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * DEVELOPMENT-ONLY demo data, applied idempotently on startup so
 * {@code spring-boot:run} shows a populated UI.
 *
 * <p>Enabled by {@code app.demo-seed.enabled=true} (default); the integration
 * test base disables it. Every statement is an upsert/no-op, so it coexists with
 * any existing catalog or users and never duplicates rows. Must be disabled in
 * real environments.
 */
@Component
@ConditionalOnProperty(name = "app.demo-seed.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String DEMO_PASSWORD = "Password123";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String hash = passwordEncoder.encode(DEMO_PASSWORD);

        UUID universityId = university();
        UUID unitId = academicUnit(universityId);
        UUID informaticaId = career(unitId, "Ingeniería en Informática", "ingenieria-informatica-demo", "ISI");
        UUID sistemasId = career(unitId, "Licenciatura en Sistemas", "licenciatura-sistemas-demo", "LSI");

        UUID matematica = subject("Matemática I", "matematica-i-demo");
        UUID algebra = subject("Álgebra", "algebra-demo");
        UUID fisica = subject("Física I", "fisica-i-demo");
        UUID ingles = subject("Inglés", "ingles-demo");
        UUID programacion = subject("Programación I", "programacion-i-demo");

        UUID csMatematica = careerSubject(informaticaId, matematica);
        UUID csAlgebra = careerSubject(informaticaId, algebra);
        UUID csProgramacion = careerSubject(informaticaId, programacion);
        UUID csFisica = careerSubject(sistemasId, fisica);
        UUID csIngles = careerSubject(sistemasId, ingles);

        teacher(hash, "ana.profe@claseya.dev", "Ana Gómez",
                "Profesora de matemática y álgebra. Clases claras, con ejercicios y seguimiento.",
                new BigDecimal("8000.00"), "Santa Fe", "https://i.pravatar.cc/160?img=47",
                "Disponible lunes, miércoles y viernes por la tarde.",
                List.of(csMatematica, csAlgebra),
                new int[][]{{1, 1020, 1200}, {3, 1020, 1200}, {5, 540, 720}});

        teacher(hash, "bruno.profe@claseya.dev", "Bruno Fernández",
                "Físico. Preparo parciales y finales con enfoque en resolución de problemas.",
                new BigDecimal("9500.00"), "Santo Tomé", "https://i.pravatar.cc/160?img=12",
                "Disponible martes y jueves por la mañana.",
                List.of(csFisica), new int[][]{{2, 540, 720}, {4, 540, 720}});

        teacher(hash, "carla.profe@claseya.dev", "Carla Ríos",
                "Ingeniera en sistemas. Enseño programación desde cero con proyectos reales.",
                new BigDecimal("11000.00"), "Santa Fe", "https://i.pravatar.cc/160?img=32",
                "Disponible sábados por la mañana.",
                List.of(csProgramacion), new int[][]{{6, 540, 780}});

        teacher(hash, "diego.profe@claseya.dev", "Diego Sosa",
                "Profesor de inglés con experiencia en conversación y exámenes internacionales.",
                new BigDecimal("7000.00"), "Santa Fe", "https://i.pravatar.cc/160?img=68",
                "Disponible lunes a jueves por la noche.",
                List.of(csIngles),
                new int[][]{{1, 1140, 1320}, {2, 1140, 1320}, {3, 1140, 1320}, {4, 1140, 1320}});

        student(hash, "student@claseya.dev", "Sofía Estudiante", universityId, informaticaId);

        Integer verified = jdbc.queryForObject(
                "SELECT count(*) FROM teacher_profiles WHERE verification_status = 'VERIFIED'", Integer.class);
        log.info("Demo seed ready (password '{}'): {} verified teacher profiles available", DEMO_PASSWORD, verified);
    }

    private UUID university() {
        jdbc.update("""
                INSERT INTO universities (name, short_name, slug, city, province, country, active)
                VALUES (?, ?, ?, ?, ?, ?, true)
                ON CONFLICT (slug) DO NOTHING
                """, "Universidad Nacional del Litoral", "UNL", "unl-demo", "Santa Fe", "Santa Fe", "Argentina");
        return jdbc.queryForObject("SELECT id FROM universities WHERE slug = ?", UUID.class, "unl-demo");
    }

    private UUID academicUnit(UUID universityId) {
        jdbc.update("""
                INSERT INTO academic_units (university_id, name, code, active)
                VALUES (?, ?, ?, true)
                ON CONFLICT (university_id, code) DO NOTHING
                """, universityId, "Facultad de Ingeniería y Ciencias Hídricas", "FICH");
        return jdbc.queryForObject(
                "SELECT id FROM academic_units WHERE university_id = ? AND code = ?", UUID.class, universityId, "FICH");
    }

    private UUID career(UUID unitId, String name, String slug, String code) {
        jdbc.update("""
                INSERT INTO careers (academic_unit_id, name, slug, code, active)
                VALUES (?, ?, ?, ?, true)
                ON CONFLICT (slug) DO NOTHING
                """, unitId, name, slug, code);
        return jdbc.queryForObject("SELECT id FROM careers WHERE slug = ?", UUID.class, slug);
    }

    private UUID subject(String name, String slug) {
        jdbc.update("""
                INSERT INTO subjects (name, normalized_name, slug, active)
                VALUES (?, ?, ?, true)
                ON CONFLICT (normalized_name) DO NOTHING
                """, name, name.trim().toLowerCase(), slug);
        return jdbc.queryForObject("SELECT id FROM subjects WHERE slug = ?", UUID.class, slug);
    }

    private UUID careerSubject(UUID careerId, UUID subjectId) {
        jdbc.update("""
                INSERT INTO career_subjects (career_id, subject_id, year, semester, mandatory, active)
                VALUES (?, ?, 1, 1, true, true)
                ON CONFLICT (career_id, subject_id) DO NOTHING
                """, careerId, subjectId);
        return jdbc.queryForObject(
                "SELECT id FROM career_subjects WHERE career_id = ? AND subject_id = ?", UUID.class, careerId, subjectId);
    }

    private void teacher(String hash, String email, String name, String bio, BigDecimal price, String city,
                         String photoUrl, String availabilityNote, List<UUID> careerSubjects, int[][] windows) {
        UUID userId = user(hash, email, name, "TEACHER");
        jdbc.update("""
                INSERT INTO teacher_profiles
                    (user_id, bio, verification_status, price_per_hour, city, photo_url, availability_note,
                     rating_average, rating_count, created_at, updated_at)
                VALUES (?, ?, 'VERIFIED', ?, ?, ?, ?, 4.7, 12, now(), now())
                ON CONFLICT (user_id) DO UPDATE SET
                    bio = EXCLUDED.bio, verification_status = 'VERIFIED', price_per_hour = EXCLUDED.price_per_hour,
                    city = EXCLUDED.city, photo_url = EXCLUDED.photo_url,
                    availability_note = EXCLUDED.availability_note, updated_at = now()
                """, userId, bio, price, city, photoUrl, availabilityNote);
        UUID teacherId = jdbc.queryForObject("SELECT id FROM teacher_profiles WHERE user_id = ?", UUID.class, userId);

        for (UUID careerSubjectId : careerSubjects) {
            jdbc.update("""
                    INSERT INTO teacher_subjects (teacher_id, career_subject_id, description, years_experience, active, created_at)
                    VALUES (?, ?, 'Clases personalizadas', 5, true, now())
                    ON CONFLICT (teacher_id, career_subject_id) DO NOTHING
                    """, teacherId, careerSubjectId);
        }
        for (String modality : new String[]{"ONLINE", "IN_PERSON"}) {
            jdbc.update("""
                    INSERT INTO teacher_modalities (teacher_id, modality, created_at)
                    VALUES (?, ?, now())
                    ON CONFLICT (teacher_id, modality) DO NOTHING
                    """, teacherId, modality);
        }
        for (int[] window : windows) {
            jdbc.update("""
                    INSERT INTO availability_windows
                        (teacher_id, day_of_week, start_minutes, end_minutes, mode, status, created_at, updated_at)
                    SELECT ?, ?, ?, ?, NULL, 'AVAILABLE', now(), now()
                    WHERE NOT EXISTS (
                        SELECT 1 FROM availability_windows w
                        WHERE w.teacher_id = ? AND w.day_of_week = ? AND w.start_minutes = ?
                    )
                    """, teacherId, window[0], window[1], window[2], teacherId, window[0], window[1]);
        }
    }

    private void student(String hash, String email, String name, UUID universityId, UUID careerId) {
        UUID userId = user(hash, email, name, "STUDENT");
        jdbc.update("""
                INSERT INTO student_profiles (user_id, university_id, career_id, current_year, bio, created_at, updated_at)
                VALUES (?, ?, ?, 2, 'Estudiante demo', now(), now())
                ON CONFLICT (user_id) DO NOTHING
                """, userId, universityId, careerId);
    }

    private UUID user(String hash, String email, String name, String role) {
        jdbc.update("""
                INSERT INTO users (email, name, password_hash, role, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', now(), now())
                ON CONFLICT (email) DO UPDATE SET
                    name = EXCLUDED.name, role = EXCLUDED.role, status = 'ACTIVE', updated_at = now()
                """, email, name, hash, role);
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
    }
}
