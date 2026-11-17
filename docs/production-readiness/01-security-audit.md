# 01 — Auditoría de seguridad — ClaseYa

- **Fecha:** 2026-11-17
- **Rama / commit:** `main` @ `52cac65`
- **Modo:** revisión estática de solo lectura. No se modificó código, no se ejecutó la suite, no se probó contra servicios desplegados ni externos, no se instalaron herramientas. No se extrajeron datos reales.
- **Marcos de referencia:** OWASP ASVS, OWASP Top 10 (2021), OWASP API Security Top 10 (2023), CWE.
- **Clasificación de cada hallazgo:** **CONFIRMADO** (comprobado en el código) o **SOSPECHA** (indicios, falta confirmación en el entorno o con una prueba).
- Informe previo: `docs/production-readiness/00-project-baseline.md` (los identificadores `R-xx` se refieren a él).

---

## 1. Resumen ejecutivo

La seguridad de la aplicación es sólida. En la revisión no se encontró ninguna vulnerabilidad de severidad CRITICAL ni HIGH confirmada. No se encontró inyección SQL, IDOR/BOLA, escalada de privilegios, SSRF, path traversal ni deserialización insegura. Los controles principales están implementados y verificados en código:

- JWT HS256 con secreto validado al arranque, claims mínimos, versión de sesión (`ver`) y recarga del usuario en cada request.
- Cookie de sesión `HttpOnly` con CSRF de doble submit.
- Registro anti-enumeración, tokens de un solo uso almacenados como hash SHA-256, consumidos con un `UPDATE` atómico.
- Validación del ID token de Google (firma por JWKS, `iss`, `aud`, `exp`, `email_verified`).
- Ownership resuelto siempre desde `CurrentUser`, nunca desde el cliente; documentos ajenos devuelven 404.
- Subida de archivos por contenido real (PDF), tamaño, cuota, escaneo ClamAV fuera de transacción, clave de almacenamiento aleatoria y descarga siempre como adjunto.
- `ProductionConfigValidator`, `JwtSecretValidator` y `DemoDataProductionGuard` que impiden arrancar con configuración insegura.

Los hallazgos reales son de severidad **MEDIUM** y **LOW** y casi todos dependen de configuración o de límites de abuso:

| Severidad | Cantidad |
|---|---:|
| CRITICAL | 0 |
| HIGH | 0 |
| MEDIUM | 4 |
| LOW | 8 |
| INFO | 6 |

Los cuatro MEDIUM son: rate limiting que puede colapsar a un bucket único detrás del proxy de Vercel (S-01), bloqueo de cuenta que permite denegar el acceso a una víctima (S-02), recuperación y reenvío sin límite por destinatario (S-03) y evidencia que puede quedar sin escanear (S-04).

**Bloqueantes para producción:** ninguno de código. Hay tres verificaciones de configuración obligatorias antes de abrir tráfico (§9).

---

## 2. Superficie de ataque

| Superficie | Exposición | Autenticación | Notas |
|---|---|---|---|
| `POST /api/auth/{register,login,activate,resend-activation,forgot-password,reset-password,google,message-link}` | Internet | Ninguna (`permitAll`) | `SecurityConfig.java:72`. Limitadas por IP. |
| `GET /api/auth/me`, `POST /api/auth/logout`, `GET /api/auth/csrf` | Internet | Cookie | `me` autenticado (`:71`). |
| `GET /api/human/status`, `POST /api/human/verify` | Internet | Ninguna | `SecurityConfig.java:73`. |
| Catálogo académico `GET` | Internet | Ninguna | Escritura solo `ADMIN` (`:80-90`). |
| `GET /api/teachers`, `GET /api/teachers/{id}`, `GET /api/teachers/*/reviews`, `GET /api/availability`, `GET /api/subjects/**` | Internet | Ninguna | Solo profesores visibles (D1). |
| `/sitemap.xml`, `/sitemaps/**` | Internet | Ninguna | `SecurityConfig.java:78`. |
| `GET /actuator/health` | Internet | Ninguna | Sin detalles. `metrics` solo `ADMIN` (`:76`). |
| Mensajería, favoritos, reportes, bloqueos, opiniones, perfiles `/me` | Internet | Cookie + roles | `SecurityConfig.java:91-114`. |
| Carga de PDF `POST /api/teachers/me/education/{id}/documents` | Internet | `TEACHER`/`ADMIN` | Multipart 10 MB, 12 MB por request. |
| `/api/admin/**` | Internet | `ADMIN` | `SecurityConfig.java:117`. |
| Salida: Brevo/SMTP, Google JWKS, Turnstile, S3/R2, ClamAV | Servicios externos | Claves del entorno | URLs configurables por entorno, no por el usuario. |
| Prerender SEO (`frontend/api/seo.ts`) | Internet | Ninguna | Genera HTML con datos públicos. |

Actores: visitante anónimo, `STUDENT`, `TEACHER`, `ADMIN`, y un atacante que controla un navegador o un sitio de terceros (CSRF/XSS).

---

## 3. Resultado por área

### A. Autenticación

