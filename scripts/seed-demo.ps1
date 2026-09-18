# ClaseYa — seed demo (solo desarrollo)
#
# Carga catálogo mínimo + profesores verificados (con precio/ciudad/foto/disponibilidad)
# + un estudiante, para que la UI se vea poblada. NO es una migración Flyway y NO
# modifica el backend: ejecuta SQL contra el contenedor de Postgres.
#
# Requisitos: Docker Desktop activo y el contenedor `claseya-pg` corriendo.
# Uso:  scripts\seed-demo.ps1
[CmdletBinding()]
param(
    [string]$Container = 'claseya-pg',
    [string]$DbUser = 'claseya',
    [string]$DbName = 'claseya'
)

$ErrorActionPreference = 'Stop'

docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host 'Docker Desktop no está disponible.' -ForegroundColor Yellow
    exit 1
}

$sql = @'
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Catálogo -------------------------------------------------------------------
INSERT INTO universities (name, short_name, slug, city, province, country, active)
VALUES ('Universidad Nacional del Litoral', 'UNL', 'unl-demo', 'Santa Fe', 'Santa Fe', 'Argentina', true)
ON CONFLICT (slug) DO NOTHING;

INSERT INTO academic_units (university_id, name, code, active)
SELECT u.id, 'Facultad de Ingeniería y Ciencias Hídricas', 'FICH', true FROM universities u WHERE u.slug = 'unl-demo'
ON CONFLICT (university_id, code) DO NOTHING;

INSERT INTO careers (academic_unit_id, name, slug, code, active)
SELECT au.id, 'Ingeniería en Informática', 'ingenieria-informatica-demo', 'ISI', true
FROM academic_units au WHERE au.code = 'FICH'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO careers (academic_unit_id, name, slug, code, active)
SELECT au.id, 'Licenciatura en Sistemas', 'licenciatura-sistemas-demo', 'LSI', true
FROM academic_units au WHERE au.code = 'FICH'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO subjects (name, normalized_name, slug, active) VALUES
    ('Matemática I', 'matemática i', 'matematica-i-demo', true),
    ('Física I', 'física i', 'fisica-i-demo', true),
    ('Álgebra', 'álgebra', 'algebra-demo', true),
    ('Inglés', 'inglés', 'ingles-demo', true),
    ('Programación I', 'programación i', 'programacion-i-demo', true)
ON CONFLICT (normalized_name) DO NOTHING;

INSERT INTO career_subjects (career_id, subject_id, year, semester, mandatory, active)
SELECT c.id, s.id, 1, 1, true, true
FROM careers c JOIN subjects s ON s.slug IN ('matematica-i-demo', 'algebra-demo', 'programacion-i-demo')
WHERE c.slug = 'ingenieria-informatica-demo'
ON CONFLICT (career_id, subject_id) DO NOTHING;

INSERT INTO career_subjects (career_id, subject_id, year, semester, mandatory, active)
SELECT c.id, s.id, 1, 1, true, true
FROM careers c JOIN subjects s ON s.slug IN ('fisica-i-demo', 'ingles-demo')
WHERE c.slug = 'licenciatura-sistemas-demo'
ON CONFLICT (career_id, subject_id) DO NOTHING;

-- Usuarios (BCrypt vía pgcrypto; contraseña demo: Password123) -----------------
INSERT INTO users (email, name, password_hash, role, status, created_at, updated_at)
VALUES
    ('ana.profe@claseya.dev', 'Ana Gómez', crypt('Password123', gen_salt('bf')), 'TEACHER', 'ACTIVE', now(), now()),
    ('bruno.profe@claseya.dev', 'Bruno Fernández', crypt('Password123', gen_salt('bf')), 'TEACHER', 'ACTIVE', now(), now()),
    ('carla.profe@claseya.dev', 'Carla Ríos', crypt('Password123', gen_salt('bf')), 'TEACHER', 'ACTIVE', now(), now()),
    ('diego.profe@claseya.dev', 'Diego Sosa', crypt('Password123', gen_salt('bf')), 'TEACHER', 'ACTIVE', now(), now()),
    ('student@claseya.dev', 'Sofía Estudiante', crypt('Password123', gen_salt('bf')), 'STUDENT', 'ACTIVE', now(), now())
