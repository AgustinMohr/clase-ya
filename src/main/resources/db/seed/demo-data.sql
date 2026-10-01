-- =============================================================================
-- ClaseYa — datos demo para desarrollo local
--
-- NO es una migración Flyway: es un archivo de DATOS para el entorno local.
-- Se ejecuta con:  scripts\seed-demo.ps1
--
-- Propiedades:
--   * Idempotente ....... se puede correr N veces sin duplicar registros.
--   * No destructivo .... nunca borra ni pisa datos existentes (solo inserta lo
--                         que falta y actualiza agregados de sus propios datos).
--   * Determinístico .... los valores "aleatorios" derivan de md5/hashtext, así
--                         que dos corridas producen exactamente los mismos datos.
--   * Autosuficiente .... funciona en una base recién migrada, sin depender del
--                         DemoDataSeeder de la aplicación.
--
-- Credenciales de las cuentas generadas (password: Password123):
--   profesores ... profe01@claseya.dev .. profe55@claseya.dev
--   estudiantes .. alumno01@claseya.dev .. alumno35@claseya.dev
--   admin ........ admin@claseya.dev
-- Las cuentas previas (ana/bruno/carla/diego.profe, student@claseya.dev y
-- cualquier cuenta real) se preservan intactas.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Entero estable en [0, mod) derivado de una semilla de texto.
-- Centraliza la aritmética de hashtext (evita negativos y desbordes) y hace que
-- la generación de datos sea reproducible.
CREATE OR REPLACE FUNCTION pg_temp.demo_rand(seed text, mod int)
RETURNS int LANGUAGE sql IMMUTABLE AS $$
    SELECT (((hashtext(seed)::bigint % mod) + mod) % mod)::int
$$;

-- =============================================================================
-- 1) CATÁLOGO ACADÉMICO
-- =============================================================================

INSERT INTO universities (name, short_name, slug, city, province, country, active) VALUES
    ('Universidad Nacional del Litoral', 'UNL', 'unl-demo',      'Santa Fe', 'Santa Fe', 'Argentina', true),
    ('Universidad Tecnológica Nacional', 'UTN', 'utn-frsf-demo', 'Santa Fe', 'Santa Fe', 'Argentina', true),
    ('Universidad Nacional de Rosario',  'UNR', 'unr-demo',      'Rosario',  'Santa Fe', 'Argentina', true)
ON CONFLICT (slug) DO NOTHING;

INSERT INTO academic_units (university_id, name, code, active)
SELECT u.id, v.name, v.code, true
FROM (VALUES
    ('unl-demo',      'Facultad de Ingeniería y Ciencias Hídricas',              'FICH'),
    ('unl-demo',      'Facultad de Ciencias Económicas',                         'FCE'),
    ('unl-demo',      'Facultad de Humanidades y Ciencias',                      'FHUC'),
    ('unl-demo',      'Facultad de Ciencias Jurídicas y Sociales',               'FCJS'),
    ('utn-frsf-demo', 'Facultad Regional Santa Fe',                              'UTN-FRSF'),
    ('unr-demo',      'Facultad de Ciencias Exactas, Ingeniería y Agrimensura',  'FCEIA')
) AS v(uni_slug, name, code)
JOIN universities u ON u.slug = v.uni_slug
ON CONFLICT (university_id, code) DO NOTHING;

INSERT INTO careers (academic_unit_id, name, slug, code, active)
SELECT au.id, v.name, v.slug, v.code, true
FROM (VALUES
    ('FICH',     'Ingeniería en Informática',             'ingenieria-informatica-demo',   'ISI'),
    ('FICH',     'Licenciatura en Sistemas',              'licenciatura-sistemas-demo',    'LSI'),
    ('FCE',      'Contador Público',                      'contador-publico-demo',         'CP'),
    ('FHUC',     'Licenciatura en Historia',              'licenciatura-historia-demo',    'LH'),
    ('FCJS',     'Abogacía',                              'abogacia-demo',                 'ABG'),
    ('UTN-FRSF', 'Ingeniería en Sistemas de Información', 'ingenieria-sistemas-info-demo', 'ISI-UTN'),
    ('FCEIA',    'Licenciatura en Economía',              'licenciatura-economia-demo',    'LE'),
    ('FHUC',     'Profesorado de Matemática',             'profesorado-matematica-demo',   'PM')
) AS v(unit_code, name, slug, code)
JOIN academic_units au ON au.code = v.unit_code
ON CONFLICT (slug) DO NOTHING;

-- Catálogo de materias: 23 materias (5 reutilizan las claves del seeder de la
-- app: mismas normalized_name/slug, así que ON CONFLICT las preserva).
-- Casos borde incluidos a propósito:
--   * 'química general' y 'portugués' ....... existen pero ningún profesor las dicta -> 0 resultados.
--   * 'estadística' y 'derecho civil' ....... las dicta exactamente 1 profesor -> 1 resultado.
--   * 'física ii' ........................... active = false (materia desactivada).
--   * 'biología celular' y 'psicología general' ... sin carrera asociada.
INSERT INTO subjects (name, normalized_name, slug, description, active) VALUES
    ('Matemática I', 'matemática i', 'matematica-i-demo', NULL, true),
    ('Álgebra', 'álgebra', 'algebra-demo', NULL, true),
    ('Física I', 'física i', 'fisica-i-demo', NULL, true),
    ('Inglés', 'inglés', 'ingles-demo', NULL, true),
    ('Programación I', 'programación i', 'programacion-i-demo', NULL, true),
    ('Análisis Matemático I', 'análisis matemático i', 'analisis-matematico-i-demo',
     'Límites, derivadas e integrales de una variable.', true),
    ('Análisis Matemático II', 'análisis matemático ii', 'analisis-matematico-ii-demo',
     'Series, integrales múltiples y ecuaciones diferenciales.', true),
    ('Física II', 'física ii', 'fisica-ii-demo',
     'Electromagnetismo y termodinámica. (Materia desactivada: caso de catálogo inactivo.)', false),
    ('Química General', 'química general', 'quimica-general-demo',
     'Estructura atómica, enlace químico y estequiometría.', true),
    ('Estadística', 'estadística', 'estadistica-demo',
     'Estadística descriptiva e inferencial aplicada.', true),
    ('Historia Argentina', 'historia argentina', 'historia-argentina-demo',
     'Del período colonial a la Argentina contemporánea.', true),
    ('Lengua y Literatura', 'lengua y literatura', 'lengua-literatura-demo',
     'Comprensión lectora, gramática y producción de textos.', true),
    ('Biología Celular', 'biología celular', 'biologia-celular-demo',
     'Estructura y función de la célula.', true),
    ('Contabilidad Básica', 'contabilidad básica', 'contabilidad-basica-demo',
     'Registración contable y estados básicos.', true),
    ('Derecho Civil', 'derecho civil', 'derecho-civil-demo',
     'Personas, obligaciones y contratos.', true),
    ('Portugués', 'portugués', 'portugues-demo',
     'Comprensión y conversación en portugués.', true),
    ('Programación II', 'programación ii', 'programacion-ii-demo',
     'Estructuras de datos y programación orientada a objetos.', true),
    ('Bases de Datos', 'bases de datos', 'bases-de-datos-demo',
     'Modelo relacional, SQL y normalización.', true),
    ('Sistemas Operativos', 'sistemas operativos', 'sistemas-operativos-demo',
     'Procesos, memoria, archivos y concurrencia.', true),
    ('Redes de Computadoras', 'redes de computadoras', 'redes-de-computadoras-demo',
     'Modelo TCP/IP, protocolos y seguridad básica.', true),
    ('Economía I', 'economía i', 'economia-i-demo',
     'Microeconomía: oferta, demanda y mercados.', true),
    ('Psicología General', 'psicología general', 'psicologia-general-demo',
     'Introducción a los procesos psicológicos básicos.', true)