| Control | Resultado | Evidencia |
|---|---|---|
| Firma y algoritmo | Correcto. HS256 con clave derivada de `jwt.secret`. `Jwts.parser().verifyWith(key)` de jjwt 0.12 rechaza `alg=none` y tokens sin firma. | `security/JwtService.java:79-85` |
| Secreto | `JwtSecretValidator` falla el arranque si es el valor de desarrollo, está vacío o mide menos de 32 bytes, salvo el perfil `local`. | `security/JwtSecretValidator.java` |
| Expiración | 12 h por defecto (`jwt.expiration=43200`). La cookie no tiene `Max-Age`; `exp` del JWT limita la sesión. | `application.yml`, `AuthCookieFactory.java` |
| Claims | `sub`, `jti`, `ver`, `iat`, `exp`. Sin datos personales. | `JwtService.java:40-50` |
| Transmisión y almacenamiento | Cookie `HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/`. El JWT no sale en el cuerpo de la respuesta. No hay uso de `localStorage` para el token. | `AuthCookieFactory.java`, búsqueda en `frontend/src` |
| Invalidación | El filtro recarga el usuario, exige `ACTIVE` y compara `ver` con `users.session_version`. Logout y reset de contraseña incrementan la versión. | `JwtAuthenticationFilter.java:50-62`, `SessionService`, `PasswordResetService.java:103` |
| Refresh tokens | No existen. Sesión corta de 12 h, sin rotación. | — |
| Reuso de token robado | Un JWT robado funciona hasta `exp` o hasta que cambie `ver`. No hay vinculación a dispositivo ni lista de revocación por `jti`. | S-13 |
| Tokens de un solo uso | 32 bytes de `SecureRandom`, guardados como SHA-256, consumo atómico (`UPDATE … WHERE used_at IS NULL`), TTL 24 h (activación), 30 min (reset), 7 días (enlace de mensaje). | `PasswordResetService.java:35,92`, `EmailVerificationTokenRepository.java:22-30` |
| Enumeración | Registro siempre `202`. Login con un único mensaje genérico. Reset y reenvío siempre `202`. | `AuthService.java:74-82,137`, `PasswordResetService.java:63-76` |
| Google OAuth | `NimbusJwtDecoder.withIssuerLocation` valida firma e issuer; el verificador exige `iss`, `aud` que contenga el `client-id`, `exp` y `email_verified=true`. Vincula por `google_sub`, solo cuentas `ACTIVE`, y rechaza `ADMIN`. | `oauth/config/GoogleOAuthConfig.java`, `GoogleIdTokenVerifier.java:35-66`, `GoogleAuthService.java:44-72,81` |
| Token humano vs sesión | El token de `human-check` usa `sub="human-check"`; en el filtro de sesión falla `UUID.fromString` y se ignora. | `JwtService.java:52-72`, `JwtAuthenticationFilter.java:49` |

### B. Autorización

| Control | Resultado | Evidencia |
|---|---|---|
| RBAC | Reglas en `SecurityConfig`, en orden, con `anyRequest().authenticated()` al final. Un único punto de mapeo de roles. | `SecurityConfig.java:69-119` |
| Identidad | Siempre de `CurrentUser.id()`. No se encontró `userId`, `studentId` ni `senderId` provistos por el cliente. | 53 usos de `CurrentUser` en controllers |
| Ownership | Conversaciones: el acceso se prueba con la fila de participante y un tercero recibe 404. Documentos: `findByIdAndTeacher_Id`, `requireOwnedCredential` y filtro por perfil; ajeno → 404. Credenciales: `findOwnedForUpdate(id, teacherId)`. | `ConversationService.java:281`, `VerificationDocumentService.java:97-99,194-196`, `TeacherEducationRepository.java:28-30` |
| IDOR / BOLA | No se encontró. Los repositorios consultan siempre por par `(id, dueño)`. | — |
| Escalada vertical | `ADMIN` no se puede registrar por email (`AuthService.java:66`) ni por Google (`GoogleAuthService.java:81`). El rol solo cambia por SQL. | — |
| Endpoints admin | `/api/admin/**` solo `ADMIN`; el filtro recarga el usuario, así que un cambio de rol surte efecto al siguiente request. | `SecurityConfig.java:117` |
| Documentos privados | Descarga solo del dueño o de un `ADMIN`, siempre `Content-Disposition: attachment`, `Cache-Control: no-store`, clave S3 aleatoria no expuesta. | `TeacherVerificationController.java:90-100`, `AdminVerificationController.java:85-96`, `S3DocumentStorage.java:88` |
| Mass assignment | Los `@RequestBody` son records DTO con campos explícitos; no se aceptan entidades. | grep de `@RequestBody` |
| Autorización en el frontend | `ProtectedRoute` es solo UX; el backend aplica los roles. | `frontend/src/components/routing/ProtectedRoute.tsx` |

### C. API

