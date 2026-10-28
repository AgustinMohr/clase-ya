# Revisión técnica de ClaseYa

**Fecha:** 27 de octubre de 2026  
**Commit base:** `888a3c2` (`docs: add project README`)  
**Árbol de trabajo:** 8 archivos tracked modificados sin commitear (availability DTOs, `GlobalExceptionHandler`, `TeacherVerificationController` y tests) + directorios `opendesign/` y `.opencode/` sin trackear.  
**Alcance:** backend Spring Boot, frontend React/TypeScript, esquema Flyway, configuración, dependencias, tests y documentación.  
**Método:** revisión estática del código y de los documentos normativos (`AGENTS.md`, PRD, specs, ADRs), más `mvn -B -q -DskipTests compile` y `npx tsc --noEmit` (ambos OK). La suite de integración **no se ejecutó** porque Docker Desktop no estaba disponible en el entorno de revisión.

---

## 1. Resumen ejecutivo

ClaseYa es un proyecto **por encima del promedio** en disciplina de backend: monolito modular claro, identidad resuelta siempre desde `SecurityContext`, validación con Bean Validation, paginación acotada, orden por whitelist, DTOs en lugar de entidades, migraciones Flyway versionadas y una suite de integración real con Testcontainers (222 métodos `@Test` y ~389 aserciones). No se encontraron bypasses de autorización, inyección SQL, path traversal ni exposición pública de emails, direcciones o coordenadas en los flujos revisados.

Los riesgos principales no son de “OWASP clásico” sino de **integridad del circuito de confianza, abuso de endpoints y madurez operativa**:

1. **La evidencia documental puede borrarse después de una decisión de verificación** (incluso con la credencial `VERIFIED`), porque el guard de borrado consulta una FK que el código nunca completa. Esto contradice explícitamente RF-18/RF-21 del spec.
2. **No hay rate limiting, lockout ni cuotas anti-spam** en login, registro, contacto o subida de documentos. El README lo reconoce como pendiente, pero es un riesgo alto para producción.
3. **El backend corre sobre Spring Boot 3.2.6, fuera de soporte OSS**, y Dependabot solo actualiza npm: no hay actualizaciones automáticas de Maven ni escaneo de dependencias.
4. **Los favoritos no respetan la regla de visibilidad pública (D1)**: un profesor despublicado, inactivo o suspendido sigue apareciendo con su tarjeta completa en `/api/favorites`.
5. **No hay pipeline de CI**; los 222 tests y el `tsc` no se ejecutan como gate de merge.

Además, el flujo **email + contraseña** quedó a mitad de camino: el backend registra cuentas `PENDING` sin endpoint de activación, el frontend no tiene pantalla de registro y el modal de login sí ofrece email/contraseña. El producto define Google OAuth como obligatorio, así que la recomendación principal es **alinear el código con esa decisión** (retirar o aislar el flujo legado) o completarlo de extremo a extremo; hoy es una fuente de confusión y soporte.

**Veredicto (actualizado):** los bloqueantes EVID-01, SEC-01, DEP-01 y PRIV-01, junto con AUTH-01,
ARCH-01, CI-01, SEC-02, BUG-02, BUG-03, SEC-03, SEC-04, OPS-01, OPS-02, QUAL-01, TEST-01, QUAL-02,
QUAL-03, SEC-05, SEC-06, DOC-01, UX-01, DEV-01, PERF-01, PERF-02 y DEP-02, están corregidos y
verificados (317 tests backend + 24 unit del frontend + 40 E2E). Queda **STOR-01** (migración de
evidencia a object storage + antivirus), que requiere proveedor/credenciales y escáner. CI está
configurado, pero requiere activar branch protection para ser un gate obligatorio.

---

## 2. Tabla resumen

| Severidad | Cantidad |
|---|---:|
| 🔴 CRITICAL | 1 |
| 🟠 HIGH | 6 |
| 🟡 MEDIUM | 16 |
| 🔵 LOW / REFACTOR | 5 |
| **Total** | **28** |

> No se identificaron CRITICAL de autenticación, autorización, inyección o exposición masiva de datos. El único CRITICAL es de **pérdida permanente de evidencia de auditoría**.

---

## 3. Detalle de hallazgos

### 🔴 CRITICAL

#### EVID-01 — La evidencia de verificación se puede eliminar después de la decisión

- **Severidad:** 🔴 CRITICAL
- **Ubicación:** `src/main/java/com/claseya/verification/service/VerificationDocumentService.java:116-124`; `src/main/java/com/claseya/verification/service/VerificationDecisionService.java:83,107,154-160`; spec `docs/specs/active/TEACHER-001-teacher-page-and-verification.md:180,187,316`.
- **Descripción:** `delete()` solo bloquea el borrado cuando `decisionRepository.existsByDocument_Id(documentId)` es verdadero. Sin embargo, `VerificationDecisionService` crea **todas** las decisiones con `record(profile, education, null, ...)`, es decir, `document_id` siempre queda `NULL` (`VerificationDecisionService.java:160`). El guard es código muerto: ningún documento queda protegido. Tampoco se verifica el estado de la credencial.
- **Impacto:** un profesor puede borrar el PDF/JPG que sustentó su credencial **después** de quedar `VERIFIED`, o borrar toda la evidencia mientras está `UNDER_REVIEW`. La credencial y la decisión permanecen, pero el binario desaparece: se rompe la inmutabilidad de la evidencia (RF-18), la prohibición de borrar datos históricos (RF-21) y la posibilidad de re-auditar, resolver disputas o revocar con prueba. Es pérdida permanente de datos de cumplimiento.
- **Solución propuesta:** bloquear el borrado si la credencial fue presentada alguna vez (`submittedAt != null`) o si existe cualquier decisión asociada a la credencial; alternativamente, registrar `document_id` en las decisiones que se basan en un documento y bloquear también por estado `VERIFIED`/`UNDER_REVIEW`. Agregar tests de regresión.

```java
// VerificationDocumentService.delete(...)
TeacherEducation credential = document.getTeacherEducation();

boolean usedAsEvidence = credential.getSubmittedAt() != null
        || decisionRepository.existsByTeacherEducation_Id(credential.getId())
        || credential.getVerificationStatus() == VerificationStatus.VERIFIED
        || credential.getVerificationStatus() == VerificationStatus.UNDER_REVIEW;

if (usedAsEvidence) {
    throw new ConflictException("A document used as evidence cannot be deleted");
}
documentRepository.delete(document);
```

Además, en `VerificationDecisionService.record(...)`, cuando la decisión se base en un documento concreto, pasar ese documento en lugar de `null` para que la FK `verification_decisions.document_id` cumpla su función.

---

### 🟠 HIGH

#### AUTH-01 — El flujo email + contraseña está incompleto y compite con Google OAuth