ON CONFLICT (normalized_name) DO NOTHING;

-- Materias por carrera. Incluye los 5 vínculos ya existentes para que el archivo
-- funcione también en una base recién migrada; ON CONFLICT los preserva.
INSERT INTO career_subjects (career_id, subject_id, year, semester, mandatory, active)
SELECT c.id, s.id, v.year, v.semester, true, true
FROM (VALUES
    -- Ingeniería en Informática (FICH / UNL)
    ('ingenieria-informatica-demo',   'matemática i',            1, 1),
    ('ingenieria-informatica-demo',   'álgebra',                 1, 1),
    ('ingenieria-informatica-demo',   'programación i',          1, 1),
    ('ingenieria-informatica-demo',   'análisis matemático i',   1, 2),
    ('ingenieria-informatica-demo',   'química general',         1, 2),
    ('ingenieria-informatica-demo',   'física i',                1, 2),
    ('ingenieria-informatica-demo',   'inglés',                  2, 1),
    ('ingenieria-informatica-demo',   'análisis matemático ii',  2, 1),
    ('ingenieria-informatica-demo',   'estadística',             2, 1),
    ('ingenieria-informatica-demo',   'programación ii',         2, 1),
    ('ingenieria-informatica-demo',   'física ii',               2, 2),
    ('ingenieria-informatica-demo',   'bases de datos',          3, 1),
    ('ingenieria-informatica-demo',   'sistemas operativos',     3, 2),
    ('ingenieria-informatica-demo',   'redes de computadoras',   4, 1),
    -- Licenciatura en Sistemas (FICH / UNL)
    ('licenciatura-sistemas-demo',    'física i',                1, 1),
    ('licenciatura-sistemas-demo',    'inglés',                  1, 1),
    ('licenciatura-sistemas-demo',    'programación i',          1, 1),
    ('licenciatura-sistemas-demo',    'matemática i',            1, 2),
    ('licenciatura-sistemas-demo',    'álgebra',                 1, 2),
    ('licenciatura-sistemas-demo',    'bases de datos',          2, 1),
    ('licenciatura-sistemas-demo',    'sistemas operativos',     3, 1),
    ('licenciatura-sistemas-demo',    'redes de computadoras',   3, 2),
    -- Contador Público (FCE / UNL)
    ('contador-publico-demo',         'contabilidad básica',     1, 1),
    ('contador-publico-demo',         'economía i',              1, 1),
    ('contador-publico-demo',         'matemática i',            1, 2),
    ('contador-publico-demo',         'álgebra',                 1, 2),
    ('contador-publico-demo',         'estadística',             2, 1),
    -- Licenciatura en Historia (FHUC / UNL)
    ('licenciatura-historia-demo',    'historia argentina',      1, 1),
    ('licenciatura-historia-demo',    'lengua y literatura',     1, 1),
    ('licenciatura-historia-demo',    'inglés',                  2, 1),
    ('licenciatura-historia-demo',    'portugués',               2, 2),
    -- Abogacía (FCJS / UNL)
    ('abogacia-demo',                 'derecho civil',           2, 1),
    ('abogacia-demo',                 'historia argentina',      1, 1),
    ('abogacia-demo',                 'lengua y literatura',     1, 1),
    -- Ingeniería en Sistemas de Información (UTN FR Santa Fe)
    ('ingenieria-sistemas-info-demo', 'programación i',          1, 1),
    ('ingenieria-sistemas-info-demo', 'análisis matemático i',   1, 1),
    ('ingenieria-sistemas-info-demo', 'física i',                1, 2),
    ('ingenieria-sistemas-info-demo', 'programación ii',         2, 1),
    ('ingenieria-sistemas-info-demo', 'bases de datos',          2, 2),
    -- Licenciatura en Economía (FCEIA / UNR)
    ('licenciatura-economia-demo',    'economía i',              1, 1),
    ('licenciatura-economia-demo',    'contabilidad básica',     1, 2),
    ('licenciatura-economia-demo',    'matemática i',            1, 1),
    ('licenciatura-economia-demo',    'estadística',             2, 1),
    -- Profesorado de Matemática (FHUC / UNL)
    ('profesorado-matematica-demo',   'matemática i',            1, 1),
    ('profesorado-matematica-demo',   'álgebra',                 1, 1),
    ('profesorado-matematica-demo',   'análisis matemático i',   2, 1),
    ('profesorado-matematica-demo',   'estadística',             3, 1)
) AS v(career_slug, subject_name, year, semester)
JOIN careers c ON c.slug = v.career_slug
JOIN subjects s ON s.normalized_name = v.subject_name
ON CONFLICT (career_id, subject_id) DO NOTHING;

-- =============================================================================
-- 2) USUARIOS  (91 cuentas nuevas; password: Password123)
--    Los nombres se combinan con aritmética estable para que la corrida sea
--    reproducible y no haya 55 filas idénticas escritas a mano.
-- =============================================================================

-- --- Profesores: profe01 .. profe55 ------------------------------------------
-- Casos borde de estado (no pueden autenticarse y no deben aparecer en la
-- búsqueda pública): 51 y 55 PENDING, 53 INACTIVE, 54 SUSPENDED.
-- profe03 tiene google_sub para poder probar la vinculación de OAUTH-001.
INSERT INTO users (email, name, password_hash, role, status, google_sub, created_at, updated_at, last_login_at)
SELECT
    'profe' || lpad(i::text, 2, '0') || '@claseya.dev',
    (ARRAY['Ana','Bruno','Carla','Diego','Elena','Facundo','Gabriela','Hernán','Ignacio','Julieta',
           'Karina','Lucas','Mariana','Nicolás','Olivia','Pablo','Rocío','Santiago','Tamara','Tomás',
           'Valentina','Verónica','Walter','Ximena','Yamila','Zoe','Agustina','Bautista','Camila','Damián',
           'Emilia','Franco'])[1 + ((i - 1) % 32)]
      || ' ' ||
    (ARRAY['Gómez','Fernández','Ríos','Sosa','Pereyra','Benítez','Romero','Acosta','Villalba','Quiroga',
           'Molina','Cáceres','Duarte','Ibarra','Ledesma','Maidana','Ojeda','Paz','Ramírez','Silva',
           'Torres','Vega','Zárate','Bustos'])[1 + (((i - 1) * 5 + 3) % 24)],
    crypt('Password123', gen_salt('bf')),
    'TEACHER',
    CASE i WHEN 51 THEN 'PENDING' WHEN 53 THEN 'INACTIVE' WHEN 54 THEN 'SUSPENDED' WHEN 55 THEN 'PENDING'
           ELSE 'ACTIVE' END,
    CASE WHEN i = 3 THEN 'demo-google-sub-profe03' ELSE NULL END,
    now() - ((i % 120) || ' days')::interval,
    now() - ((i % 60) || ' days')::interval,
    CASE WHEN i IN (51, 53, 54, 55) THEN NULL ELSE now() - ((i % 30) || ' days')::interval END