| Control | Resultado |
|---|---|
| SQL Injection | No encontrada. Las consultas son JPQL/Specifications con parámetros enlazados o SQL nativo con `@Param` (`SubjectRepository`, `NotificationOutboxRepository`, `ConversationPairRepository`). No hay concatenación de entrada del usuario en consultas. |
| Validación | Bean Validation en DTOs; orden de búsqueda con whitelist (`TeacherSort.parse`); tamaños de página acotados; `photoUrl` limitada a `https://` (`CreateTeacherProfileRequest.java:29`). |
| Rate limiting | Ver S-01, S-02, S-03. Cubre login, registro, Google, contacto, mensajes y subida. |
| Errores | `ApiError` uniforme; el handler genérico devuelve un mensaje fijo y registra el detalle solo en el servidor (`GlobalExceptionHandler.java:177-181`). |
| Exposición de datos | DTOs sin email, `passwordHash`, tokens ni coordenadas. `GET /actuator/health` sin detalles. |

### D. Frontend

| Control | Resultado | Evidencia |
|---|---|---|
| XSS | No hay `dangerouslySetInnerHTML`, `innerHTML` ni `eval` en `frontend/src`. React escapa el contenido de bios, mensajes y opiniones. El prerender escapa con `escapeHtml` y neutraliza `<` en el JSON-LD. | grep; `frontend/api/seo.ts:75,471-472` |
| CSP | Meta tag generada en el build, verificada por `check-csp.mjs` (sin `unsafe-inline`/`unsafe-eval` en `script-src`, `object-src 'none'`, `frame-src` limitado). Ver S-06 y S-07. | `frontend/vite.config.ts:13-44`, `frontend/scripts/check-csp.mjs` |
| CSRF | Doble submit con `X-XSRF-TOKEN`; el cliente reintenta una vez ante un 403. | `SecurityConfig.java:124-139`, `frontend/src/api.ts:510-516` |
| Tokens en el navegador | No hay JWT accesible a JavaScript. `localStorage` solo guarda el tema; `sessionStorage` solo el historial de búsqueda. | `ThemeProvider.tsx`, `SearchInput.tsx` |
| Variables `VITE_*` | Solo `VITE_API_URL`, `VITE_GOOGLE_CLIENT_ID` y `VITE_TURNSTILE_SITE_KEY`. Son públicas por diseño, no hay secretos. | `frontend/src/vite-env.d.ts` |
| Dependencias | `npm outdated` muestra solo actualizaciones menores y mayores aplazadas. `npm audit` no se ejecutó (S-09). | `00-project-baseline.md` §6 |
| Rutas protegidas | `isPrivatePath` y `ProtectedRoute`; al cerrar sesión se redirige a la portada. | `App.tsx:134-141` |

### E. Backend

| Control | Resultado |
|---|---|
| Command Injection | No hay ejecución de comandos del sistema. |
| SSRF | No hay peticiones salientes con URL controlada por el usuario. Las URLs de Brevo, Turnstile, Google y S3 vienen de la configuración. |
| Path Traversal | Los archivos se guardan por clave `verification/<UUID>`; el nombre original solo se guarda saneado como metadato (`DocumentContentValidator.java:68-82`). |
| Deserialización | Solo Jackson hacia records/DTO; sin `ObjectInputStream` ni polimorfismo por tipo. |
| CORS | Lista explícita de orígenes (`cors.allowed-origins`), sin comodín, `allowCredentials=true`; cabeceras y métodos acotados (`SecurityConfig.java:155-169`). |
| Cabeceras | `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Permissions-Policy`. Sin HSTS en la aplicación (S-08). |
| Logs | No se loguean contraseñas ni JWT. El enlace de activación y el de contacto solo se escriben con banderas de desarrollo (`AUTH_LOG_ACTIVATION_LINKS`, `MESSAGE_LINK_LOG`, por defecto `false`). Ver S-10. |
| Configuración | `security.csrf.enabled=false` solo se permite en `local`/`test`; el validador lo rechaza en otros perfiles (`ProductionConfigValidator.java:131`). |

### F. Carga de archivos

| Control | Resultado | Evidencia |
|---|---|---|
| Contenido real | Solo PDF, por magic bytes `%PDF-`; no se confía en extensión ni `Content-Type` del cliente. | `DocumentContentValidator.java:26,35` |
| Tipo almacenado | Siempre `application/pdf`, nunca el declarado por el cliente. | `DocumentContentValidator.java:38` |
| Tamaño | 10 MB por archivo (413 al excederlo); 12 MB por request. | `DocumentContentValidator.java:23`, `application.yml` |
| Cantidad y cuota | Máximo 5 archivos y 25 MB por credencial, validado dos veces (la segunda bajo bloqueo de fila). | `VerificationDocumentService.java:33-34,141-149` |
| Nombre | Se descartan rutas y caracteres de control, longitud máxima 255. | `DocumentContentValidator.java:68-82` |
| Ruta de almacenamiento | Clave `verification/<UUID aleatorio>` en bucket privado; sin rutas de usuario. | `S3DocumentStorage.java:88` |
| Acceso | Solo el dueño y los `ADMIN`; sin URLs prefirmadas ni públicas. | `TeacherVerificationController.java:90`, `AdminVerificationController.java:86` |
| Escaneo | ClamAV INSTREAM antes de guardar; infectado → 400, escáner caído → 503. | `VerificationDocumentService.java:81`, `ClamAvDocumentScanner.java` |
| Políglotas / PDF maliciosos | El cabecera `%PDF-` no garantiza que el archivo sea un PDF inocuo (JavaScript embebido, polígloto PDF/ZIP). Sin ClamAV queda sin cubrir. Ver S-04. | — |
| Inmutabilidad | Un documento presentado como evidencia no se puede borrar. | `VerificationDocumentService.java:198-207` |
| Rate limit | `UPLOAD_USER`: 20 por hora y usuario. | `application.yml` |