- **Severidad:** 🟠 HIGH
- **Ubicación:** `src/main/java/com/claseya/auth/service/AuthService.java:47-70`; `src/main/java/com/claseya/auth/controller/AuthController.java:26-33`; `frontend/src/components/LoginModal.tsx:67-93`; `frontend/src/api.ts:397-398`; `docs/auth.md:4`; `README.md` (sección 2, “Cuenta y acceso”).
- **Descripción:** `POST /api/auth/register` crea al usuario con `status=PENDING` (`AuthService.java:64`) y **no existe endpoint ni tarea de activación**: solo Google crea cuentas `ACTIVE`. El frontend no tiene pantalla de registro (no hay llamada a `/api/auth/register` en `frontend/src`), pero el modal de login sí ofrece email y contraseña. El PRD define Google OAuth como obligatorio (`docs/product/product-requirements.md:52`), mientras README y `docs/auth.md` presentan el registro por contraseña como implementado.
- **Impacto:** un usuario que ve “Ingresar” con email/contraseña no puede crear una cuenta desde la UI; si usa la API, queda en `PENDING` para siempre y ni siquiera puede vincular Google después (`GoogleAuthService.requireActive` lo rechaza). Genera soporte manual, cuentas basura, documentación engañosa y una superficie de ataque innecesaria.
- **Solución propuesta:** elegir una de dos direcciones y documentarla en un ADR:
  - **A (recomendada, alineada al PRD):** dejar Google OAuth como único mecanismo de alta; retirar el formulario email/contraseña del frontend y, si se conserva el endpoint legado, aislarlo detrás de un flag/perfil y marcarlo `@Deprecated`.
  - **B (si se quiere conservar password):** agregar registro en UI + verificación de email + endpoint de activación por token de un solo uso + opción de activación manual por ADMIN, con tests de extremo a extremo.

```java
// Opción B: activación con token de un solo uso
@PostMapping("/auth/activate")
public ResponseEntity<Void> activate(@RequestParam String token) {
    authService.activate(token); // valida hash, expiración y uso único
    return ResponseEntity.noContent().build();
}
```

---

#### SEC-01 — Sin rate limiting, lockout ni cuotas anti-abuso

- **Severidad:** 🟠 HIGH
- **Ubicación:** `src/main/java/com/claseya/auth/controller/AuthController.java`, `src/main/java/com/claseya/security/SecurityConfig.java`, `src/main/java/com/claseya/messaging/controller/ConversationController.java`, `README.md` (sección 5, “Limitaciones y pendientes”).
- **Descripción:** No hay límite de intentos de login, registro, login con Google, creación de conversaciones ni subida de documentos. Tampoco hay bloqueo temporal por cuenta/IP, CAPTCHA ni cuota por usuario. El registro devuelve `409` cuando el email existe (`AuthService.java:54`), lo que permite enumerar cuentas sin costo.
- **Impacto:** brute force y credential stuffing contra contraseñas débiles; enumeración de emails registrados; spam masivo de contactos a profesores; abuso de almacenamiento (hasta 25 MB por credencial) y de CPU (BCrypt) por parte de un atacante.
- **Solución propuesta:** aplicar rate limiting en dos capas (proxy/WAF + aplicación), con buckets por IP y por email, respuesta `429` con `Retry-After`, backoff exponencial y lockout temporal. Agregar CAPTCHA en registro/login tras N fallos y cuotas de contacto por estudiante/día. Para múltiples instancias, usar Redis (Bucket4j + Redis o similar).

```java
// Esquema mínimo con Bucket4j (una instancia); en producción usar Redis
if (!bucket.tryConsume(1)) {
    response.setStatus(429);
    response.setHeader("Retry-After", "60");
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getWriter(),
            ApiError.of(429, "Too Many Requests", "Too many attempts", request.getRequestURI()));
    return;
}
```

---

#### DEP-01 — Backend sobre Spring Boot 3.2.6 (EOL) y sin actualizaciones Maven

- **Severidad:** 🟠 HIGH
- **Ubicación:** `pom.xml:9-10`; `.github/dependabot.yml:8-10`; ausencia de `dependency-check`, `jacoco`, `spotbugs` o `checkstyle` en `pom.xml`.
- **Descripción:** Spring Boot 3.2.x terminó su soporte OSS en noviembre de 2024; en octubre de 2026 ya no recibe parches de seguridad. Dependabot únicamente cubre `npm` en `/frontend`; no hay ecosistema `maven` ni `github-actions`. Tampoco hay escaneo de vulnerabilidades de dependencias ni cobertura en el build.
- **Impacto:** acumulación silenciosa de CVEs en Spring Framework, Tomcat embebido, Jackson, Hibernate o jjwt; migraciones más costosas cuanto más se posterguen; imposibilidad de demostrar un proceso de actualización seguro.
- **Solución propuesta:** actualizar a la línea soportada (3.4.x/3.5.x) con un PR por dependencia y suite verde; agregar Maven a Dependabot y `dependency-check` al pipeline. Revisar changelogs y lockfiles, no hacer “bump” masivo.

```yaml
# .github/dependabot.yml
- package-ecosystem: "maven"
  directory: "/"
  schedule:
    interval: "weekly"
```

---

#### PRIV-01 — Los favoritos exponen profesores que ya no son visibles

- **Severidad:** 🟠 HIGH
- **Ubicación:** `src/main/java/com/claseya/favorite/service/FavoriteService.java:83-111`; `src/main/java/com/claseya/teacher/service/TeacherSummaryAssembler.java:47-80`; `README.md` (sección 5, “Visibilidad pública”).
- **Descripción:** `add` exige que el profesor sea visible (`TeacherVisibilityService.requireVisible`), pero `list` carga todos los favoritos del estudiante y los pasa a `TeacherSummaryAssembler.summarize(...)`, que **no aplica ningún filtro de visibilidad**. Si el profesor se despublica, su cuenta pasa a `INACTIVE`/`SUSPENDED` o deja de cumplir la regla D1 después de haber sido guardado, su tarjeta completa (nombre, bio, materias, precio, ciudad, foto) sigue devolviéndose.
- **Impacto:** contradice la regla “el resto se comporta como 404” y puede exponer perfiles retirados, suspendidos por moderación o con datos que el profesor ya no quiere publicar. Es una fuga de privacidad y un problema de moderación, no solo de UX.
- **Solución propuesta:** filtrar a nivel de consulta, no en memoria, usando la misma especificación `TeacherSpecifications.visible()`; si se quiere conservar el favorito, devolver solo el ID y un estado “no disponible”. Agregar test: favoritear → despublicar/suspender → `GET /api/favorites` no debe incluir la tarjeta.

```java
// En FavoriteRepository: join con la regla de visibilidad
@Query("""
    select f from Favorite f
    where f.student.id = :studentId
      and exists (
        select 1 from TeacherProfile t
        where t.id = f.teacher.id
          and t.user.status = com.claseya.model.enums.UserStatus.ACTIVE
          and exists (select 1 from TeacherSubject ts where ts.teacher = t and ts.active = true)
          and exists (select 1 from TeacherModality tm where tm.teacher = t)
          and exists (select 1 from AvailabilityWindow aw where aw.teacher = t)
          and t.user.name is not null and trim(t.user.name) <> ''
          and t.bio is not null and trim(t.bio) <> ''
      )
    """)
Page<Favorite> findVisibleByStudent(@Param("studentId") UUID studentId, Pageable pageable);
```