FROM generate_series(1, 55) AS i
ON CONFLICT (email) DO NOTHING;

-- --- Estudiantes: alumno01 .. alumno35 ---------------------------------------
-- Casos borde de estado: 33 PENDING, 34 INACTIVE, 35 SUSPENDED.
-- alumno04 tiene google_sub (vinculación OAUTH-001).
INSERT INTO users (email, name, password_hash, role, status, google_sub, created_at, updated_at, last_login_at)
SELECT
    'alumno' || lpad(i::text, 2, '0') || '@claseya.dev',
    (ARRAY['Sofía','Joaquín','Micaela','Thiago','Lucía','Franco','Agustina','Ramiro','Catalina','Ezequiel',
           'Martina','Gonzalo','Paula','Emiliano','Renata','Cristian','Florencia','Matías','Antonella','Lautaro',
           'Josefina','Maximiliano','Milagros','Rodrigo','Celeste','Tobías','Abril','Nahuel','Delfina','Iván',
           'Priscila','Facundo'])[1 + ((i + 7) % 32)]
      || ' ' ||
    (ARRAY['Gómez','Fernández','Ríos','Sosa','Pereyra','Benítez','Romero','Acosta','Villalba','Quiroga',
           'Molina','Cáceres','Duarte','Ibarra','Ledesma','Maidana','Ojeda','Paz','Ramírez','Silva',
           'Torres','Vega','Zárate','Bustos'])[1 + ((i * 11 + 5) % 24)],
    crypt('Password123', gen_salt('bf')),
    'STUDENT',
    CASE i WHEN 33 THEN 'PENDING' WHEN 34 THEN 'INACTIVE' WHEN 35 THEN 'SUSPENDED' ELSE 'ACTIVE' END,
    CASE WHEN i = 4 THEN 'demo-google-sub-alumno04' ELSE NULL END,
    now() - ((i % 90) || ' days')::interval,
    now() - ((i % 45) || ' days')::interval,
    CASE WHEN i IN (33, 34, 35) THEN NULL ELSE now() - ((i % 20) || ' days')::interval END
FROM generate_series(1, 35) AS i
ON CONFLICT (email) DO NOTHING;

-- --- Administrador -----------------------------------------------------------
-- Recuerda: ADMIN no puede auto-registrarse; esta cuenta permite probar /api/test/admin.
INSERT INTO users (email, name, password_hash, role, status, created_at, updated_at, last_login_at)
VALUES ('admin@claseya.dev', 'Administrador ClaseYa', crypt('Password123', gen_salt('bf')),
        'ADMIN', 'ACTIVE', now(), now(), NULL)
ON CONFLICT (email) DO NOTHING;

-- =============================================================================
-- 3) PERFILES
-- =============================================================================

-- --- Perfiles de profesor (profe01 .. profe55) --------------------------------
-- Casos borde:
--   * profe52 REJECTED y profe53/54/55 PENDING -> no deben aparecer en la búsqueda.
--   * profe51 con perfil VERIFIED pero usuario PENDING -> tampoco aparece.
--   * profe49: sin precio y con bio muy corta.  profe50: sin precio y bio muy larga.
--   * profe48: sin foto (la card debe caer a las iniciales).
--   * profe49/50 en otras ciudades y 51..55 sin coordenadas.
--   * rating_average/rating_count arrancan en 0 y se recalculan desde las reviews.
--   * price_per_hour: mínimo 3000 (profe06), máximo 25000 (profe05).
INSERT INTO teacher_profiles (user_id, bio, verification_status, latitude, longitude, address,
                              rating_average, rating_count, availability_note,
                              price_per_hour, city, photo_url, created_at, updated_at)
SELECT
    u.id,
    CASE
      WHEN i = 49 THEN 'Profe de mate.'
      WHEN i = 50 THEN 'Doy clases desde 2009 y en ese tiempo aprendí que cada estudiante necesita un camino distinto. '
                    || 'Mi método arranca por detectar exactamente qué es lo que no cierra: puede ser un tema puntual, '
                    || 'una técnica de estudio o simplemente el miedo al examen. A partir de ahí armamos un plan con '
                    || 'objetivos chicos y medibles. Trabajo con el material de la cátedra cuando existe, y cuando no, '
                    || 'preparo mis propias guías con ejercicios ordenados de menor a mayor dificultad. Entre clases '
                    || 'quedo disponible para dudas puntuales por mensaje, porque muchas veces el problema aparece '
                    || 'cuando uno se sienta solo a resolver la práctica. Si estás en una materia que ya diste y '
                    || 'volvés a recursar, mi objetivo es que esta vez entiendas el porqué de cada paso.'
      ELSE (ARRAY[
            'Doy clases desde hace más de diez años, en aula y de forma particular.',
            'Ayudo a entender la materia desde la base, sin saltear pasos y con ejercicios guiados.',
            'Clases orientadas a resolver problemas: primero la teoría justa y después mucha práctica.',
            'Trabajo con material propio y simulacros de parcial para llegar con confianza.',
            'Me enfoco en que puedas explicar lo que aprendiste, no en memorizar de memoria.',
            'Preparo ingresos, parciales y finales con un plan de estudio semana a semana.',
            'Explico con ejemplos cotidianos hasta que el concepto termina de cerrar.',
            'Acompaño todo el proceso: dudas por mensaje y seguimiento entre clases.'
           ])[1 + ((i - 1) % 8)]
        || ' ' ||
           (ARRAY[
            'La primera clase es de diagnóstico y sin cargo.',
            'Comparto apuntes y guías de ejercicios.',
            'Clases online con pizarra digital o presenciales en zona céntrica.',
            'Armo grupos reducidos de hasta tres personas.',
            'Hago preparación intensiva antes de los exámenes.',
            'Tengo flexibilidad horaria para quienes trabajan.'
           ])[1 + (((i - 1) * 3) % 6)]
    END,
    CASE WHEN i IN (53, 54, 55) THEN 'PENDING' WHEN i = 52 THEN 'REJECTED' ELSE 'VERIFIED' END,
    CASE WHEN i <= 40 THEN -31.63 + ((i % 13) - 6) * 0.01
         WHEN i <= 45 THEN -32.94 + ((i % 7) - 3) * 0.01
         WHEN i <= 48 THEN -31.25 + ((i % 5) - 2) * 0.01
         WHEN i = 49 THEN -31.73
         WHEN i = 50 THEN -31.42
         ELSE NULL END,
    CASE WHEN i <= 40 THEN -60.70 + ((i % 11) - 5) * 0.01
         WHEN i <= 45 THEN -60.63 + ((i % 7) - 3) * 0.01
         WHEN i <= 48 THEN -61.48 + ((i % 5) - 2) * 0.01
         WHEN i = 49 THEN -60.52
         WHEN i = 50 THEN -64.18
         ELSE NULL END,
    NULL,
    0, 0,
    CASE WHEN i % 4 = 0 THEN NULL
         WHEN i % 5 = 0 THEN 'Atención: doy prioridad a quienes reservan con anticipación y sostienen la continuidad semanal. Si necesitás preparar un final en poco tiempo, avisame y armamos un plan intensivo de dos semanas.'
         ELSE (ARRAY[
            'Disponible por la tarde, de lunes a viernes.',
            'Preferentemente turno mañana.',
            'Solo turno noche entre semana y sábados por la mañana.',
            'Coordino horarios por mensaje directo.',
            'Disponibilidad amplia, incluso fines de semana.'
           ])[1 + (i % 5)] END,
    CASE WHEN i IN (49, 50) THEN NULL WHEN i = 6 THEN 3000.00 WHEN i = 5 THEN 25000.00
         ELSE (3500 + (i % 10) * 1500)::numeric(10,2) END,
    CASE WHEN i <= 40 THEN 'Santa Fe'
         WHEN i <= 45 THEN 'Rosario'
         WHEN i <= 48 THEN 'Rafaela'
         WHEN i = 49 THEN 'Paraná'
         WHEN i = 50 THEN 'Córdoba'
         ELSE (ARRAY['Santa Fe', 'Santo Tomé', 'Esperanza', 'San José del Rincón'])[1 + (i % 4)] END,
    CASE WHEN i = 48 THEN NULL ELSE 'https://i.pravatar.cc/160?img=' || (1 + (i % 70)) END,
    now() - ((i % 120) || ' days')::interval,
    now() - ((i % 60) || ' days')::interval