### G. Secretos y credenciales

- **Archivos versionados:** no se encontraron claves de Brevo (`xkeysib-`), claves AWS, claves privadas ni cadenas de conexión de Neon (búsqueda por patrones en `git ls-files`). **CONFIRMADO** para los patrones buscados.
- `application-local.yml` y los `.env*` están en `.gitignore`; solo se versionan `application-local.example.yml` y `frontend/.env.example`.
- `application.yml` contiene valores por defecto de desarrollo (`jwt.secret`, contraseña de BD `claseya`). El `JwtSecretValidator` impide el secreto de desarrollo fuera de `local`. No hay un validador equivalente para `DB_PASSWORD` (S-11).
- `.github/workflows/ci.yml` define `JWT_SECRET` y contraseñas de la BD efímera del job E2E con valores de CI no sensibles (`ci-only-secret-…`, `claseya`). Riesgo bajo, valores desechables.
- **No se auditó el historial completo de Git** (`git log -p`) ni los secretos configurados en GitHub, Render o Vercel. **SOSPECHA/PENDIENTE** (§9).

### H. Infraestructura

- **HTTPS y cookies:** `ProductionConfigValidator` exige URLs HTTPS, cookies seguras, CORS explícito y los flags de CAPTCHA, escaneo y demo fuera de `local`/`test`. **CONFIRMADO** en el código; los valores reales **PENDIENTES**.
- **Contenedor:** usuario no root, `HEALTHCHECK`, JVM con límite de memoria (`Dockerfile`).
- **Actuator:** solo `health` y `metrics` expuestos; `metrics` solo `ADMIN`.
- **Proxy:** ver S-01.

---

## 4. Hallazgos priorizados

### MEDIUM

#### S-01 — Rate limiting por IP puede colapsar a un solo bucket detrás del proxy (SOSPECHA / configuración)
- **Categoría:** OWASP API4:2023 Unrestricted Resource Consumption; CWE-770.
- **Archivo:** `security/ratelimit/ClientIpResolver.java:19-30`, `security/ProductionConfigValidator.java:145`, `application.yml` (`security.rate-limit.trust-forwarded-headers`, `app.deployment.behind-trusted-proxy`).
- **Evidencia:** `resolve` solo lee `Forwarded`/`X-Forwarded-For` si `trust-forwarded-headers=true`; si no, usa `request.getRemoteAddr()`. En producción el navegador llega a Render a través del proxy de Vercel (`frontend/vercel.json`, ADR-022), así que `getRemoteAddr()` es la IP del proxy.
- **Escenario:** con la bandera en `false`, todos los usuarios comparten un bucket por IP: `REGISTER_IP` permite 20 solicitudes por hora para el sitio entero, y `LOGIN_IP` 30 por minuto. Un solo atacante agota los buckets y bloquea el registro, la activación, la recuperación y el login de todos (denegación de servicio). Con la bandera en `true` sin un proxy que sanee la cabecera, un atacante la falsifica y evade el límite.
- **Impacto:** disponibilidad (registro y login) o evasión de límites.
- **Probabilidad:** Alta si la configuración no se hizo; el valor real es **PENDIENTE**.
- **Recomendación:** confirmar los valores en Render (`BEHIND_TRUSTED_PROXY=true` y `RATE_LIMIT_TRUST_FORWARDED_HEADERS=true`) y que la infraestructura delante (Render) sobrescribe `X-Forwarded-For` con la IP real del cliente en lugar de pasar la que envía Vercel. Si no se puede garantizar, aceptar que el límite por IP no es fiable y apoyarse en límites por cuenta y por destinatario.
- **Test:** prueba de integración con dos clientes de IP distinta con cabecera `X-Forwarded-For` (con la bandera activa y desactivada) que verifique buckets separados y que un valor falsificado en una cadena no controlada no lo evada; prueba manual en producción: enviar 25 registros desde una IP y comprobar que un segundo cliente no queda bloqueado.