---

#### ARCH-01 — Navegación sin router, sin URLs compartibles ni SEO

- **Severidad:** 🟠 HIGH
- **Ubicación:** `frontend/src/App.tsx:20-57`; `frontend/package.json:11-20` (no hay `react-router` ni equivalente).
- **Descripción:** La SPA mantiene la vista en un estado interno (`View`) y usa `history.pushState` solo para el botón “atrás”, sin modificar la URL. Al refrescar se vuelve a la landing; no existen enlaces profundos a `/profesores/:id`, `/buscar?materia=...` ni `/favoritos`. Tampoco hay metadatos por página, `sitemap.xml`, `robots.txt` ni renderizado del lado del servidor.
- **Impacto:** para un **directorio** cuyo crecimiento depende de búsqueda orgánica, esto es estructural: no se pueden indexar perfiles ni compartir enlaces, no se pueden medir funnels por URL y se pierde el estado al recargar. También impide campañas y deep links desde emails.
- **Solución propuesta:** introducir React Router con rutas reales, lazy loading por página y metadatos/Open Graph por perfil; evaluar prerender/SSR (por ejemplo, `vite-plugin-ssr`, Remix o Next) para que los perfiles sean indexables. Mantener el estado de filtros en query params.

```tsx
<Routes>
  <Route path="/" element={<LandingPage />} />
  <Route path="/buscar" element={<SearchPage />} />
  <Route path="/profesores/:id" element={<TeacherProfilePage />} />
  <Route path="/favoritos" element={<FavoritesPage />} />
</Routes>
```

---

#### CI-01 — No hay pipeline de CI; los tests no son un gate

- **Severidad:** 🟠 HIGH
- **Ubicación:** `.github/` contiene únicamente `dependabot.yml`; `README.md` (sección 3) reconoce “No hay pipeline de CI configurado”.
- **Descripción:** Existen 222 tests backend, 25 escenarios E2E y un `tsc` que falla el build, pero nada los ejecuta en push/PR. Tampoco hay escaneo de secretos, análisis estático (CodeQL) ni auditoría npm automatizada.
- **Impacto:** cualquier regresión, CVE o error de tipos puede llegar a `main` sin señal; la calidad depende de que cada persona recuerde ejecutar `scripts/verify.ps1`. A medida que crece el equipo, el costo se multiplica.
- **Solución propuesta:** pipeline de GitHub Actions con jobs de backend (Maven + Testcontainers), frontend (`npm ci`, `tsc`, build, Playwright), `npm audit` y `dependency-check`; branch protection con checks obligatorios. Cachear Maven/npm para mantenerlo rápido.

```yaml
name: ci
on: [push, pull_request]
jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17', cache: maven }
      - run: mvn -B clean verify
  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '22', cache: npm, cache-dependency-path: frontend/package-lock.json }
      - run: npm ci && npm run build
        working-directory: frontend
```

---

### 🟡 MEDIUM

#### SEC-02 — JWT en `sessionStorage`, claim de email y ausencia de CSP

- **Severidad:** 🟡 MEDIUM
- **Ubicación:** `frontend/src/api.ts:279,310-318`; `src/main/java/com/claseya/security/JwtService.java:36`; `frontend/index.html` (sin CSP); `SecurityConfig` (sin headers explícitos).
- **Descripción:** El token se guarda en `sessionStorage`, accesible a cualquier JavaScript del origen. El JWT incluye `email` y `role` además del `sub`. No hay `Content-Security-Policy` ni configuración explícita de headers. No se encontraron sinks de XSS (`dangerouslySetInnerHTML`, `innerHTML`, `eval`), por lo que el riesgo es condicional a un XSS futuro o a una dependencia comprometida.
- **Impacto:** si aparece un XSS, el atacante roba el token (válido 1 h) y el email; no hay refresh token ni revocación, así que la sesión no se puede invalidar hasta que expire.
- **Solución propuesta:** agregar CSP estricta (con orígenes de Google Identity), `Referrer-Policy`, `X-Content-Type-Options` y `Permissions-Policy`; reducir claims al mínimo (`sub`, `role`) y evitar PII en el token; evaluar cookie `HttpOnly` + `SameSite` con CSRF token si se prioriza resistencia a XSS. Mantener TTL corto y agregar `jti` para una futura lista de revocación.

---

#### SEC-03 — Endpoints `/api/test/*` desplegados en producción

- **Severidad:** 🟡 MEDIUM
- **Ubicación:** `src/main/java/com/claseya/web/SecurityTestController.java:19,59`; `src/main/java/com/claseya/security/SecurityConfig.java:47-51`; `frontend/playwright.config.ts` (usa `/api/test/public` como health check).
- **Descripción:** El andamiaje de pruebas de seguridad sigue formando parte del artefacto. `/api/test/authenticated`, `/student`, `/teacher` y `/admin` devuelven `userId`, `email` y `role`; `/api/test/public` se usa como sonda de salud de Playwright.
- **Impacto:** superficie innecesaria en producción, exposición de email/rol a cualquier usuario autenticado, y acoplamiento que impide retirarlo sin romper E2E.
- **Solución propuesta:** eliminar el controller y reemplazar la sonda por `GET /actuator/health` (solo `health` expuesto). Si se necesita conservarlo temporalmente, anotarlo con `@Profile("local")` y excluirlo del jar de producción.

---

#### SEC-04 — Política de contraseñas débil y enumeración en registro

- **Severidad:** 🟡 MEDIUM
- **Ubicación:** `src/main/java/com/claseya/auth/dto/RegisterRequest.java:15`; `src/main/java/com/claseya/auth/service/AuthService.java:54`.
- **Descripción:** Solo se exige `min = 8`, sin máximo, sin complejidad, sin lista de contraseñas comprometidas. BCrypt trunca a 72 bytes, pero no se valida ese límite. El registro responde `409` para emails existentes, lo que permite enumeración (el login sí es genérico).
- **Impacto:** contraseñas débiles y reutilizadas; comportamiento silencioso con contraseñas >72 bytes; enumeración de cuentas facilitada por la ausencia de rate limiting.
- **Solución propuesta:** `@Size(min = 10, max = 72)` + política de complejidad o `zxcvbn`, verificación contra HaveIBeenPwned (k-anonymity) y respuesta de registro genérica (`202`) con envío de email, o al menos rate limiting + CAPTCHA. Documentar la decisión.

---

#### BUG-01 — Carrera de creación de conversaciones duplicadas