FROM generate_series(1, 55) AS i
JOIN users u ON u.email = 'profe' || lpad(i::text, 2, '0') || '@claseya.dev'
ON CONFLICT (user_id) DO NOTHING;

-- --- Perfiles de estudiante (alumno01 .. alumno35) ----------------------------
-- Casos borde:
--   * current_year 1 (alumno01) y 12 (alumno02) -> extremos del CHECK.
--   * bio NULL en 1 de cada 4, bio muy corta y bio larga.
--   * carreras repartidas entre las tres universidades (UNL/UTN/UNR).
INSERT INTO student_profiles (user_id, university_id, career_id, current_year, bio, created_at, updated_at)
SELECT u.id, uni.id, car.id,
       CASE WHEN i = 1 THEN 1 WHEN i = 2 THEN 12 ELSE 1 + (i % 5) END,
       CASE
         WHEN i % 4 = 0 THEN NULL
         WHEN i % 7 = 0 THEN 'Estudiante.'
         WHEN i = 3 THEN 'Estoy en tercer año y me cuesta organizarme con las materias pesadas. '
                      || 'Busco a alguien que me ayude a ordenar el estudio y a preparar los parciales con tiempo, '
                      || 'sobre todo en las materias de análisis y álgebra que son las que más se me complican. '
                      || 'Prefiero clases online por la tarde porque trabajo a la mañana.'
         ELSE (ARRAY[
                'Estudiante de grado, busco apoyo para parciales.',
                'Trabajo y estudio, necesito horarios flexibles.',
                'Vengo de otra carrera y estoy recursando materias del primer año.',
                'Me preparo para rendir finales y necesito práctica intensiva.',
                'Busco clases de apoyo para no quedar libre en las materias del CBC interno.'
               ])[1 + (i % 5)]
       END,
       now() - ((i % 90) || ' days')::interval,
       now() - ((i % 45) || ' days')::interval
FROM generate_series(1, 35) AS i
JOIN users u ON u.email = 'alumno' || lpad(i::text, 2, '0') || '@claseya.dev'
JOIN LATERAL (VALUES (
    CASE WHEN i % 3 = 0 THEN 'unl-demo' WHEN i % 3 = 1 THEN 'utn-frsf-demo' ELSE 'unr-demo' END,
    CASE WHEN i % 3 = 0
              THEN (ARRAY['ingenieria-informatica-demo', 'licenciatura-sistemas-demo',
                          'contador-publico-demo', 'licenciatura-historia-demo'])[1 + (i % 4)]
         WHEN i % 3 = 1 THEN 'ingenieria-sistemas-info-demo'
         ELSE 'licenciatura-economia-demo' END
)) AS m(uni_slug, career_slug) ON true
JOIN universities uni ON uni.slug = m.uni_slug
JOIN careers car ON car.slug = m.career_slug
ON CONFLICT (user_id) DO NOTHING;

-- --- Formación académica (0, 1 o 2 filas por profesor) ------------------------
-- Casos borde: ~25% sin formación cargada, ~25% con dos títulos, end_year NULL
-- (estudios en curso). La verificación queda PENDING: la semilla no verifica nada.
WITH base AS (
    SELECT tp.id AS teacher_id, s.slot,
           2000 + pg_temp.demo_rand(tp.id::text || 'y' || s.slot, 19) AS start_year
    FROM teacher_profiles tp
    JOIN users u ON u.id = tp.user_id
    CROSS JOIN (VALUES (1), (2)) AS s(slot)
    WHERE u.email LIKE 'profe%@claseya.dev'
      AND pg_temp.demo_rand(tp.id::text || 'edu', 4) <> 0
      AND (s.slot = 1 OR pg_temp.demo_rand(tp.id::text || 'two', 4) = 0)
)
INSERT INTO teacher_education (teacher_id, institution, degree, description, start_year, end_year, created_at)
SELECT b.teacher_id,
       (ARRAY['Universidad Nacional del Litoral', 'Universidad Tecnológica Nacional',
              'Universidad Nacional de Rosario', 'Instituto Superior del Profesorado N° 8'])[1 + pg_temp.demo_rand(b.teacher_id::text || 'i' || b.slot, 4)],
       (ARRAY['Profesorado de Matemática', 'Licenciatura en Sistemas', 'Ingeniería en Informática',
              'Licenciatura en Física', 'Profesorado de Inglés', 'Especialización en Didáctica',
              'Licenciatura en Economía', 'Licenciatura en Historia'])[1 + pg_temp.demo_rand(b.teacher_id::text || 'd' || b.slot, 8)],
       CASE WHEN pg_temp.demo_rand(b.teacher_id::text || 'c' || b.slot, 3) = 0 THEN NULL
            ELSE 'Formación con práctica docente supervisada y trabajos de investigación aplicada.' END,
       b.start_year,
        CASE WHEN pg_temp.demo_rand(b.teacher_id::text || 'e' || b.slot, 5) = 0 THEN NULL
             ELSE b.start_year + 4 + pg_temp.demo_rand(b.teacher_id::text || 'e' || b.slot, 5) END,
        now() - ((b.slot * 100) || ' days')::interval
FROM base b
WHERE NOT EXISTS (SELECT 1 FROM teacher_education e WHERE e.teacher_id = b.teacher_id);

-- =============================================================================
-- 4) RELACIONES DE PROFESOR (materias, modalidades, disponibilidad)
-- =============================================================================

-- --- Materias que dicta cada profesor -----------------------------------------
-- Densidad determinística (~10% de las 47 relaciones carrera-materia por profe).
-- Se excluyen las materias "de caso borde" para poder asignarlas a mano:
--   * 'química general' y 'portugués' -> ningún profesor (0 resultados).
--   * 'estadística' y 'derecho civil' -> exactamente 1 profesor (1 resultado).
INSERT INTO teacher_subjects (teacher_id, career_subject_id, description, years_experience, active, created_at)
SELECT tp.id, cs.id,
       CASE WHEN pg_temp.demo_rand(tp.id::text || cs.id::text || 'd', 5) = 0 THEN NULL
            WHEN pg_temp.demo_rand(tp.id::text || cs.id::text || 'd', 5) = 1 THEN 'Clases de apoyo y preparación de finales.'
            ELSE 'Clases personalizadas con material propio y seguimiento.' END,
       pg_temp.demo_rand(tp.id::text || cs.id::text || 'y', 26),
       true,
       now() - ((pg_temp.demo_rand(tp.id::text || cs.id::text, 40)) || ' days')::interval