ON CONFLICT (email) DO NOTHING;

-- Perfiles de profesor --------------------------------------------------------
INSERT INTO teacher_profiles (user_id, bio, verification_status, price_per_hour, city, photo_url, availability_note, rating_average, rating_count, created_at, updated_at)
SELECT u.id, 'Profesora de matemática y álgebra. Clases claras, con ejercicios y seguimiento.',
       'VERIFIED', 8000, 'Santa Fe', 'https://i.pravatar.cc/160?img=47',
       'Disponible lunes, miércoles y viernes por la tarde.', 4.8, 24, now(), now()
FROM users u WHERE u.email = 'ana.profe@claseya.dev'
ON CONFLICT (user_id) DO UPDATE SET verification_status = 'VERIFIED', price_per_hour = EXCLUDED.price_per_hour,
    city = EXCLUDED.city, photo_url = EXCLUDED.photo_url, bio = EXCLUDED.bio,
    availability_note = EXCLUDED.availability_note, updated_at = now();

INSERT INTO teacher_profiles (user_id, bio, verification_status, price_per_hour, city, photo_url, availability_note, rating_average, rating_count, created_at, updated_at)
SELECT u.id, 'Físico. Preparo parciales y finales con enfoque en resolución de problemas.',
       'VERIFIED', 9500, 'Santo Tomé', 'https://i.pravatar.cc/160?img=12',
       'Disponible martes y jueves por la mañana.', 4.6, 11, now(), now()
FROM users u WHERE u.email = 'bruno.profe@claseya.dev'
ON CONFLICT (user_id) DO UPDATE SET verification_status = 'VERIFIED', price_per_hour = EXCLUDED.price_per_hour,
    city = EXCLUDED.city, photo_url = EXCLUDED.photo_url, bio = EXCLUDED.bio,
    availability_note = EXCLUDED.availability_note, updated_at = now();

INSERT INTO teacher_profiles (user_id, bio, verification_status, price_per_hour, city, photo_url, availability_note, rating_average, rating_count, created_at, updated_at)
SELECT u.id, 'Ingeniera en sistemas. Enseño programación desde cero con proyectos reales.',
       'VERIFIED', 11000, 'Santa Fe', 'https://i.pravatar.cc/160?img=32',
       'Disponible sábados por la mañana.', 4.9, 31, now(), now()
FROM users u WHERE u.email = 'carla.profe@claseya.dev'
ON CONFLICT (user_id) DO UPDATE SET verification_status = 'VERIFIED', price_per_hour = EXCLUDED.price_per_hour,
    city = EXCLUDED.city, photo_url = EXCLUDED.photo_url, bio = EXCLUDED.bio,
    availability_note = EXCLUDED.availability_note, updated_at = now();

INSERT INTO teacher_profiles (user_id, bio, verification_status, price_per_hour, city, photo_url, availability_note, rating_average, rating_count, created_at, updated_at)
SELECT u.id, 'Profesor de inglés con experiencia en conversación y exámenes internacionales.',
       'VERIFIED', 7000, 'Santa Fe', 'https://i.pravatar.cc/160?img=68',
       'Disponible lunes a jueves por la noche.', 4.7, 19, now(), now()
FROM users u WHERE u.email = 'diego.profe@claseya.dev'
ON CONFLICT (user_id) DO UPDATE SET verification_status = 'VERIFIED', price_per_hour = EXCLUDED.price_per_hour,
    city = EXCLUDED.city, photo_url = EXCLUDED.photo_url, bio = EXCLUDED.bio,
    availability_note = EXCLUDED.availability_note, updated_at = now();