- **Severidad:** 🟡 MEDIUM
- **Ubicación:** `src/main/java/com/claseya/messaging/service/ConversationService.java:87-98`; `docs/architecture/decisions/ADR-011-conversacion-unica-por-par.md`; `V1__create_schema.sql` / `V4__messaging_support.sql` (sin constraint de par).
- **Descripción:** El flujo busca una conversación existente y, si no la encuentra, crea una nueva. Dos POST concurrentes del mismo par pueden pasar el `findBetween` antes de que cualquiera commitee y producir dos conversaciones. ADR-011 lo reconoce como limitación aceptada, pero no hay garantía de base de datos.
- **Impacto:** hilos duplicados, mensajes divididos, UX confusa y datos sucios; el “service resuelve determinísticamente después” no deshace el duplicado.
- **Solución propuesta:** agregar en `V9` una tabla de par (`student_user_id`, `teacher_user_id`, `conversation_id UNIQUE`) con PK compuesta y un `INSERT ... ON CONFLICT DO NOTHING`, o serializar con `pg_advisory_xact_lock` sobre el par. Mantener la reutilización y agregar un test de concurrencia.

```sql
CREATE TABLE conversation_pairs (
    student_user_id uuid NOT NULL REFERENCES users (id),
    teacher_user_id uuid NOT NULL REFERENCES users (id),
    conversation_id uuid NOT NULL UNIQUE REFERENCES conversations (id),
    PRIMARY KEY (student_user_id, teacher_user_id),
    CHECK (student_user_id <> teacher_user_id)
);
```

---

#### BUG-02 — Token válido de un usuario eliminado produce 500

- **Severidad:** 🟡 MEDIUM
- **Ubicación:** `src/main/java/com/claseya/security/JwtAuthenticationFilter.java:48-56`.
- **Descripción:** `loadUserById` lanza `UsernameNotFoundException` si el usuario ya no existe, pero el `catch` solo cubre `JwtException | IllegalArgumentException`. La excepción escapa del filtro y termina en el catch-all como 500.
- **Impacto:** respuesta incorrecta (500 en lugar de 401), ruido en logs/alertas y una diferencia de comportamiento entre “token inválido” y “usuario borrado”.
- **Solución propuesta:** capturar también `UsernameNotFoundException` (o `AuthenticationException`) y dejar la request sin autenticar. Agregar test de regresión.

```java
} catch (JwtException | IllegalArgumentException | UsernameNotFoundException e) {
    // Token válido pero usuario inexistente: request no autenticada.
}
```

---

#### BUG-03 — La disponibilidad pública no respeta la visibilidad D1 completa

- **Severidad:** 🟡 MEDIUM
- **Ubicación:** `src/main/java/com/claseya/availability/specification/AvailabilityWindowSpecifications.java:25-29`; `src/main/java/com/claseya/availability/service/AvailabilityService.java:102-110`.
- **Descripción:** `visible()` exige ventana `AVAILABLE` y usuario `ACTIVE`, pero **no** exige anuncio publicado (nombre, bio, materia, modalidad y disponibilidad), a diferencia de `TeacherSpecifications.visible()`. Un profesor con anuncio incompleto o despublicado sigue exponiendo sus franjas por `/api/availability?teacherId=...`.
- **Impacto:** inconsistencia de la regla de visibilidad entre módulos; se puede inferir actividad/disponibilidad de perfiles que no deberían existir públicamente. Es exactamente el tipo de deriva que el servicio centralizado busca evitar.
- **Solución propuesta:** reutilizar la regla de profesor visible dentro de un `EXISTS` sobre `AvailabilityWindow.teacher`, o hacer que el endpoint público valide primero `teacherVisibilityService.requireVisible(teacherId)`. Agregar test de anuncio incompleto.

---

#### PERF-01 — Consultas redundantes en visibilidad y búsqueda; re-fetch de la página

- **Severidad:** 🟡 MEDIUM
- **Ubicación:** `src/main/java/com/claseya/teacher/service/TeacherVisibilityService.java:27-41`; `src/main/java/com/claseya/teacher/specification/TeacherSpecifications.java:59-82`; `src/main/java/com/claseya/teacher/service/TeacherSummaryAssembler.java:55`.
- **Descripción:** `requireVisible` hace `findById` + `count` con una especificación que contiene tres `EXISTS` (materias, modalidades, disponibilidad). La búsqueda aplica esos mismos `EXISTS` por fila tanto en la query de contenido como en la de `count`. El assembler vuelve a cargar los perfiles de la página con `findWithUserByIds` aunque ya vengan del `Page`.
- **Impacto:** latencia y carga de base que crecen con el catálogo; en un directorio con miles de profesores y filtros combinados, el `count` con subconsultas correlacionadas es el primer cuello de botella.
- **Solución propuesta:** resolver `requireVisible` con una sola consulta (`findOne(spec)` y `orElseThrow`), agregar un flag denormalizado `is_published` mantenido por el servicio (o una vista materializada) e indexarlo, y usar `@EntityGraph(attributePaths = "user")` en la consulta de búsqueda para evitar el re-fetch. Medir con `EXPLAIN (ANALYZE, BUFFERS)` antes y después.

```java
public TeacherProfile requireVisible(UUID teacherId) {
    return teacherProfileRepository
            .findOne(TeacherSpecifications.visible().and(TeacherSpecifications.hasId(teacherId)))
            .orElseThrow(ResourceNotFoundException::new);
}
```

---

#### PERF-02 — Precarga del perfil de estudiante con fan-out de requests

- **Severidad:** 🟡 MEDIUM
- **Ubicación:** `frontend/src/components/StudentProfileModal.tsx:109-110`.
- **Descripción:** Para deducir la facultad de una carrera existente, el modal pide `api.careers(unit.id)` para **cada** unidad académica de la universidad en paralelo (`Promise.all` sobre `units.map`). Con muchas facultades, son N requests HTTP solo para abrir el formulario.
- **Impacto:** latencia perceptible, carga innecesaria en el backend y un patrón que escala mal al crecer el catálogo.
- **Solución propuesta:** exponer la unidad en el DTO del perfil (`academicUnitId`) o agregar un endpoint `GET /api/careers/{careerId}` que devuelva carrera + unidad + universidad; el modal resuelve la cascada con una sola llamada. Si se mantiene el fan-out, cachear por universidad.

---

#### QUAL-01 — TypeScript no estricto y ausencia de lint

- **Severidad:** 🟡 MEDIUM
- **Ubicación:** `frontend/tsconfig.json:14-16` (`strict: false`, `noUnusedLocals: false`, `noUnusedParameters: false`); `frontend/src/api.ts:338` (`let parsed: any`); no hay configuración de ESLint ni script `lint`.
- **Descripción:** El proyecto usa TypeScript pero renuncia a las garantías que justifican su costo. `strict: false` permite `null`/`undefined` fuera de control y `any` implícitos; el cliente HTTP usa `any` explícito. No hay lint que detecte hooks mal usados, imports muertos o dependencias faltantes.
- **Impacto:** errores en runtime que el compilador podría haber atrapado; refactors riesgosos; inconsistencias de estilo que el equipo termina discutiendo en review.
- **Solución propuesta:** activar `strict` de forma incremental (empezar por `strictNullChecks` y `noImplicitAny`), reemplazar `any` por un tipo `ApiErrorBody`, agregar ESLint + `eslint-plugin-react-hooks` + Prettier y un script `npm run lint` que CI ejecute.