FROM teacher_profiles tp
JOIN users u ON u.id = tp.user_id
JOIN career_subjects cs ON true
JOIN subjects s ON s.id = cs.subject_id
WHERE u.email LIKE 'profe%@claseya.dev'
  AND s.normalized_name NOT IN ('química general', 'portugués', 'estadística', 'derecho civil')
  AND pg_temp.demo_rand(tp.id::text || cs.id::text, 30) < 2
ON CONFLICT (teacher_id, career_subject_id) DO NOTHING;

-- Materias de resultado único: las dicta un solo profesor.
INSERT INTO teacher_subjects (teacher_id, career_subject_id, description, years_experience, active, created_at)
SELECT tp.id, cs.id, 'Preparación específica de la materia.', 9, true, now()
FROM (VALUES ('profe01@claseya.dev', 'estadística'), ('profe02@claseya.dev', 'derecho civil')) AS v(email, subject_name)
JOIN users u ON u.email = v.email
JOIN teacher_profiles tp ON tp.user_id = u.id
JOIN career_subjects cs ON cs.subject_id = (SELECT id FROM subjects WHERE normalized_name = v.subject_name)
ON CONFLICT (teacher_id, career_subject_id) DO NOTHING;

-- --- Modalidades --------------------------------------------------------------
-- Casos borde: solo ONLINE (i%7=0), solo IN_PERSON (i%7=1) y ambas.
INSERT INTO teacher_modalities (teacher_id, modality, created_at)
SELECT tp.id, m.modality, now() - ((i % 100) || ' days')::interval
FROM generate_series(1, 55) AS i
JOIN users u ON u.email = 'profe' || lpad(i::text, 2, '0') || '@claseya.dev'
JOIN teacher_profiles tp ON tp.user_id = u.id
CROSS JOIN (VALUES ('ONLINE'), ('IN_PERSON')) AS m(modality)
WHERE (i % 7 = 0 AND m.modality = 'ONLINE')
   OR (i % 7 = 1 AND m.modality = 'IN_PERSON')
   OR (i % 7 > 1)
ON CONFLICT (teacher_id, modality) DO NOTHING;

-- --- Disponibilidad semanal ---------------------------------------------------
-- Patrones rotativos (mañana / tarde / noche+finde). A lo sumo UNA ventana por
-- día y profesor, que es lo que exige la constraint de exclusión (sin solapes).
-- Incluye una ventana DISABLED y ventanas con mode NULL (= ambas modalidades).
INSERT INTO availability_windows (teacher_id, day_of_week, start_minutes, end_minutes, mode, status, created_at, updated_at)
SELECT tp.id, d.day, d.start_min, d.end_min, d.mode, d.status, now(), now()
FROM teacher_profiles tp
JOIN users u ON u.id = tp.user_id
CROSS JOIN (VALUES
    (0, 1,  540,  720, NULL,        'AVAILABLE'),
    (0, 3,  540,  780, NULL,        'AVAILABLE'),
    (0, 5,  900, 1080, 'ONLINE',    'AVAILABLE'),
    (1, 2, 1020, 1200, NULL,        'AVAILABLE'),
    (1, 4, 1020, 1260, 'IN_PERSON', 'AVAILABLE'),
    (1, 6,  600,  780, 'ONLINE',    'AVAILABLE'),
    (2, 2, 1140, 1320, NULL,        'AVAILABLE'),
    (2, 3, 1200, 1320, NULL,        'DISABLED'),
    (2, 4, 1140, 1320, 'ONLINE',    'AVAILABLE'),
    (2, 7,  660,  900, NULL,        'AVAILABLE')
) AS d(pattern, day, start_min, end_min, mode, status)
WHERE u.email LIKE 'profe%@claseya.dev'
  AND d.pattern = pg_temp.demo_rand(tp.id::text || 'av', 3)
  AND NOT EXISTS (
      SELECT 1 FROM availability_windows w
      WHERE w.teacher_id = tp.id AND w.day_of_week = d.day
  );

-- =============================================================================
-- 5) CLASES DICTADAS Y RESEÑAS
--    El modelo exige que cada reseña cuelgue de UNA clase (reviews.booking_id
--    es NOT NULL y UNIQUE), así que las reseñas se generan sobre clases
--    COMPLETED. Las clases son datos históricos del entorno local y no se
--    exponen por ninguna API (el rumbo de producto descartó la agenda).
-- =============================================================================

WITH eligible AS (
    SELECT ts.teacher_id, ts.career_subject_id,
           row_number() OVER (ORDER BY ts.teacher_id, ts.career_subject_id) AS rn
    FROM teacher_subjects ts
    JOIN teacher_profiles tp ON tp.id = ts.teacher_id
    JOIN users tu ON tu.id = tp.user_id
    WHERE tu.email LIKE 'profe%@claseya.dev' AND tu.status = 'ACTIVE'
),
students AS (
    -- Solo alumnos generados (alumnoNN): mantiene la población estable entre
    -- corridas, porque la sección 8 agrega otras cuentas más abajo.
    SELECT sp.id AS student_id,
           row_number() OVER (ORDER BY sp.user_id) AS rn,
           count(*) OVER () AS total
    FROM student_profiles sp
    JOIN users su ON su.id = sp.user_id
    WHERE su.email LIKE 'alumno%@claseya.dev' AND su.status = 'ACTIVE'
),
-- Dos clases por relación profesor-materia, con alumnos distintos.
pairs AS (
    SELECT e.teacher_id, e.career_subject_id, s.student_id, (e.rn * 3 + s.rn) AS k
    FROM eligible e
    JOIN students s ON s.rn = 1 + (e.rn % s.total) OR s.rn = 1 + ((e.rn + 7) % s.total)
)
INSERT INTO bookings (student_id, teacher_id, career_subject_id, scheduled_at, duration_minutes,
                      mode, status, calendly_event_id, created_at, updated_at)
SELECT p.student_id, p.teacher_id, p.career_subject_id,
       now() - ((p.k % 150) || ' days')::interval - ((p.k % 8) || ' hours')::interval,
       CASE WHEN p.k % 3 = 0 THEN 90 ELSE 60 END,
       CASE WHEN p.k % 2 = 0 THEN 'ONLINE' ELSE 'IN_PERSON' END,
       'COMPLETED',
       NULL,
       now() - ((p.k % 150) || ' days')::interval,
       now() - ((p.k % 150) || ' days')::interval
FROM pairs p
WHERE NOT EXISTS (
    SELECT 1 FROM bookings b
    WHERE b.student_id = p.student_id
      AND b.teacher_id = p.teacher_id
      AND b.career_subject_id = p.career_subject_id
);