-- Materias del profesor -------------------------------------------------------
INSERT INTO teacher_subjects (teacher_id, career_subject_id, description, years_experience, active, created_at)
SELECT tp.id, cs.id, 'Clases personalizadas', 6, true, now()
FROM teacher_profiles tp
JOIN users u ON u.id = tp.user_id
JOIN career_subjects cs ON cs.subject_id = (SELECT id FROM subjects WHERE slug = 'matematica-i-demo')
WHERE u.email = 'ana.profe@claseya.dev'
ON CONFLICT (teacher_id, career_subject_id) DO NOTHING;

INSERT INTO teacher_subjects (teacher_id, career_subject_id, description, years_experience, active, created_at)
SELECT tp.id, cs.id, 'Clases personalizadas', 5, true, now()
FROM teacher_profiles tp
JOIN users u ON u.id = tp.user_id
JOIN career_subjects cs ON cs.subject_id = (SELECT id FROM subjects WHERE slug = 'algebra-demo')
WHERE u.email = 'ana.profe@claseya.dev'
ON CONFLICT (teacher_id, career_subject_id) DO NOTHING;

INSERT INTO teacher_subjects (teacher_id, career_subject_id, description, years_experience, active, created_at)
SELECT tp.id, cs.id, 'Clases personalizadas', 4, true, now()
FROM teacher_profiles tp
JOIN users u ON u.id = tp.user_id
JOIN career_subjects cs ON cs.subject_id = (SELECT id FROM subjects WHERE slug = 'fisica-i-demo')
WHERE u.email = 'bruno.profe@claseya.dev'
ON CONFLICT (teacher_id, career_subject_id) DO NOTHING;

INSERT INTO teacher_subjects (teacher_id, career_subject_id, description, years_experience, active, created_at)
SELECT tp.id, cs.id, 'Clases personalizadas', 7, true, now()
FROM teacher_profiles tp
JOIN users u ON u.id = tp.user_id
JOIN career_subjects cs ON cs.subject_id = (SELECT id FROM subjects WHERE slug = 'programacion-i-demo')
WHERE u.email = 'carla.profe@claseya.dev'
ON CONFLICT (teacher_id, career_subject_id) DO NOTHING;

INSERT INTO teacher_subjects (teacher_id, career_subject_id, description, years_experience, active, created_at)
SELECT tp.id, cs.id, 'Clases personalizadas', 8, true, now()
FROM teacher_profiles tp
JOIN users u ON u.id = tp.user_id
JOIN career_subjects cs ON cs.subject_id = (SELECT id FROM subjects WHERE slug = 'ingles-demo')
WHERE u.email = 'diego.profe@claseya.dev'
ON CONFLICT (teacher_id, career_subject_id) DO NOTHING;

-- Modalidades -----------------------------------------------------------------
INSERT INTO teacher_modalities (teacher_id, modality, created_at)
SELECT tp.id, v.modality, now()
FROM teacher_profiles tp
JOIN users u ON u.id = tp.user_id
CROSS JOIN (VALUES ('ONLINE'), ('IN_PERSON')) AS v(modality)
WHERE u.email IN ('ana.profe@claseya.dev', 'bruno.profe@claseya.dev', 'carla.profe@claseya.dev', 'diego.profe@claseya.dev')
ON CONFLICT (teacher_id, modality) DO NOTHING;

-- Disponibilidad semanal ------------------------------------------------------
INSERT INTO availability_windows (teacher_id, day_of_week, start_minutes, end_minutes, mode, status, created_at, updated_at)
SELECT tp.id, v.day, v.start_min, v.end_min, NULL, 'AVAILABLE', now(), now()
FROM teacher_profiles tp
JOIN users u ON u.id = tp.user_id
CROSS JOIN (VALUES (1, 1020, 1200), (3, 1020, 1200), (5, 540, 720)) AS v(day, start_min, end_min)
WHERE u.email = 'ana.profe@claseya.dev'
  AND NOT EXISTS (
      SELECT 1 FROM availability_windows w
      WHERE w.teacher_id = tp.id AND w.day_of_week = v.day AND w.start_minutes = v.start_min
  );