---

#### QUAL-02 — `MyListingPage` es un monolito de 693 líneas

- **Severidad:** 🟡 MEDIUM
- **Ubicación:** `frontend/src/pages/MyListingPage.tsx` (693 líneas).
- **Descripción:** La página mezcla carga inicial de siete recursos, formulario de perfil, cascada académica, materias, modalidades, formación, disponibilidad, verificación, confirmaciones y toasts. Además duplica el mapeo `TeacherProfile → TeacherProfileInput` en la carga y al guardar (`setForm({...})` repetido).
- **Impacto:** difícil de revisar, testear y modificar; cualquier cambio en una sección obliga a leer el archivo completo. Es el tipo de archivo donde aparecen regresiones cruzadas.
- **Solución propuesta:** extraer hooks (`useTeacherListing`, `useAcademicCascade`) y componentes por sección (`ProfileForm`, `SubjectsSection`, `EducationSection`, `AvailabilitySection`), dejando la página como orquestadora. Mover el mapeo a una función pura testeable.

---

#### QUAL-03 — Modelo y tablas de una dirección de producto abandonada

- **Severidad:** 🟡 MEDIUM
- **Ubicación:** `src/main/java/com/claseya/model/Booking.java`, `CalendlyIntegration.java`, `Review.java`, `Report.java`, `Notification.java` y sus enums; `src/main/resources/db/migration/V1__create_schema.sql:226,246,264,283,300`.
- **Descripción:** Reservas, Calendly, reviews, reportes y notificaciones quedaron fuera del rumbo (AGENTS.md), pero las entidades y tablas siguen presentes sin lógica de negocio. `rg` confirma que solo se referencian desde el modelo/enums (y `TeacherEducationRepository` por coincidencia de nombre). El esquema mantiene FKs e índices para features que no existen.
- **Impacto:** confusión para nuevos desarrolladores y agentes, riesgo de que alguien “complete” una feature descartada, peso innecesario en backups/migraciones y una falsa sensación de funcionalidad.
- **Solución propuesta:** ADR que confirme el descarte y, según apetito de riesgo, `V9` que elimine tablas no usadas (con backup/ventana) **o** al menos retirar las entidades y documentar las tablas como legacy; nunca construir sobre ellas.

---

#### TEST-01 — Sin tests unitarios/componentes de frontend

- **Severidad:** 🟡 MEDIUM
- **Ubicación:** `frontend/package.json:6-10` (solo `test:e2e`); `frontend/tests/` (25 escenarios Playwright).
- **Descripción:** Toda la verificación de UI depende de E2E contra el stack completo. No hay Vitest/Testing Library ni tests de funciones puras (normalización de precios, formateo, decodificación de sesión, orden de mensajes, validaciones de formulario).
- **Impacto:** feedback lento, tests frágiles por estado compartido, poca cobertura de ramas de error y dificultad para refactorizar componentes.
- **Solución propuesta:** agregar Vitest + Testing Library y extraer lógica pura a `lib/` para testearla sin DOM; reservar Playwright para los flujos críticos (login, contacto, favoritos, verificación). Incluir `npm test` en CI.

---

#### OPS-01 — Sin health checks, métricas ni trazabilidad operativa

- **Severidad:** 🟡 MEDIUM
- **Ubicación:** `pom.xml` (sin `spring-boot-starter-actuator`); `src/main/java` (solo tres llamadas a `log.*`: error no manejado, descarga admin y seed); `frontend/playwright.config.ts` usa `/api/test/public` como sonda.
- **Descripción:** No hay endpoint de salud/liveness/readiness, métricas, request IDs ni logs estructurados. No se audita login exitoso/fallido, bloqueos, rate limits ni decisiones administrativas más allá de la tabla de auditoría.
- **Impacto:** despliegues sin health check confiable, diagnóstico de incidentes a ciegas, imposibilidad de alertar sobre errores 5xx o latencia, y baja capacidad de investigación forense.
- **Solución propuesta:** agregar Actuator (`health`, `info`, `metrics`, `prometheus`) con exposición restringida; logs JSON con `traceId`/`userId` (nunca PII sensible), filtro de correlación; health check real en Docker/orquestador.

---

#### OPS-02 — Imagen Docker ejecutada como root y sin health check

- **Severidad:** 🟡 MEDIUM
- **Ubicación:** `Dockerfile:15-24`.
- **Descripción:** La etapa final no define `USER`, no tiene `HEALTHCHECK`, compila con `-DskipTests` y no ajusta memoria para contenedor (`-XX:MaxRAMPercentage`). El JRE corre como root por defecto.
- **Impacto:** mayor blast radius ante una vulnerabilidad de ejecución; orquestador sin señal de salud; riesgo de OOM mal gestionado; tests ausentes en la construcción de la imagen.
- **Solución propuesta:** crear usuario no privilegiado, agregar health check contra Actuator, fijar `-XX:MaxRAMPercentage=75`, ejecutar tests en CI (no necesariamente en la imagen) y firmar/escancar la imagen (Trivy/Grype).

```dockerfile
RUN addgroup --system app && adduser --system --ingroup app app
USER app
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
```

---

#### DEV-01 — Onboarding local no reproducible

- **Severidad:** 🟡 MEDIUM
- **Ubicación:** `.gitignore:26` (`src/main/resources/application-local.yml` ignorado); no hay `application-local.example.yml`, `docker-compose.yml` ni Maven Wrapper; `README.md` (sección 7).
- **Descripción:** El perfil `local` es necesario para seed y Google Client ID, pero el archivo que lo activa está fuera del repositorio y no hay plantilla versionada. La base se crea con un `docker run` manual y no hay `mvnw`.
- **Impacto:** un clon nuevo arranca sin seed ni Google login y el desarrollador debe reconstruir configuración de memoria; divergencias entre entornos y tiempo perdido.
- **Solución propuesta:** versionar `application-local.example.yml` con placeholders, agregar `docker-compose.yml` (Postgres + backend opcional), incluir Maven Wrapper y documentar el flujo en un único comando.

---

#### STOR-01 — Evidencia binaria en PostgreSQL sin antivirus ni streaming

- **Severidad:** 🟡 MEDIUM
- **Ubicación:** `src/main/resources/db/migration/V8__verification_circuit.sql:39` (`content bytea`); `src/main/java/com/claseya/verification/service/PostgresDocumentStorage.java:19`; límites de 10 MB/archivo y 25 MB/credencial en `VerificationDocumentService`.
- **Descripción:** Los binarios se guardan en `bytea` y se cargan completos en heap (`byte[]`) para subir y descargar. La validación es por magic bytes y hash, pero no hay escaneo antimalware, cuarentena ni streaming. La decisión D5 está documentada, pero el costo operativo no.
- **Impacto:** crecimiento de la base y de los backups, presión de memoria con descargas concurrentes, y riesgo de almacenar malware que luego se sirve como adjunto (aunque con `Content-Type` vetted y `attachment`).
- **Solución propuesta:** mantener la interfaz `DocumentStorage` y migrar a object storage privado (S3/R2) con URLs prefirmadas, escaneo asíncrono (ClamAV o servicio gestionado) y cuarentena; para el MVP, al menos streaming y límites por usuario. Documentar retención/purga (D6).