-- Una reseña por clase, con calidad sesgada por profesor (así se obtienen
-- perfiles con promedio bajo, medio y alto). ~15% de las clases quedan sin
-- reseña a propósito, para poder probar el flujo de "calificar clase".
INSERT INTO reviews (booking_id, student_id, teacher_id, rating, comment, created_at, updated_at)
SELECT b.id, b.student_id, b.teacher_id,
       CASE
         WHEN pg_temp.demo_rand(b.teacher_id::text || 'band', 10) = 0 THEN 2 + pg_temp.demo_rand(b.id::text, 2)
         WHEN pg_temp.demo_rand(b.teacher_id::text || 'band', 10) <= 2 THEN 3 + pg_temp.demo_rand(b.id::text, 2)
         ELSE 4 + pg_temp.demo_rand(b.id::text, 2)
       END,
       CASE WHEN pg_temp.demo_rand(b.id::text || 'c', 9) = 0 THEN NULL
            ELSE (ARRAY[
                'Muy clara explicando, se nota que prepara las clases.',
                'Excelente predisposición, respondió todas mis dudas.',
                'Me ayudó a ordenar el estudio y aprobé el parcial.',
                'Buenas clases, aunque a veces íbamos un poco rápido.',
                'Muy paciente y con mucho material de práctica.',
                'Cambió un horario a último momento, pero después todo bien.',
                'Esperaba otra cosa, no terminó de conectar con mi ritmo de estudio.',
                'Genial, volvería a tomar clases el próximo cuatrimestre.',
                'Explica con ejemplos claros y hace seguimiento entre clases.',
                'Muy recomendable para preparar finales.'
            ])[1 + pg_temp.demo_rand(b.id::text || 't', 10)] END,
       b.scheduled_at + interval '2 days',
       b.scheduled_at + interval '2 days'
FROM bookings b
WHERE NOT EXISTS (SELECT 1 FROM reviews r WHERE r.booking_id = b.id)
  AND pg_temp.demo_rand(b.id::text || 'skip', 7) <> 0;

-- Los agregados públicos (los que muestra la card) se derivan de las reseñas,
-- pero SOLO para los profesores generados: los 4 profesores previos conservan
-- los valores que ya tenían cargados.
UPDATE teacher_profiles tp
SET rating_average = sub.avg_rating,
    rating_count   = sub.cnt,
    updated_at     = now()
FROM (
    SELECT r.teacher_id,
           round(avg(r.rating), 2)::numeric(3, 2) AS avg_rating,
           count(*)::int AS cnt
    FROM reviews r
    GROUP BY r.teacher_id
) sub
WHERE tp.id = sub.teacher_id
  AND tp.user_id IN (SELECT u.id FROM users u WHERE u.email LIKE 'profe%@claseya.dev');

-- =============================================================================
-- 6) FAVORITOS
--    Densidad determinística sobre estudiantes activos x profesores activos.
--    Casos borde: alumno01 con muchos favoritos (12, explícitos) y alumno02 con
--    cero favoritos (excluido de la generación).
-- =============================================================================
INSERT INTO favorites (student_id, teacher_id, created_at)
SELECT s.id, t.id,
       now() - ((pg_temp.demo_rand(s.id::text || t.id::text || 'fav', 60)) || ' days')::interval
FROM student_profiles s
JOIN users su ON su.id = s.user_id
JOIN teacher_profiles t ON true
JOIN users tu ON tu.id = t.user_id
WHERE su.email LIKE 'alumno%@claseya.dev' AND su.status = 'ACTIVE'
  AND su.email <> 'alumno02@claseya.dev'
  AND tu.email LIKE 'profe%@claseya.dev' AND tu.status = 'ACTIVE'
  AND pg_temp.demo_rand(s.id::text || t.id::text, 40) < 3
ON CONFLICT (student_id, teacher_id) DO NOTHING;

-- alumno01: estudiante "power user" con una lista de favoritos larga.
INSERT INTO favorites (student_id, teacher_id, created_at)
SELECT s.id, t.id, now() - (i || ' days')::interval
FROM users su
JOIN student_profiles s ON s.user_id = su.id
CROSS JOIN generate_series(1, 12) AS i
JOIN users tu ON tu.email = 'profe' || lpad(i::text, 2, '0') || '@claseya.dev'
JOIN teacher_profiles t ON t.user_id = tu.id
WHERE su.email = 'alumno01@claseya.dev'
ON CONFLICT (student_id, teacher_id) DO NOTHING;

-- =============================================================================
-- 7) MENSAJERÍA
--    El id de cada conversación es determinístico (md5 de los dos participantes),
--    así el seed es idempotente sin necesitar una tabla auxiliar.
--    Casos borde: conversaciones sin mensajes, con 1 mensaje, con muchos, y
--    mensajes leídos / no leídos (read_at NULL).
-- =============================================================================

WITH pair AS (
    SELECT s.user_id AS student_user,
           t.user_id AS teacher_user,
           md5('claseya-demo-conv:' || s.user_id::text || ':' || t.user_id::text)::uuid AS conv_id,
           row_number() OVER (ORDER BY s.user_id, t.user_id) AS rn
    FROM student_profiles s
    JOIN users su ON su.id = s.user_id
    JOIN teacher_profiles t ON true
    JOIN users tu ON tu.id = t.user_id
    WHERE su.email LIKE 'alumno%@claseya.dev' AND su.status = 'ACTIVE'
      AND tu.email LIKE 'profe%@claseya.dev' AND tu.status = 'ACTIVE'
      AND pg_temp.demo_rand(s.id::text || t.id::text || 'conv', 97) < 2
)
INSERT INTO conversations (id, created_at, updated_at)
SELECT p.conv_id,
       now() - ((p.rn % 45) || ' days')::interval,
       now() - ((p.rn % 45) || ' days')::interval
FROM pair p
ON CONFLICT (id) DO NOTHING;

WITH pair AS (
    SELECT s.user_id AS student_user,
           t.user_id AS teacher_user,
           md5('claseya-demo-conv:' || s.user_id::text || ':' || t.user_id::text)::uuid AS conv_id
    FROM student_profiles s
    JOIN users su ON su.id = s.user_id
    JOIN teacher_profiles t ON true
    JOIN users tu ON tu.id = t.user_id
    WHERE su.email LIKE 'alumno%@claseya.dev' AND su.status = 'ACTIVE'
      AND tu.email LIKE 'profe%@claseya.dev' AND tu.status = 'ACTIVE'
      AND pg_temp.demo_rand(s.id::text || t.id::text || 'conv', 97) < 2
)
INSERT INTO conversation_participants (conversation_id, user_id, created_at)
SELECT p.conv_id, x.uid, c.created_at
FROM pair p
CROSS JOIN LATERAL (VALUES (p.student_user), (p.teacher_user)) AS x(uid)
JOIN conversations c ON c.id = p.conv_id
ON CONFLICT (conversation_id, user_id) DO NOTHING;