#### S-02 — Bloqueo de cuenta por intentos fallidos permite denegar el acceso a una víctima (CONFIRMADO)
- **Categoría:** OWASP API4/API2; CWE-645 (Overly Restrictive Account Lockout).
- **Archivo:** `auth/service/AuthService.java:126-128`, `application.yml` (`login-account`: 5 intentos / 15 min).
- **Evidencia:** `isBlocked(email)` se evalúa antes de autenticar, con un contador por email (existente o no, para no enumerar).
- **Escenario:** un atacante envía 5 logins fallidos con el email de la víctima cada 15 minutos; el login legítimo de la víctima recibe 429 de forma sostenida. Cuesta pocas peticiones por hora.
- **Impacto:** denegación de servicio dirigida contra una cuenta (profesores, administradores).
- **Probabilidad:** Media.
- **Recomendación:** mantener el límite por cuenta pero hacerlo por par (cuenta, IP) o combinarlo con un retardo progresivo, o permitir la entrada con CAPTCHA tras N fallos en lugar de bloquear. Evaluar cada alternativa contra la regla anti-enumeración.
- **Test:** test de integración que realice 5 intentos fallidos desde una IP y compruebe que un login correcto desde otra IP no es bloqueado (o exige CAPTCHA).

#### S-03 — Recuperación de contraseña y reenvío de activación sin límite por destinatario (CONFIRMADO)
- **Categoría:** OWASP API4:2023; CWE-770 / CWE-799 (Improper Control of Interaction Frequency).
- **Archivo:** `security/ratelimit/RateLimitFilter.java:~104-112`; `auth/service/PasswordResetService.java:63-76`; `auth/service/AccountActivationService.java:68`.
- **Evidencia:** `forgot-password`, `resend-activation`, `activate`, `reset-password` y `register` comparten el bucket `REGISTER_IP` (20 por hora y por IP). No hay límite por email destinatario.
- **Escenario:** 20 solicitudes por hora por IP, repartidas entre pocas IPs, llenan la bandeja de una víctima con correos de ClaseYa y consumen la cuota diaria de Brevo (el plan gratuito permite unos 300 correos por día, dato del proveedor, no del repositorio). Con la cuota agotada, ningún usuario recibe su enlace de activación.
- **Impacto:** acoso por correo y denegación del servicio de correo (activación, recuperación, avisos de contacto).
- **Probabilidad:** Media.
- **Recomendación:** limitar por destinatario (por ejemplo 3 por hora por email, silencioso, siempre `202`) y separar el bucket de `forgot-password`/`resend-activation` del de registro; alertar cuando la cuota del proveedor se acerque al máximo.
- **Test:** test de integración que solicite 4 reenvíos para el mismo email desde IPs distintas y verifique que solo se encolan los primeros N sin cambiar el código de respuesta.

#### S-04 — Evidencia sin escanear y validación de PDF limitada a la cabecera (SOSPECHA / configuración)
- **Categoría:** OWASP A04:2021 Insecure Design / A05:2021; CWE-434, CWE-353.
- **Archivo:** `verification/service/NoopDocumentScanner.java`, `DocumentContentValidator.java:35`, `ProductionConfigValidator.java`.
- **Evidencia:** con `SCAN_ENABLED=false` el escáner registra `UNSCANNED` y acepta el archivo. El validador comprueba solo los 5 primeros bytes (`%PDF-`). El validador de arranque exige `SCAN_ENABLED=true` o `ALLOW_UNSCANNED_EVIDENCE=true` (reconocimiento explícito).
- **Escenario:** un profesor malicioso sube un PDF con JavaScript embebido o un archivo políglota (PDF/ZIP, PDF/HTML). Un administrador lo descarga y lo abre con un visor vulnerable.
- **Impacto:** ejecución de código en el equipo del administrador o robo de su sesión, según el visor. La descarga siempre es como adjunto, lo que evita que el navegador lo renderice dentro del origen de la aplicación, pero no protege al visor local.
- **Probabilidad:** Baja a Media (requiere un profesor registrado y un administrador que abra el archivo).
- **Recomendación:** activar ClamAV en producción (`SCAN_ENABLED=true`); documentar a los administradores que abran la evidencia solo en un visor aislado; considerar una validación estructural del PDF (por ejemplo rechazar `/JavaScript`, `/JS`, `/OpenAction`, `/Launch` y cualquier firma ZIP posterior al `%%EOF`). Estado real de ClamAV: **PENDIENTE**.
- **Test:** test que suba un PDF con `/JavaScript` y un archivo con cabecera `%PDF-` seguida de una firma ZIP y verifique el rechazo; test de integración con el `EICAR` contra ClamAV (entorno de pruebas).

### LOW

#### S-05 — La puerta "human check" no se aplica en el backend (CONFIRMADO)
- **Categoría:** OWASP A04:2021; CWE-807 (Reliance on Untrusted Inputs in a Security Decision).
- **Archivo:** `auth/human/HumanCheckService.java`, `HumanCheckController.java`.
- **Evidencia:** `isVerified`/`isRequired` solo se usan en `HumanCheckController`; ningún filtro ni controlador de negocio exige la cookie `CLASEYA_HUMAN`.
- **Escenario:** un bot llama a la API directamente y omite la puerta, que solo existe en la interfaz.
- **Impacto:** el mecanismo no frena el scraping ni el abuso de endpoints públicos; protege solo la experiencia de la página. El registro sí exige el CAPTCHA en el backend (`AuthService.java:72`).
- **Probabilidad:** Alta (trivial), impacto bajo.
- **Recomendación:** documentarlo como UX y no como control de seguridad, o aplicar la cookie como requisito en endpoints sensibles de lectura masiva.
- **Test:** llamada directa a `GET /api/teachers` sin la cookie y comprobar el comportamiento esperado.

