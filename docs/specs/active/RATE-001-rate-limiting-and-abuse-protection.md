# RATE-001 — Rate limiting y protección anti-abuso

**Estado:** Aprobada e implementada (verificación de integración pendiente de Docker)  
**Nivel de cambio:** 2 (seguridad + dependencia nueva)  
**Origen:** hallazgo `SEC-01` de `review.md`

---

## 1. ID

`RATE-001` — Rate limiting y protección anti-abuso para autenticación, contacto, mensajería y subida de evidencia.

---

## 2. Objetivo

Reducir el riesgo de fuerza bruta, credential stuffing, enumeración de cuentas, spam de contactos y abuso de almacenamiento mediante límites de frecuencia por cliente y por cuenta, sin introducir Redis ni infraestructura adicional en esta fase.

---

## 3. Contexto

- `SEC-01` identificó que no existe rate limiting, lockout ni cuotas anti-abuso en login, registro, Google login, creación de conversaciones, envío de mensajes ni subida de documentos (`AuthController`, `ConversationController`, `TeacherVerificationController`).
- El registro devuelve `409` cuando el email ya existe, lo que facilita la enumeración si no hay límites.
- La API es stateless (JWT) y el despliegue actual es un monolito Spring Boot en una instancia; no hay Redis.
- `AGENTS.md` declara Redis fuera de alcance hasta que exista una spec específica. Por eso esta spec implementa un limitador **en memoria, por instancia**, y deja la versión distribuida como trabajo futuro.
- El login ejecuta BCrypt; un atacante puede consumir CPU con intentos repetidos. El límite debe aplicarse antes de llegar al `AuthenticationManager` cuando sea posible.
- La evidencia de verificación tiene topes de 10 MB por archivo y 25 MB por credencial; sin cuota por usuario, un docente puede llenar el almacenamiento.

---

## 4. Actores

| Actor | Rol |
|---|---|
| Visitante / atacante | Envía requests sin autenticar a `/api/auth/**`. |
| Estudiante | Inicia conversaciones y envía mensajes. |
| Profesor | Sube evidencia y responde mensajes. |
| Sistema | Aplica los límites, devuelve `429` y registra el evento sin exponer PII. |
| Operaciones | Configura límites por entorno y puede desactivar el limitador ante una emergencia. |

---

## 5. Requisitos funcionales

Los valores numéricos son **propuestos** y se confirman en REVIEW; deben ser configurables por entorno.

- **RF-1 — Login por IP:** `POST /api/auth/login` permite como máximo **30 intentos por minuto por IP**. El intento 31 devuelve `429`.
- **RF-2 — Login por cuenta:** se cuentan **todos** los intentos fallidos (exista o no el email, esté activo o no) con un máximo de **5 fallos por email normalizado cada 15 minutos**. El 6.º intento devuelve `429` sin ejecutar BCrypt. Un login exitoso reinicia el contador de esa cuenta.
- **RF-3 — Registro por IP:** `POST /api/auth/register` permite **20 requests por hora por IP**.
- **RF-4 — Google login por IP:** `POST /api/auth/google` permite **30 requests por minuto por IP**.
- **RF-5 — Contacto por estudiante:** `POST /api/conversations` permite **10 conversaciones nuevas por hora por usuario autenticado**. La reutilización de una conversación existente cuenta igual como request.
- **RF-6 — Mensajes por usuario:** `POST /api/conversations/{id}/messages` permite **60 mensajes por minuto por usuario**.
- **RF-7 — Subida de evidencia por profesor:** `POST /api/teachers/me/education/{id}/documents` permite **20 subidas por hora por usuario**.
- **RF-8 — Respuesta uniforme:** toda request bloqueada devuelve `429 Too Many Requests` con `ApiError` y header `Retry-After` en segundos (entero, mínimo 1, máximo 3600). El mensaje es genérico y no indica qué límite se agotó.
- **RF-9 — Configuración:** todos los límites y la activación se leen de propiedades (`security.rate-limit.*`) con overrides por variables de entorno. Valores inválidos (negativos, cero, ventanas no positivas) impiden el arranque (fail-fast).
- **RF-10 — Almacenamiento acotado:** el estado vive en memoria, con un máximo configurable de claves (por defecto 50.000), TTL de inactividad (por defecto 30 minutos) y limpieza periódica. Si se alcanza el máximo, se descarta la entrada más antigua; nunca se permite crecimiento sin límite.
- **RF-11 — Privacidad:** las claves se derivan con SHA-256 sobre un salt aleatorio por proceso. No se persisten IPs ni emails, y los logs no incluyen la IP/email en claro (solo el scope y un hash truncado).
- **RF-12 — IP del cliente:** por defecto se usa `request.getRemoteAddr()`. Detrás de un proxy confiable se puede activar `security.rate-limit.trust-forwarded-headers=true`; en ese caso se toma el primer valor válido de `X-Forwarded-For`/`Forwarded`. Activar esta opción sin un proxy que sanee el header permite spoofing y debe documentarse como requisito de despliegue.
- **RF-13 — Falla del limitador:** si la evaluación lanza una excepción inesperada, la request se permite y se registra `ERROR` (fail-open). El limitador es defensa en profundidad; no debe convertirse en un punto único de falla. Esta decisión se documenta en el ADR.
- **RF-14 — Interruptor de emergencia:** `security.rate-limit.enabled=false` desactiva todos los límites. El default es `true`; el flag existe solo para incidentes operativos y debe quedar auditado en configuración.
- **RF-15 — Sin efecto en lecturas:** los `GET` públicos, la búsqueda de profesores, la lectura de conversaciones y la descarga de documentos no se limitan en esta fase.