WITH pair AS (
    SELECT s.user_id AS student_user,
           t.user_id AS teacher_user,
           md5('claseya-demo-conv:' || s.user_id::text || ':' || t.user_id::text)::uuid AS conv_id,
           row_number() OVER (ORDER BY s.user_id, t.user_id) AS rn
    FROM student_profiles s
    JOIN users su ON su.id = s.user_id
    JOIN teacher_profiles t ON true
    JOIN users tu ON tu.id = t.user_id
    WHERE su.email LIKE 'alumno%@claseya.dev' AND su.status = 'ACTIVE'
      AND tu.email LIKE 'profe%@claseya.dev' AND tu.status = 'ACTIVE'
      AND pg_temp.demo_rand(s.id::text || t.id::text || 'conv', 97) < 2
),
msgs AS (
    -- 0 a 12 mensajes por conversación: las que quedan en 0 son "conversaciones
    -- vacías" (caso borde real de la UI).
    SELECT p.conv_id, p.student_user, p.teacher_user, p.rn, g.n,
           (p.rn * 37 + g.n * 11) AS k
    FROM pair p
    CROSS JOIN LATERAL generate_series(0, pg_temp.demo_rand(p.conv_id::text || 'qty', 13) - 1) AS g(n)
)
INSERT INTO messages (conversation_id, sender_id, content, created_at, read_at)
SELECT m.conv_id,
       CASE WHEN m.n % 2 = 0 THEN m.student_user ELSE m.teacher_user END,
       (ARRAY[
           'Hola, vi tu perfil y quería consultar por clases de apoyo.',
           'Hola! Sí, tengo disponibilidad. ¿Qué tema necesitás repasar?',
           'Estoy con la unidad 3, no me cierran los ejercicios de la guía.',
           'Perfecto, traé la guía a la primera clase y los vemos juntos.',
           '¿Podés los martes por la tarde?',
           'Los martes a las 18 me sirve. ¿Online o presencial?',
           'Preferiría online, me queda más cómodo después del trabajo.',
           'Listo, te mando el enlace antes de la clase.',
           '¿Tenés el material de la cátedra para que lo mire antes?',
           'Sí, te lo comparto por acá y lo repasamos en clase.',
           'Gracias! Ayer me quedó mucho más claro el tema.',
           'Buenísimo, la semana que viene seguimos con integrales.',
           '¿Podemos mover la clase del jueves? Me surgió algo.',
           'Sin problema, la pasamos al viernes a la misma hora.'
       ])[1 + (m.k % 14)],
       c.created_at + ((m.n * 47 + 5) || ' minutes')::interval,
       CASE WHEN pg_temp.demo_rand(m.conv_id::text || m.n::text || 'read', 3) = 0 THEN NULL
            ELSE c.created_at + ((m.n * 47 + 90) || ' minutes')::interval END
FROM msgs m
JOIN conversations c ON c.id = m.conv_id
WHERE NOT EXISTS (SELECT 1 FROM messages x WHERE x.conversation_id = m.conv_id);

-- La conversación "se mueve" con su último mensaje (así la lista queda ordenada).
UPDATE conversations c
SET updated_at = m.last_at
FROM (SELECT conversation_id, max(created_at) AS last_at FROM messages GROUP BY conversation_id) m
WHERE c.id = m.conversation_id AND c.updated_at < m.last_at;

-- (La conversación deliberadamente vacía se crea en la sección 8: depende de las
--  cuentas demo históricas, que se insertan ahí.)

-- =============================================================================
-- 8) CUENTAS DEMO HISTÓRICAS
--    Las que ya existían antes de este archivo (y que la documentación sigue
--    mencionando). Se incluyen para que el seed sea autosuficiente en una base
--    nueva; en bases ya pobladas, ON CONFLICT / NOT EXISTS las deja intactas
--    (incluidos sus ratings, que por eso van explícitos y no se recalculan).
--
--    IMPORTANTE: esta sección va al FINAL. Ninguna sección anterior debe depender
--    de estas cuentas (por eso la conversación vacía se crea acá abajo).
-- =============================================================================
INSERT INTO users (email, name, password_hash, role, status, created_at, updated_at)
VALUES
    ('ana.profe@claseya.dev',   'Ana Gómez',         crypt('Password123', gen_salt('bf')), 'TEACHER', 'ACTIVE', now(), now()),
    ('bruno.profe@claseya.dev', 'Bruno Fernández',   crypt('Password123', gen_salt('bf')), 'TEACHER', 'ACTIVE', now(), now()),
    ('carla.profe@claseya.dev', 'Carla Ríos',        crypt('Password123', gen_salt('bf')), 'TEACHER', 'ACTIVE', now(), now()),
    ('diego.profe@claseya.dev', 'Diego Sosa',        crypt('Password123', gen_salt('bf')), 'TEACHER', 'ACTIVE', now(), now()),
    ('student@claseya.dev',     'Sofía Estudiante',  crypt('Password123', gen_salt('bf')), 'STUDENT', 'ACTIVE', now(), now())
ON CONFLICT (email) DO NOTHING;

INSERT INTO teacher_profiles (user_id, bio, verification_status, price_per_hour, city, photo_url,
                              availability_note, rating_average, rating_count, created_at, updated_at)
SELECT u.id, v.bio, 'VERIFIED', v.price, v.city, v.photo, v.note, 4.70, 12, now(), now()
FROM (VALUES
    ('ana.profe@claseya.dev',
     'Profesora de matemática y álgebra. Clases claras, con ejercicios y seguimiento.',
     8000.00, 'Santa Fe', 'https://i.pravatar.cc/160?img=47',
     'Disponible lunes, miércoles y viernes por la tarde.'),
    ('bruno.profe@claseya.dev',
     'Físico. Preparo parciales y finales con enfoque en resolución de problemas.',
     9500.00, 'Santo Tomé', 'https://i.pravatar.cc/160?img=12',
     'Disponible martes y jueves por la mañana.'),
    ('carla.profe@claseya.dev',
     'Ingeniera en sistemas. Enseño programación desde cero con proyectos reales.',
     11000.00, 'Santa Fe', 'https://i.pravatar.cc/160?img=32',
     'Disponible sábados por la mañana.'),
    ('diego.profe@claseya.dev',
     'Profesor de inglés con experiencia en conversación y exámenes internacionales.',
     7000.00, 'Santa Fe', 'https://i.pravatar.cc/160?img=68',
     'Disponible lunes a jueves por la noche.')
) AS v(email, bio, price, city, photo, note)
JOIN users u ON u.email = v.email
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO teacher_subjects (teacher_id, career_subject_id, description, years_experience, active, created_at)
SELECT tp.id, cs.id, 'Clases personalizadas', v.years, true, now()
FROM (VALUES
    ('ana.profe@claseya.dev',   'ingenieria-informatica-demo', 'matemática i',   6),
    ('ana.profe@claseya.dev',   'ingenieria-informatica-demo', 'álgebra',        6),
    ('bruno.profe@claseya.dev', 'licenciatura-sistemas-demo',  'física i',       4),
    ('carla.profe@claseya.dev', 'ingenieria-informatica-demo', 'programación i', 7),
    ('diego.profe@claseya.dev', 'licenciatura-sistemas-demo',  'inglés',         8)
) AS v(email, career_slug, subject_name, years)
JOIN users u ON u.email = v.email
JOIN teacher_profiles tp ON tp.user_id = u.id
JOIN careers c ON c.slug = v.career_slug
JOIN subjects s ON s.normalized_name = v.subject_name
JOIN career_subjects cs ON cs.career_id = c.id AND cs.subject_id = s.id
ON CONFLICT (teacher_id, career_subject_id) DO NOTHING;

INSERT INTO teacher_modalities (teacher_id, modality, created_at)
SELECT tp.id, m.modality, now()
FROM (VALUES
    ('ana.profe@claseya.dev'), ('bruno.profe@claseya.dev'),
    ('carla.profe@claseya.dev'), ('diego.profe@claseya.dev')
) AS v(email)
JOIN users u ON u.email = v.email
JOIN teacher_profiles tp ON tp.user_id = u.id
CROSS JOIN (VALUES ('ONLINE'), ('IN_PERSON')) AS m(modality)
ON CONFLICT (teacher_id, modality) DO NOTHING;