#### S-06 — La CSP se entrega por `<meta>` y `img-src` admite cualquier `https:` (CONFIRMADO)
- **Categoría:** OWASP A05:2021; CWE-1021 / CWE-359.
- **Archivo:** `frontend/vite.config.ts:37-38`, `frontend/public/_headers` (solo `frame-ancestors 'none'`).
- **Evidencia:** `style-src` incluye `'unsafe-inline'` e `img-src 'self' data: https:`. La CSP completa depende de una etiqueta `<meta>` (no admite `frame-ancestors`, que sí se entrega por cabecera).
- **Escenario:** un profesor publica una foto desde un servidor propio (`photoUrl` solo exige `https://`, `CreateTeacherProfileRequest.java:29`) y registra las IP y el momento en que cada visitante abre su perfil.
- **Impacto:** fuga de IP y de datos de navegación de los visitantes hacia un tercero; el riesgo de XSS no cambia porque `script-src` es estricto.
- **Probabilidad:** Baja.
- **Recomendación:** servir las fotos desde un almacenamiento propio o restringir `img-src` a los dominios permitidos; entregar la CSP completa por cabecera HTTP.
- **Test:** test del validador que rechace `photoUrl` fuera de la lista de dominios permitidos; prueba E2E que compruebe la cabecera CSP.

#### S-07 — `style-src 'unsafe-inline'` (CONFIRMADO)
- **Categoría:** OWASP A05:2021; CWE-1021.
- **Archivo:** `frontend/vite.config.ts:37`.
- **Impacto:** bajo; permite inyección de estilos si hubiera una inyección de HTML, que no existe hoy.
- **Recomendación:** evaluar un nonce o hashes si las librerías lo permiten.

#### S-08 — Sin HSTS en la aplicación (SOSPECHA)
- **Categoría:** OWASP A05:2021; CWE-319.
- **Evidencia:** no hay coincidencias de `Strict-Transport-Security` en `src/main` ni en `frontend/public/_headers`.
- **Impacto:** depende de que Render y Vercel lo agreguen (Vercel suele hacerlo en sus dominios).
- **Recomendación:** verificar en producción con `curl -I` y, si falta, agregar la cabecera en `_headers`.
- **Test:** comprobación de cabeceras tras el despliegue.

#### S-09 — Dependencias sin análisis de vulnerabilidades en esta auditoría (SOSPECHA)
- **Categoría:** OWASP A06:2021; CWE-1104.
- **Evidencia:** `npm audit` y el análisis de dependencias Maven no se ejecutaron. El CI corre `npm audit --audit-level=high`; Dependabot cubre Maven y npm; `dependency-submission.yml` y `container-scan.yml` existen. Hay dependencias transitivas fijadas manualmente (`commons-compress` 1.26.0, `commons-lang3` 3.18.0 en `pom.xml`).
- **Recomendación:** revisar el último resultado de CI y de Dependabot; confirmar que las fijaciones siguen siendo necesarias.

#### S-10 — Banderas de desarrollo que escriben credenciales en el log (CONFIRMADO)
- **Categoría:** OWASP A09:2021; CWE-532.
- **Archivo:** `notification/service/ConfigurableActivationLinkSender.java:48`, `ConfigurableContactNotificationSender.java:37`.
- **Evidencia:** con `AUTH_LOG_ACTIVATION_LINKS=true` o `MESSAGE_LINK_LOG=true` el enlace de un solo uso, que es una credencial, se escribe en el log. Por defecto ambas están en `false`.
- **Impacto:** cualquiera con acceso a los logs de Render podría activar cuentas o abrir una sesión del profesor. En conversación se indicó que durante las pruebas el enlace se veía en los logs de Render, es decir, la bandera estuvo activa en producción en algún momento (dato del usuario, no verificable en el repositorio).
- **Corrección (2026-11-17, auditoría 06):** esta recomendación ya está implementada: `ProductionConfigValidator.validateSecurityToggles` (`ProductionConfigValidator.java:~108-135`, commit `2e83ebc`) rechaza el arranque con ambas banderas en `true` fuera de los perfiles `local`/`test`. La afirmación original "no bloqueado por el validador" era errónea. Consecuencia: si el enlace apareció en los logs de Render, la instancia corría con perfil `local`/`test` activo (`SPRING_PROFILES_ACTIVE`) o con una imagen anterior a ese commit; verificar en Render (PENDIENTE).
- **Recomendación (original):** agregar estas dos variables al `ProductionConfigValidator` para impedir el arranque con ellas en `true` fuera de `local`/`test`; confirmar que están en `false` y revocar los enlaces emitidos durante ese período (los tokens de activación vencen a las 24 h; los de contacto, a los 7 días).
- **Test:** `ProductionConfigValidatorTest` que falle con cualquiera de las dos banderas activas en un perfil de producción.