INSERT INTO availability_windows (teacher_id, day_of_week, start_minutes, end_minutes, mode, status, created_at, updated_at)
SELECT tp.id, v.day, v.start_min, v.end_min, NULL, 'AVAILABLE', now(), now()
FROM teacher_profiles tp
JOIN users u ON u.id = tp.user_id
CROSS JOIN (VALUES (2, 540, 720), (4, 540, 720)) AS v(day, start_min, end_min)
WHERE u.email = 'bruno.profe@claseya.dev'
  AND NOT EXISTS (
      SELECT 1 FROM availability_windows w
      WHERE w.teacher_id = tp.id AND w.day_of_week = v.day AND w.start_minutes = v.start_min
  );

INSERT INTO availability_windows (teacher_id, day_of_week, start_minutes, end_minutes, mode, status, created_at, updated_at)
SELECT tp.id, v.day, v.start_min, v.end_min, 'ONLINE', 'AVAILABLE', now(), now()
FROM teacher_profiles tp
JOIN users u ON u.id = tp.user_id
CROSS JOIN (VALUES (6, 540, 780)) AS v(day, start_min, end_min)
WHERE u.email = 'carla.profe@claseya.dev'
  AND NOT EXISTS (
      SELECT 1 FROM availability_windows w
      WHERE w.teacher_id = tp.id AND w.day_of_week = v.day AND w.start_minutes = v.start_min
  );

INSERT INTO availability_windows (teacher_id, day_of_week, start_minutes, end_minutes, mode, status, created_at, updated_at)
SELECT tp.id, v.day, v.start_min, v.end_min, NULL, 'AVAILABLE', now(), now()
FROM teacher_profiles tp
JOIN users u ON u.id = tp.user_id
CROSS JOIN (VALUES (1, 1140, 1320), (2, 1140, 1320), (3, 1140, 1320), (4, 1140, 1320)) AS v(day, start_min, end_min)
WHERE u.email = 'diego.profe@claseya.dev'
  AND NOT EXISTS (
      SELECT 1 FROM availability_windows w
      WHERE w.teacher_id = tp.id AND w.day_of_week = v.day AND w.start_minutes = v.start_min
  );

-- Perfil de estudiante demo ---------------------------------------------------
INSERT INTO student_profiles (user_id, university_id, career_id, current_year, bio, created_at, updated_at)
SELECT u.id, un.id, c.id, 2, 'Estudiante demo', now(), now()
FROM users u, universities un, careers c
WHERE u.email = 'student@claseya.dev'
  AND un.slug = 'unl-demo'
  AND c.slug = 'ingenieria-informatica-demo'
ON CONFLICT (user_id) DO NOTHING;

-- Resumen ---------------------------------------------------------------------
SELECT 'universidades' AS entidad, count(*)::text AS total FROM universities
UNION ALL SELECT 'carreras', count(*)::text FROM careers
UNION ALL SELECT 'materias', count(*)::text FROM subjects
UNION ALL SELECT 'profesores verificados', count(*)::text FROM teacher_profiles WHERE verification_status = 'VERIFIED'
UNION ALL SELECT 'ventanas de disponibilidad', count(*)::text FROM availability_windows;
'@

Write-Host 'Cargando datos demo...' -ForegroundColor Cyan
docker exec $Container psql -U $DbUser -d $DbName -v ON_ERROR_STOP=1 -c $sql
if ($LASTEXITCODE -ne 0) {
    Write-Host 'El seed falló. Revisá el output de psql.' -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host ''
Write-Host 'Seed demo aplicado.' -ForegroundColor Green
Write-Host 'Usuarios de prueba (contraseña: Password123):'
Write-Host '  - student@claseya.dev (estudiante, con perfil)'
Write-Host '  - ana.profe@claseya.dev / bruno.profe@claseya.dev / carla.profe@claseya.dev / diego.profe@claseya.dev (profesores)'