INSERT INTO availability_windows (teacher_id, day_of_week, start_minutes, end_minutes, mode, status, created_at, updated_at)
SELECT tp.id, v.day, v.start_min, v.end_min, v.mode, 'AVAILABLE', now(), now()
FROM (VALUES
    ('ana.profe@claseya.dev',   1, 1020, 1200, NULL::varchar),
    ('ana.profe@claseya.dev',   3, 1020, 1200, NULL::varchar),
    ('ana.profe@claseya.dev',   5,  540,  720, NULL::varchar),
    ('bruno.profe@claseya.dev', 2,  540,  720, NULL::varchar),
    ('bruno.profe@claseya.dev', 4,  540,  720, NULL::varchar),
    ('carla.profe@claseya.dev', 6,  540,  780, 'ONLINE'::varchar),
    ('diego.profe@claseya.dev', 1, 1140, 1320, NULL::varchar),
    ('diego.profe@claseya.dev', 2, 1140, 1320, NULL::varchar),
    ('diego.profe@claseya.dev', 3, 1140, 1320, NULL::varchar),
    ('diego.profe@claseya.dev', 4, 1140, 1320, NULL::varchar)
) AS v(email, day, start_min, end_min, mode)
JOIN users u ON u.email = v.email
JOIN teacher_profiles tp ON tp.user_id = u.id
WHERE NOT EXISTS (
    SELECT 1 FROM availability_windows w
    WHERE w.teacher_id = tp.id AND w.day_of_week = v.day
);

INSERT INTO student_profiles (user_id, university_id, career_id, current_year, bio, created_at, updated_at)
SELECT u.id, uni.id, c.id, 2, 'Estudiante demo', now(), now()
FROM users u
JOIN universities uni ON uni.slug = 'unl-demo'
JOIN careers c ON c.slug = 'ingenieria-informatica-demo'
WHERE u.email = 'student@claseya.dev'
ON CONFLICT (user_id) DO NOTHING;

-- Conversación deliberadamente VACÍA (id fijo): garantiza el caso borde de
-- "mensajería sin mensajes" en cualquier base, sin depender del hash. Se crea acá
-- porque necesita las cuentas demo recién insertadas.
INSERT INTO conversations (id, created_at, updated_at)
VALUES (md5('claseya-demo-conv:empty')::uuid, now() - interval '6 days', now() - interval '6 days')
ON CONFLICT (id) DO NOTHING;

INSERT INTO conversation_participants (conversation_id, user_id, created_at)
SELECT c.id, u.id, c.created_at
FROM conversations c
JOIN users u ON u.email IN ('student@claseya.dev', 'ana.profe@claseya.dev')
WHERE c.id = md5('claseya-demo-conv:empty')::uuid
ON CONFLICT (conversation_id, user_id) DO NOTHING;

-- =============================================================================
-- 8b) VERIFICACION ACADEMICA (TEACHER-001) — dataset consistente
-- =============================================================================
-- El estado del perfil debe salir de la tabla de RF-14 (I12). Los profesores demo nombrados quedan
-- verificados de verdad: se les asegura una credencial, se marca VERIFIED, se registra la decisión
-- de auditoría y, al final, se recalcula cada perfil desde sus credenciales.

-- Al menos una credencial por profesor nombrado (los que no tuvieron la formación aleatoria).
INSERT INTO teacher_education (teacher_id, institution, degree, description, start_year, end_year, created_at)
SELECT tp.id, 'Universidad Nacional del Litoral', 'Profesorado de Matemática', 'Formación de grado.', 2010, 2015, now()
FROM (VALUES ('ana.profe@claseya.dev'), ('bruno.profe@claseya.dev'),
             ('carla.profe@claseya.dev'), ('diego.profe@claseya.dev')) AS v(email)
JOIN users u ON u.email = v.email
JOIN teacher_profiles tp ON tp.user_id = u.id
WHERE NOT EXISTS (SELECT 1 FROM teacher_education e WHERE e.teacher_id = tp.id);

-- Todas sus credenciales quedan verificadas.
UPDATE teacher_education e
SET verification_status = 'VERIFIED', submitted_at = COALESCE(e.submitted_at, now())
FROM teacher_profiles tp
JOIN users u ON u.id = tp.user_id
WHERE e.teacher_id = tp.id
  AND u.email IN ('ana.profe@claseya.dev', 'bruno.profe@claseya.dev',
                  'carla.profe@claseya.dev', 'diego.profe@claseya.dev');

-- Auditoría: una decisión VERIFIED por credencial verificada (idempotente).
INSERT INTO verification_decisions (teacher_id, teacher_education_id, admin_user_id,
                                    previous_status, new_status, decision, method, reason, decided_at)
SELECT e.teacher_id, e.id, a.id, 'UNDER_REVIEW', 'VERIFIED', 'VERIFIED', 'INSTITUTION_CHECK',
       'Verificación del dataset de demostración.', now()
FROM teacher_education e
JOIN users a ON a.email = 'admin@claseya.dev'
WHERE e.verification_status = 'VERIFIED'
  AND NOT EXISTS (
      SELECT 1 FROM verification_decisions d
      WHERE d.teacher_education_id = e.id AND d.decision = 'VERIFIED'
  );

-- Recalcula el estado de cada perfil desde sus credenciales (precedencia de RF-14), para que el
-- demo nunca contradiga a sus credenciales: perfil VERIFIED <=> al menos una credencial VERIFIED.
UPDATE teacher_profiles tp
SET verification_status = (
    SELECT CASE
        WHEN bool_or(e.verification_status = 'VERIFIED') THEN 'VERIFIED'
        WHEN bool_or(e.verification_status = 'UNDER_REVIEW') THEN 'UNDER_REVIEW'
        WHEN bool_or(e.verification_status = 'MORE_INFO_REQUIRED') THEN 'MORE_INFO_REQUIRED'
        WHEN bool_or(e.verification_status = 'REJECTED') THEN 'REJECTED'
        ELSE 'PENDING'
    END
    FROM teacher_education e
    WHERE e.teacher_id = tp.id
);

-- 9) RESUMEN
-- =============================================================================
SELECT 'users' AS entidad, count(*) AS total FROM users
UNION ALL SELECT 'student_profiles', count(*) FROM student_profiles
UNION ALL SELECT 'teacher_profiles', count(*) FROM teacher_profiles
UNION ALL SELECT 'universities', count(*) FROM universities
UNION ALL SELECT 'academic_units', count(*) FROM academic_units
UNION ALL SELECT 'careers', count(*) FROM careers
UNION ALL SELECT 'subjects', count(*) FROM subjects
UNION ALL SELECT 'career_subjects', count(*) FROM career_subjects
UNION ALL SELECT 'teacher_subjects', count(*) FROM teacher_subjects
UNION ALL SELECT 'teacher_modalities', count(*) FROM teacher_modalities
UNION ALL SELECT 'teacher_education', count(*) FROM teacher_education
UNION ALL SELECT 'availability_windows', count(*) FROM availability_windows
UNION ALL SELECT 'bookings', count(*) FROM bookings
UNION ALL SELECT 'reviews', count(*) FROM reviews
UNION ALL SELECT 'favorites', count(*) FROM favorites
UNION ALL SELECT 'conversations', count(*) FROM conversations
UNION ALL SELECT 'conversation_participants', count(*) FROM conversation_participants
UNION ALL SELECT 'messages', count(*) FROM messages
UNION ALL SELECT 'verification_decisions', count(*) FROM verification_decisions
ORDER BY entidad;