---

## 6. Requisitos no funcionales

- **Rendimiento:** evaluación O(1) en memoria, sin llamadas a base de datos ni red; objetivo p99 < 5 ms por request limitada.
- **Concurrencia:** el store debe ser thread-safe y tolerar requests concurrentes sobre la misma clave sin doble conteo ni corrupción.
- **Escalabilidad:** los límites son **por instancia**. Con N instancias, el límite efectivo puede ser hasta N veces el configurado. Se documenta como limitación aceptada del MVP; la versión distribuida requiere Redis (spec/ADR futura).
- **Determinismo de tests:** el cálculo de refill debe poder testearse con un reloj inyectable o con una abstracción de tiempo; no se permiten `Thread.sleep` en tests.
- **Memoria:** el store no debe crecer sin límite bajo un ataque con IPs aleatorias.
- **Configuración:** defaults seguros en `application.yml`; overrides por entorno (`RATE_LIMIT_*`).
- **Observabilidad mínima:** log `WARN` por request bloqueada (scope, ruta, hash truncado) y `ERROR` si falla la evaluación. Métricas/Actuator quedan para `OPS-01`.

---

## 7. Seguridad

- **Autenticación:** los límites se aplican antes de BCrypt en login y antes de crear la cuenta en registro.
- **Anti-enumeración:** el lockout por email se aplica también a emails inexistentes; un `429` no debe permitir inferir si la cuenta existe.
- **Autorización:** los límites por usuario usan `CurrentUser.id()`; nunca un id enviado por el cliente.
- **Ownership:** el limitador no reemplaza las validaciones de participante/dueño existentes; solo agrega una capa previa.
- **Privacidad:** no se guardan IPs/emails crudos ni se loguean; claves hasheadas con salt por proceso.
- **Proxy:** `trust-forwarded-headers` debe permanecer en `false` si la aplicación puede recibir tráfico directo. En Render/Railway se activa solo si el proxy de la plataforma sobrescribe el header.
- **Respuesta:** `429` genérico, sin detalles del límite, sin `Retry-After` negativo y sin stack traces.
- **No regresión:** no se modifican roles, visibilidad, ownership, JWT, CORS ni CSRF.

---

## 8. Invariantes

1. Una request bloqueada no llega al controller/service y no muta datos.
2. Un login exitoso reinicia el contador de fallos de esa cuenta.
3. Un `429` nunca revela si el email existe.
4. No se persiste ni se loguea una IP/email en claro.
5. El store nunca supera el máximo configurado de claves.
6. Los límites se evalúan de forma consistente para requests concurrentes.
7. Los `GET` públicos y las lecturas autenticadas no se ven afectados.
8. La desactivación por configuración es explícita y no ocurre por defecto.

---

## 9. API contract

No se agregan endpoints ni campos de request/response. Se agrega un nuevo status posible en los endpoints listados:

```http
HTTP/1.1 429 Too Many Requests
Retry-After: 42
Content-Type: application/json

{
  "timestamp": "2026-10-27T18:00:00Z",
  "status": 429,
  "error": "Too Many Requests",
  "message": "Too many requests. Try again later.",
  "path": "/api/auth/login"
}
```

- El `Retry-After` es el tiempo estimado hasta que se pueda consumir un token, redondeado hacia arriba a segundos.
- Los status existentes (`200`, `201`, `204`, `400`, `401`, `403`, `404`, `409`, `413`) no cambian por debajo del límite.
- El frontend ya muestra `ApiError.message`; no se requiere cambio de contrato.

---

## 10. Datos afectados

- **Sin migraciones.** No se agregan tablas ni columnas.
- **Sin persistencia.** El estado del limitador es efímero y se pierde al reiniciar.
- **Sin cambios de schema.** Flyway no se toca.

---

## 11. Criterios de aceptación