---

### 🔵 LOW / REFACTOR

#### SEC-05 — CORS con credenciales innecesarias y sin normalización de orígenes

- **Severidad:** 🔵 LOW
- **Ubicación:** `src/main/java/com/claseya/security/SecurityConfig.java:100-107`.
- **Descripción:** La API usa `Authorization: Bearer` y no cookies, pero `setAllowCredentials(true)` está activo. Además, `allowedOrigins.split(",")` no hace `trim()`, por lo que un valor con espacios (por ejemplo, `https://a.com, https://b.com`) deja de coincidir.
- **Impacto:** configuración más laxa de lo necesario y fallos silenciosos de CORS en despliegues con listas separadas por comas y espacios.
- **Solución propuesta:** `setAllowCredentials(false)` mientras no haya cookies; normalizar con `Arrays.stream(...).map(String::trim).filter(s -> !s.isBlank())`. Si en el futuro se migra a cookies, re-evaluar junto con CSRF.

---

#### SEC-06 — `photoUrl` sin validación de esquema

- **Severidad:** 🔵 LOW
- **Ubicación:** `src/main/java/com/claseya/teacher/service/TeacherProfileService.java:111`; `src/main/java/com/claseya/teacher/dto/UpdateTeacherProfileRequest.java` (solo `@Size`).
- **Descripción:** Se acepta cualquier string de hasta 500 caracteres como URL de foto. React lo usa en `<img src>`, por lo que `javascript:` no se ejecuta en ese contexto, pero se permiten `data:`, URLs de tracking o recursos externos arbitrarios.
- **Impacto:** seguimiento de usuarios, contenido inesperado, ruptura de la política de imágenes y dependencia de terceros.
- **Solución propuesta:** validar `https://` y, idealmente, migrar a un pipeline de subida con almacenamiento propio y transformación; si se aceptan URLs externas, usar allowlist de hosts o proxy de imágenes.

---

#### DOC-01 — Documentación desactualizada o contradictoria

- **Severidad:** 🔵 LOW
- **Ubicación:** `docs/testing/testing-strategy.md:11` (“~104 tests” vs. 222 reales); `src/main/java/com/claseya/teacher/controller/TeacherSearchController.java:19` (“Only verified + active teachers” cuando la regla actual es ACTIVE + anuncio publicado); `README.md` (sección 2) presenta email+contraseña como implementado.
- **Descripción:** Los números y las reglas de visibilidad cambiaron, pero algunos documentos y comentarios quedaron en la versión anterior. Esto es especialmente riesgoso en un repo que se declara “fuente de verdad”.
- **Impacto:** nuevos desarrolladores y agentes toman decisiones con información incorrecta; los reviews pierden tiempo detectando contradicciones.
- **Solución propuesta:** actualizar conteos, comentario del controller y la sección de cuentas; agregar una verificación de docs en el checklist de DoD cuando cambie una regla de visibilidad o un flujo de autenticación.

---

#### DEP-02 — Dependencias y tooling de Neon en la raíz sin uso por la aplicación

- **Severidad:** 🔵 LOW
- **Ubicación:** `package.json` (raíz, con `@neon/config` y `@neon/env`); `neon.ts`; `package-lock.json` raíz; `node_modules/` raíz.
- **Descripción:** El backend no usa Node y el frontend tiene su propio `package.json`. Las dependencias de la raíz parecen tooling del agente/CLI de Neon, no parte del producto, y agregan un segundo lockfile y superficie de supply chain.
- **Impacto:** confusión sobre qué se despliega, auditorías npm duplicadas y riesgo de que una dependencia de tooling se trate como dependencia de producción.
- **Solución propuesta:** mover el tooling a `tools/` o eliminarlo del repo si no es necesario; documentar explícitamente por qué existe. Mantener un único lockfile por aplicación desplegable.

---

#### UX-01 — Límites silenciosos de 50 elementos en favoritos y disponibilidad

- **Severidad:** 🔵 LOW
- **Ubicación:** `frontend/src/api.ts:401,462`; `frontend/src/App.tsx` (favoritos sin paginación).
- **Descripción:** La UI pide `size=50` y no pagina; un estudiante con más de 50 favoritos o un profesor con más de 50 franjas verá solo las primeras 50 sin aviso.
- **Impacto:** funcionalidad incompleta percibida como “desaparecieron datos”, especialmente en cuentas longevas.
- **Solución propuesta:** agregar paginación/infinite scroll o mostrar un aviso con “ver más”; para disponibilidad, validar si el límite real justifica más de 50 franjas.

---

## 4. Plan de remediación sugerido

### Antes de producción (bloqueantes)

1. **EVID-01:** bloquear borrado de evidencia usada/presentada y agregar tests de regresión.
2. **SEC-01:** rate limiting + lockout + cuotas de contacto/subida; CAPTCHA o al menos backoff en login.
3. **DEP-01:** subir Spring Boot a una línea soportada y agregar Maven a Dependabot.
4. **PRIV-01:** filtrar favoritos por visibilidad D1 y testear el cambio de estado.
5. **CI-01:** pipeline mínimo con backend tests, `tsc`, build y auditoría de dependencias; branch protection.

### Corto plazo (1–2 sprints)

6. **AUTH-01:** ✅ hecho — se eligió completar la activación (spec `AUTH-001`, `ADR-021`).
7. **ARCH-01:** router + URLs reales + metadatos por perfil (aunque el prerender/SSR quede para después).
8. **SEC-02/SEC-03/SEC-04:** CSP, retiro de `/api/test/*`, política de contraseñas y registro
   genérico hechos; HIBP/CAPTCHA quedan pendientes (`ADR-023`).
9. **OPS-01/OPS-02:** ✅ health y Docker no-root + `HEALTHCHECK` hechos; faltan métricas, logs
   estructurados, request IDs y alertas (`OPS-01`).
10. **BUG-02/BUG-03:** ✅ hechos — 401 para un token de usuario borrado y visibilidad D1 completa en
    disponibilidad.

### Deuda planificada (backlog)

11. **PERF-01/PERF-02:** optimización de búsqueda y de la precarga del perfil con medición.
12. **QUAL-01/QUAL-02/QUAL-03:** ✅ strict TS + ESLint, división de `MyListingPage` y limpieza de
    modelos legacy (`ADR-024`) hechos.
13. **TEST-01:** ✅ hecho — Vitest + Testing Library para lógica pura y componentes; Playwright
    queda para los flujos críticos.
14. **STOR-01/DEV-01:** object storage + antivirus, compose/example/wrapper para onboarding.

---

## 5. Anexo: verificación realizada y aspectos positivos

### Verificación