#### S-11 — Credenciales de base de datos por defecto en `application.yml` (CONFIRMADO)
- **Categoría:** OWASP A05:2021; CWE-1392 (Use of Default Credentials).
- **Archivo:** `src/main/resources/application.yml` (`DB_PASSWORD:claseya`).
- **Evidencia:** no hay validador de arranque equivalente al de `jwt.secret`. Si `DB_PASSWORD` falta en producción, la aplicación intentaría conectarse con la contraseña de desarrollo.
- **Impacto:** bajo en la práctica (la conexión fallaría contra Neon), pero es un valor por defecto débil.
- **Recomendación:** sin valor por defecto fuera de `local`, o validar el arranque.

#### S-12 — Mensajes de error de validación revelan la política y el estado (INFO/LOW)
- **Archivo:** `common/exception/GlobalExceptionHandler.java:36-55`.
- **Evidencia:** `BadRequestException` devuelve el mensaje del servicio al cliente (por ejemplo "A credential cannot hold more than 5 documents"). Los textos revisados no revelan datos internos.
- **Recomendación:** mantener la regla de que los mensajes de excepción sean aptos para el cliente; añadir un test de contrato.

#### S-13 — Sin revocación individual por `jti` ni vinculación del token (CONFIRMADO, riesgo aceptado)
- **Categoría:** OWASP A07:2021; CWE-613.
- **Evidencia:** la invalidación es por usuario (`session_version`), no por sesión. Un token robado vale hasta 12 h o hasta que el usuario haga logout o cambie la contraseña.
- **Impacto:** acotado: el token está en una cookie `HttpOnly` y no es accesible a JavaScript.
- **Recomendación:** aceptable para el MVP. Si crece, introducir refresh tokens con rotación (ya está en la lista de "futuro" de `docs/architecture/security.md`).

### INFO

- **S-14.** La exención de CSRF depende de la cabecera `Authorization: Bearer` (`BearerAwareCsrfMatcher.java:16-21`) mientras el filtro JWT prioriza la cookie (`JwtAuthenticationFilter.java:70-81`). Un sitio de terceros no puede fijar esa cabecera sin pasar por CORS, y los orígenes permitidos son explícitos, así que no es explotable hoy. Si se agregara un origen permisivo, la protección CSRF quedaría anulada. Recomendación: un test que verifique que una petición con `Authorization: Bearer basura` y cookie válida desde un origen no permitido es rechazada por CORS/CSRF.
- **S-15.** El enlace de contacto (7 días) abre una sesión completa del profesor (`MessageLinkAuthService.java:38-50`). Quien reenvíe o filtre el correo cede la cuenta hasta que se consuma. Es una decisión de producto documentada (REVIEW2-002); el token es de un solo uso.
- **S-16.** El rate limiter falla en modo abierto ante un error interno (`RateLimitFilter.java:~73-77`). Decisión de diseño explícita ("defensa en profundidad").
- **S-17.** Rutas con parámetros de ruta (`;`), barras duplicadas o variaciones de mayúsculas que podrían evadir el `switch` por URI de `RateLimitFilter` quedan rechazadas por `StrictHttpFirewall` de Spring Security antes del filtro. Confirmar con un test.
- **S-18.** `/api/students/**` admite `ADMIN` (`SecurityConfig.java:91`) sin caso de uso evidente. Sin impacto.
- **S-19.** La cookie de sesión no usa el prefijo `__Host-`. Mejora opcional.

---

## 5. Vulnerabilidades por categoría OWASP

**Top 10 (2021):** A01 Broken Access Control — sin hallazgos. A02 Cryptographic Failures — sin hallazgos (HS256 con secreto validado, BCrypt, SHA-256 para tokens). A03 Injection — sin hallazgos. A04 Insecure Design — S-04, S-05. A05 Security Misconfiguration — S-06, S-07, S-08, S-11. A06 Vulnerable Components — S-09. A07 Identification and Authentication Failures — S-02, S-13. A08 Software and Data Integrity — sin hallazgos. A09 Logging and Monitoring — S-10. A10 SSRF — sin hallazgos.

**API Top 10 (2023):** API1 BOLA — sin hallazgos. API2 Broken Authentication — S-02. API3 Broken Object Property Level Authorization — sin hallazgos (DTOs explícitos). API4 Unrestricted Resource Consumption — S-01, S-03. API5 Broken Function Level Authorization — sin hallazgos. API6 Unrestricted Access to Sensitive Business Flows — S-03, S-05. API7 SSRF — sin hallazgos. API8 Security Misconfiguration — S-06 a S-11. API9 Improper Inventory Management — ver nota. API10 Unsafe Consumption of APIs — sin hallazgos.

*Nota API9:* hay documentación desactualizada (R-09 del informe 00), pero no hay endpoints no documentados con riesgo; los controllers coinciden con las reglas de `SecurityConfig`.

---

## 6. Escenarios de explotación evaluados