| ID | Escenario | Resultado esperado |
|---|---|---|
| CA-1 | 30 logins desde la misma IP en un minuto | 200/401 según credenciales; el 31.º responde 429 |
| CA-2 | 5 fallos de login para el mismo email (exista o no) | el 6.º responde 429 sin ejecutar BCrypt |
| CA-3 | Login exitoso tras fallos por debajo del límite | el contador de la cuenta se reinicia |
| CA-4 | 20 registros desde la misma IP en una hora | el 21.º responde 429 |
| CA-5 | 10 creaciones de conversación por el mismo estudiante en una hora | la 11.ª responde 429 |
| CA-6 | 60 mensajes por el mismo usuario en un minuto | el 61.º responde 429 |
| CA-7 | 20 subidas de evidencia por el mismo profesor en una hora | la 21.ª responde 429 |
| CA-8 | Misma cantidad de requests desde otra IP | no se ve afectada |
| CA-9 | `GET /api/teachers`, `/api/teachers/{id}`, `/api/availability`, lecturas de conversaciones | no se limitan |
| CA-10 | Request bloqueada | body `ApiError` genérico + `Retry-After` >= 1 |
| CA-11 | Email inexistente vs existente | ambos pueden llegar a 429; el status no revela existencia |
| CA-12 | Store bajo 60.000 claves | no supera el máximo; las entradas viejas se descartan |
| CA-13 | Configuración inválida (límite 0/negativo) | el contexto no arranca con un mensaje claro |
| CA-14 | `security.rate-limit.enabled=false` | no se aplican límites |
| CA-15 | Suite existente | sigue verde; los tests de rate limiting usan límites bajos y un contexto dedicado |

---

## 12. Out of scope

- Redis o cualquier store distribuido.
- CAPTCHA, pruebas de bot, reputación de IP o WAF.
- Protección DDoS a nivel de red.
- Límites globales por tenant/organización.
- Endpoint/UI de desbloqueo manual de cuentas.
- Cambiar la respuesta `409` del registro (enumeración por registro) más allá del rate limit.
- Métricas/Actuator y dashboards (`OPS-01`).
- Rate limiting de endpoints de lectura o del catálogo.

---

## 13. Definition of Done

- [ ] Spec aprobada en REVIEW y ADR `ADR-017` aceptado.
- [ ] Dependencia `com.bucket4j:bucket4j_jdk17-core` justificada y fijada en `pom.xml`.
- [ ] Implementación en `com.claseya.security.ratelimit` con store acotado y claves hasheadas.
- [ ] `SecurityConfig` registra el filtro en el orden correcto.
- [ ] `AuthService` integra el lockout por cuenta y el reset en login exitoso.
- [ ] Tests unitarios (bucket/store/IP/lockout) y de integración (429, `Retry-After`, no efecto en GETs, reset).
- [ ] `mvn -B clean test` en verde (con Docker disponible).
- [ ] `docs/architecture/security.md` y `README.md` actualizados (dejan de listar “sin rate limiting” como pendiente).
- [ ] Sin PII en logs, sin migraciones, sin cambios de contrato más allá del nuevo `429`.
- [ ] Reportado al humano para review final; sin commit automático.

---

## Anexo A — Decisión propuesta y alternativas

**Decisión propuesta:** usar **Bucket4j `bucket4j_jdk17-core` 8.21.0** (Apache-2.0, sin dependencias transitivas) con un store en memoria acotado, sin Redis ni Caffeine.

**Alternativas consideradas:**

| Alternativa | Por qué no ahora |
|---|---|
| Redis + Bucket4j distribuido | Redis está fuera de alcance por `AGENTS.md` y agrega infraestructura/operación; queda para una spec futura. |
| Limitador propio desde cero | Más superficie de bugs de concurrencia/tiempo; Bucket4j ya resuelve el token bucket y el cálculo de refill. |
| Solo proxy/WAF | Útil como defensa en profundidad, pero no reemplaza límites por cuenta (email/usuario) ni queda versionado en el repo. |
| Caffeine para el store | Agrega otra dependencia; el store acotado con TTL/cleanup alcanza para el MVP. |

**Limitación aceptada:** los límites son por instancia. Si el despliegue escala horizontalmente, el límite efectivo se multiplica; se documenta en el ADR y se revisa cuando exista Redis.

---

## Anexo B — Change budget propuesto (para PLAN)

- **Scope:** limitador en memoria para login, registro, Google login, creación de conversaciones, mensajes y subida de evidencia; sin cambios de UI salvo el `429` que ya muestra el cliente; sin Redis.
- **Archivos esperados:** `pom.xml`, `application.yml`, `SecurityConfig.java`, `AuthService.java`, nuevos `security/ratelimit/*`, tests unitarios/integración, `docs/architecture/decisions/ADR-017-rate-limiting-in-memory.md`, `docs/architecture/security.md`, `README.md`.
- **Migraciones esperadas:** ninguna.
- **Dependencias nuevas esperadas:** `com.bucket4j:bucket4j_jdk17-core:8.21.0` (token bucket; Apache-2.0; sin transitivas).
- **Nivel:** 2. Requiere REVIEW de esta spec y del ADR antes de implementar.