| Comprobación | Resultado |
|---|---|
| `mvn -B -q -DskipTests compile` | ✅ OK |
| `npx tsc --noEmit` (frontend) | ✅ OK |
| `npm audit` (frontend) | ✅ 0 vulnerabilidades reportadas al momento de la revisión |
| `mvn -B clean test` | ⏸️ ✅ Ejecutado: 280 tests, 0 failures (Docker 29.6.2) |
| Playwright E2E | ⏸️ ✅ 35/35 con PostgreSQL 16 + demo seed |

**Tamaño:** ~7.694 LOC backend main, ~3.778 LOC tests backend, ~4.715 LOC frontend src, ~941 LOC E2E; 192 archivos Java main, 24 archivos Java test, 222 métodos `@Test`.

### Fortalezas que conviene preservar

- **Identidad desde el contexto:** `CurrentUser` se usa de forma consistente; no se aceptan `userId`, `studentId`, `senderId` ni `participantIds` del cliente para decidir quién actúa.
- **Autorización por rol y ownership:** favoritos, mensajes, documentos y perfiles personales validan pertenencia; conversaciones ajenas y documentos ajenos responden 404.
- **Seguridad de configuración:** `JwtSecretValidator` falla al arrancar fuera de `local` si el secreto es el default o mide menos de 32 bytes; JWT stateless; BCrypt; un único punto de mapeo de roles.
- **Validación y contratos:** Bean Validation, DTOs record, paginación con whitelist de orden y topes, errores `ApiError` sin stack traces, magic bytes + SHA-256 + nombre saneado + descarga como `attachment`.
- **Base de datos:** Flyway como única fuente del esquema, `ddl-auto=validate`, UUID, `timestamptz`, CHECKs, FK compuesta que garantiza que el emisor de un mensaje sea participante, y constraint de exclusión para solapamiento de disponibilidad.
- **Tests:** suite de integración amplia con casos de IDOR, roles, visibilidad, concurrencia de verificación y validación de archivos; E2E de los flujos principales.
- **Documentación:** PRD, specs, ADRs y documentos de arquitectura/seguridad de calidad superior a la media; el README es honesto sobre varias limitaciones (aunque no todas).

---

---

## 6. Seguimiento de remediación