| Escenario | Resultado |
|---|---|
| Leer o escribir conversaciones ajenas por ID | No explotable: acceso por fila de participante, 404 a terceros. |
| Descargar el documento de otro profesor | No explotable: consulta por `(id, perfil)`; el administrador exige rol. |
| Falsificar un JWT | No explotable con un `JWT_SECRET` válido (≥ 32 bytes) y `alg` fijado por la clave. |
| Escalar a `ADMIN` por registro o por Google | No explotable: bloqueado en ambos flujos. |
| Reutilizar un enlace de activación o de reset | No explotable: `UPDATE` atómico de un solo uso. |
| Enumerar cuentas por registro, login, reset o reenvío | No explotable por contenido de respuesta; ver S-02 para el bloqueo por email. |
| XSS almacenado en bios, mensajes u opiniones | No explotable: escape de React, sin HTML sin sanear. |
| Inyección en el HTML del correo | No explotable: `HtmlUtils.htmlEscape` sobre los datos del usuario (`EmailTemplates.java:72,80,106-109`). |
| CSRF contra el logout o el envío de mensajes | No explotable con cookie `SameSite=Lax` y token `X-XSRF-TOKEN`. |
| Agotar el registro o el correo del sitio | Posible según la configuración: S-01 y S-03. |
| Bloquear a un usuario concreto | Posible: S-02. |
| PDF malicioso hacia el administrador | Posible si ClamAV no está activo: S-04. |

---

## 7. Recomendaciones (en orden)

1. Confirmar y documentar `BEHIND_TRUSTED_PROXY` y `RATE_LIMIT_TRUST_FORWARDED_HEADERS` en Render (S-01).
2. Activar ClamAV o decidir por escrito `ALLOW_UNSCANNED_EVIDENCE`; dar instrucciones de manejo de evidencia a los administradores (S-04).
3. Impedir con el validador de arranque que `AUTH_LOG_ACTIVATION_LINKS` y `MESSAGE_LINK_LOG` queden activos fuera de `local`/`test` (S-10).
4. Agregar límite por destinatario a `forgot-password` y `resend-activation` y separar su bucket (S-03).
5. Revisar el bloqueo por cuenta para que no sea una vía de denegación dirigida (S-02).
6. Restringir los orígenes de las fotos de perfil y completar la CSP por cabecera (S-06).
7. Verificar HSTS en producción (S-08).
8. Ejecutar `npm audit`, el análisis de dependencias Maven y revisar el historial de Git en busca de secretos (S-09, §9).

Los puntos 2 a 6 modifican seguridad o contratos de API: según `AGENTS.md` son **Nivel 2** y requieren spec y aprobación antes de implementarse.

---

## 8. Pruebas necesarias

| ID | Prueba | Tipo |
|---|---|---|
| S-01 | Buckets de IP separados según la bandera de proxy; valor de `X-Forwarded-For` falsificado | Integración |
| S-02 | Login correcto desde otra IP durante un bloqueo de cuenta | Integración |
| S-03 | N reenvíos por email desde IPs distintas encolan como máximo el límite | Integración |
| S-04 | Rechazo de PDF con `/JavaScript` o con firma ZIP; EICAR contra ClamAV | Unitario / integración |
| S-05 | Comportamiento esperado de `GET /api/teachers` sin la cookie humana | Integración |
| S-06 | Rechazo de `photoUrl` fuera de dominios permitidos; cabecera CSP en producción | Unitario / E2E |
| S-08 | `curl -I` en producción comprueba `Strict-Transport-Security` | Manual |
| S-10 | `ProductionConfigValidatorTest` con las banderas de log de enlaces activas | Unitario |
| S-14 | Petición con `Authorization: Bearer` inválido y cookie válida desde un origen no permitido | Integración |
| S-17 | URI con `;`, `//` y mayúsculas sobre rutas limitadas | Integración |
| General | Ejecutar `mvn -B clean verify` y `npm audit --audit-level=high` y registrar el resultado | CI |

La suite actual ya cubre `CsrfIntegrationTest`, `SecurityHeadersIntegrationTest`, `SessionRevocationIntegrationTest`, `RateLimitIntegrationTest`, `CaptchaIntegrationTest`, `GoogleAuthIntegrationTest`, `VerificationApiIntegrationTest` y `ProductionConfigValidator` (según los nombres de `src/test`); no se ejecutaron en esta auditoría y no se revisó el detalle de sus aserciones.

---

## 9. Bloqueantes para producción

No hay bloqueantes de código. Antes de abrir tráfico real se deben cerrar estas **verificaciones obligatorias**:

1. **S-01:** confirmar la configuración de proxy y rate limit en Render y Vercel; sin ella, el registro y el login pueden quedar limitados globalmente.
2. **S-04:** confirmar ClamAV o aceptar explícitamente el riesgo de evidencia sin escanear.
3. **S-10:** confirmar `AUTH_LOG_ACTIVATION_LINKS=false` y `MESSAGE_LINK_LOG=false`, y tratar como expuestos los enlaces que se hayan escrito en los logs de Render.

**No cubierto por esta auditoría (PENDIENTE):** historial completo de Git en busca de secretos (`git log -p`), secretos configurados en GitHub, Render, Vercel, Neon y Brevo, cabeceras y TLS reales de producción, resultado de `npm audit` y del análisis Maven, y contenido real del bucket de evidencia. Tampoco se ejecutaron pruebas dinámicas ni la suite de tests.