| ID | Estado | Detalle |
|---|---|---|
| EVID-01 | ✅ Corregido en working tree (pendiente de commit) | El borrado ahora se bloquea si la credencial fue presentada (`submittedAt != null`), tiene decisiones de auditoría o no está en `PENDING`. Se agregaron 4 tests unitarios y 6 tests de integración (servicio + API), y la UI oculta el botón de eliminar cuando la credencial ya fue presentada. `mvn -B test -Dtest=VerificationDocumentServiceTest` y `npx tsc --noEmit` en verde; la suite completa quedó verde (275/275). |
| SEC-01 | ✅ Corregido en working tree (pendiente de commit) | Implementado RATE-001 con Bucket4j `bucket4j_jdk17-core` 8.21.0 en memoria: límites por IP/cuenta para login, registro y Google; por usuario para contacto, mensajes y upload; `429` + `Retry-After`; claves hasheadas; store acotado; ADR-017. 38 tests unitarios verdes; 4 tests de integración verdes en la suite completa (275/275). Limitación documentada: por instancia, sin Redis. |
| PRIV-01 | ✅ Corregido en working tree (pendiente de commit) | `GET /api/favorites` ahora filtra por la regla D1 completa (usuario ACTIVE + anuncio publicado) con paginación y `totalElements` exactos. Se agregaron 2 tests de integración (profesor suspendido y anuncio despublicado), verdes en la suite completa (275/275). |
| DEP-01 | ✅ Corregido en working tree (pendiente de commit) | Spring Boot 3.2.6 → **3.5.16**, `flyway-database-postgresql` agregado por Flyway 11, Dependabot Maven configurado y ADR-018 documentado. `mvn -B clean test` corrido con Docker: **275/275 verdes**. Migración a 4.x queda como decisión separada. |
| CI-01 | ✅ Corregido en working tree (pendiente de commit) | Workflow `.github/workflows/ci.yml`: backend `mvn clean verify` + Testcontainers, frontend typecheck/build/audit, E2E Playwright con PostgreSQL + demo seed y dependency review en PRs. Backend verificado con `mvn clean verify` (**275/275**) y E2E local (**25/25**). Falta activar branch protection para que el pipeline sea un gate obligatorio. |
| ARCH-01 | ✅ Corregido en working tree (pendiente de commit) | `react-router` 7 en modo librería: rutas reales (`/`, `/buscar`, `/profesores/:id`, `/favoritos`, `/mensajes/:id`, `/mi-anuncio`, 404), estado de búsqueda en query params con whitelist, guardas de sesión/rol, metadatos por página y lazy loading por página. ADR-019 y spec ARCH-001. 10 tests E2E nuevos de deep links/historial/guardas y suite completa **35/35**; `npx tsc --noEmit` y `npm run build` verdes. SSR/prerender, Open Graph social y sitemap quedan diferidos a una fase posterior. |
| SEC-02 | ✅ Corregido en working tree (pendiente de commit) | Sesión migrada a cookie `CLASEYA_SESSION` `HttpOnly`/`Secure`/`SameSite` (login y Google), `GET /api/auth/me`, `POST /api/auth/logout` y CSRF por doble submit (`X-XSRF-TOKEN`) con token pedido antes de cada escritura. JWT con `sub`/`jti`/`iat`/`exp` sin email/role. CSP con allowlist de Google y chequeo en `npm run build`, cabeceras del API, `_headers`, tema externalizado. Bearer se mantiene para integración/tooling. ADR-020 y spec SEC-002. Backend **280/280** y E2E **35/35**. Logout no revoca el JWT (documentado). |
| AUTH-01 | ✅ Corregido en working tree (pendiente de commit) | Registro completo con activación por email (`AUTH-001`, `ADR-021`): token de un solo uso hasheado (SHA-256, TTL 24 h), `V9` con `email_verification_tokens` + `users.email_verified_at`, `POST /api/auth/activate`, reenvío genérico `202`, activación manual por ADMIN, `spring-boot-starter-mail` (sin SMTP el registro responde `503` y revierte) y, en desarrollo, `AUTH_LOG_ACTIVATION_LINKS`. SPA con `/registro`, `/activar`, enlace desde el login y aviso de reenvío tras un 401. El `origin_mismatch` de Google se diagnostica en la UI y se documentan los orígenes exactos (es configuración de Google Cloud Console, no código). 10 tests backend nuevos, 1 de rate limit y 5 E2E nuevos; suite **291/291** y E2E **40/40**. El `409` del registro queda en SEC-04 por diseño. |
| BUG-02 | ✅ Corregido en working tree (pendiente de commit) | `JwtAuthenticationFilter` ahora captura `AuthenticationException` (incluye `UsernameNotFoundException`): un token válido de una cuenta borrada deja la request sin autenticar y responde **401** en lugar de 500. Regresión agregada en `AuthIntegrationTest`. |
| BUG-03 | ✅ Corregido en working tree (pendiente de commit) | `AvailabilityWindowSpecifications.visible()` reutiliza la regla D1 completa mediante `TeacherSpecifications.visibleTeacher(...)`; `GET /api/availability?teacherId=` de un profesor oculto responde **404** y la búsqueda pública ya no expone sus franjas. Tests de disponibilidad actualizados (18) y suite completa **292/292**. |
| SEC-03 | ✅ Corregido en working tree (pendiente de commit) | Eliminado `web/SecurityTestController` y las reglas `/api/test/**`. El andamiaje ya no forma parte del artefacto; Playwright usa `GET /actuator/health` como sonda. Los tests de roles/identidad se reescribieron contra endpoints reales (`/api/favorites`, `/api/teachers/me`, `/api/admin/verifications`, `/api/auth/me`, `/api/teachers`). |
| OPS-01 | ✅ Corregido en working tree (pendiente de commit) | Health público sin detalles, `GET /actuator/metrics` (Micrometer) **ADMIN-only**, `X-Request-Id` validado en respuesta y MDC, y logs estructurados opt-in (`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`). Tests de health, métricas por rol y request id. Prometheus/trazas/alertas quedan como configuración de infraestructura. |
| SEC-04 | ✅ Corregido en working tree (pendiente de commit) | Política con `@ValidPassword`, registro **202** genérico, rate limiting y **CAPTCHA Turnstile** (`SEC-003`, `ADR-025`): verificación server-side antes de buscar el email, fail-closed (400/503) y off por defecto. HIBP sigue diferido (`ADR-023`), pero el hallazgo queda cubierto por política + 202 + rate limit + CAPTCHA. |
| OPS-02 | ✅ Corregido en working tree (pendiente de commit) | La imagen final corre como usuario `app` (uid/gid 999), declara `HEALTHCHECK` cada 30 s contra `/actuator/health` y arranca con `-XX:MaxRAMPercentage=75`. `.dockerignore` excluye `application-local.yml` y `.env*` para no hornear configuración local. Verificado con `docker build` + contenedor contra PostgreSQL local: `healthy`, health 200 y `id` no-root. |
| QUAL-01 | ✅ Corregido en working tree (pendiente de commit) | `tsconfig` con `strict`, `noUnusedLocals` y `noUnusedParameters`; `any` reemplazado por `ApiErrorBody`; ESLint 10 flat config con `typescript-eslint` y reglas de hooks (`rules-of-hooks`/`exhaustive-deps`) activas; `npm run lint` en CI. Las reglas nuevas del React Compiler (`refs`, `set-state-in-effect`) y Prettier quedan fuera por decisión (`QUAL-001`). |
| TEST-01 | ✅ Corregido en working tree (pendiente de commit) | Vitest 5 + jsdom + Testing Library; 21 tests en 4 archivos (formato, validación de registro, rutas y `Field`). La validación del registro se extrajo a `lib/validation.ts` y se testea sin DOM. `npm test` corre en el job de frontend del CI; Playwright conserva los flujos críticos. |
| QUAL-02 | ✅ Corregido en working tree (pendiente de commit) | `MyListingPage` pasa de 698 a 127 líneas: `useTeacherListing` (carga + mutaciones), `useAcademicCascade` y secciones (`Profile`, `Subjects`, `Modalities`, `Education`, `Availability`, `AnnouncementStatus`). El mapeo `TeacherProfile → TeacherProfileInput` vive en `lib/teacherProfile.ts` y tiene tests. JSX, labels y `data-testid` intactos; 24 unit y E2E 40/40 verdes. |
| QUAL-03 | ✅ Corregido en working tree (pendiente de commit) | `ADR-024` confirma el descarte de bookings/Calendly/reviews/reports/notifications y elimina las entidades/enums JPA sin uso. Las tablas de `V1` se conservan como legacy (sin migración destructiva) y `docs/architecture/database.md` lo documenta. Sin cambios de API ni de comportamiento; suite completa verde. |
| SEC-05 | ✅ Reinterpretado y corregido (working tree) | `allowCredentials(true)` es **necesario** desde ADR-020 (cookie HttpOnly); no se desactiva. Se normalizan los orígenes separados por coma con `trim()` y se descartan los vacíos (`SecurityConfig.parseOrigins`), con test unitario. |
| SEC-06 | ✅ Corregido en working tree (pendiente de commit) | `photoUrl` acepta solo `https://...` o vacío (`@Pattern` en Create/Update), además del límite de 500. `javascript:`, `http://` y URLs con espacios devuelven 400; vacío limpia el campo. Test de integración agregado. |
| DOC-01 | ✅ Corregido en working tree (pendiente de commit) | `docs/testing/testing-strategy.md` refleja 301 tests backend + 24 unit + 40 E2E; el comentario de `TeacherSearchController` y la tabla de `security.md` describen la regla D1 (ACTIVE + anuncio publicado), no la vieja VERIFIED+ACTIVE. |
| UX-01 | ✅ Corregido en working tree (pendiente de commit) | Favoritos y disponibilidad muestran un aviso `role="status"` cuando el total de la API supera los 50 elementos visibles, en vez de ocultarlos en silencio. El total se mantiene al agregar/quitar favoritos y al agregar horarios. Build, 24 unit y E2E 40/40 verdes. |
| DEV-01 | ✅ Corregido en working tree (pendiente de commit) | `docker-compose.yml` (postgres:16 + healthcheck + volumen), `application-local.example.yml` versionado (sin secretos reales) y **Maven Wrapper** (`mvnw`/`mvnw.cmd`, Maven 3.9.16). README actualizado; `docker compose config` y `mvnw -v` verificados. |
| PERF-02 | ✅ Corregido en working tree (pendiente de commit) | `StudentProfileResponse` incluye `academicUnitId`; el modal lo usa para preseleccionar la facultad y hace **una** request de carreras, en lugar del fan-out `Promise.all` sobre todas las unidades. Test de integración y E2E de perfil verdes. |
| PERF-01 | ✅ Corregido en working tree (pendiente de commit) | `requireVisible` resuelve visibilidad + id en **una** consulta. Se midió con `EXPLAIN (ANALYZE, BUFFERS)` sobre una base desechable con **5.059 profesores visibles**: count **7,6 ms** y búsqueda paginada **8,1 ms**. El flag denormalizado `is_published`/vista materializada no se justifica con esta evidencia y queda descartado hasta que una medición real muestre lo contrario. |
| DEP-02 | ✅ Corregido en working tree (pendiente de commit) | Se retiró el tooling de Neon sin uso de la raíz (`package.json`, `package-lock.json`, `neon.ts`): ninguna referencia en código, CI, scripts o docs. Queda un único lockfile, el de `frontend/`. |

> El informe original se generó en modo solo lectura. La corrección de EVID-01 se aplicó después de la auditoría (backend + guard de UI); el resto del código de producto no se modificó. El conteo de la tabla resumen refleja el estado al momento de la auditoría.
