# ADR-017 — Rate limiting en memoria con Bucket4j

**Estado:** Aceptado (RATE-001, implementado)  
**Fecha:** 2026-10-27

## Contexto

La auditoría `SEC-01` detectó ausencia de rate limiting, lockout y cuotas anti-abuso en login,
registro, Google login, contacto, mensajería y subida de evidencia. El proyecto es un monolito
Spring Boot desplegado en una instancia, sin Redis. `AGENTS.md` declara Redis fuera de alcance hasta
que exista una spec específica, y agregar infraestructura distribuida para el MVP sería
desproporcionado.

## Decisión

- Usar **Bucket4j** (`com.bucket4j:bucket4j_jdk17-core:8.21.0`, Apache-2.0, sin dependencias
  transitivas) con **token buckets en memoria**.
- Mantener un store acotado (`maxEntries`, TTL de inactividad y limpieza periódica) para que un
  ataque con claves aleatorias no agote la memoria.
- Aplicar límites por **IP** (login, registro, Google) y por **usuario autenticado** (contacto,
  mensajes, upload), con un lockout por **email normalizado** para login.
- Hashear IPs/emails con SHA-256 + salt aleatorio por proceso; no persistir ni loguear valores
  crudos.
- Responder `429` con `ApiError` genérico y `Retry-After`.
- **Fail-open** si la evaluación falla: el limitador es defensa en profundidad y no debe ser un
  punto único de falla. La configuración inválida sí falla al arrancar.
- Dejar un interruptor operativo `RATE_LIMIT_ENABLED` (default `true`).

## Consecuencias

- **+** Sin infraestructura ni dependencias de red adicionales; el limitador es rápido (O(1)) y
  testeable de forma determinista.
- **+** Protege CPU de BCrypt y reduce fuerza bruta, enumeración, spam de contactos y abuso de
  almacenamiento.
- **+** No expone PII en logs ni en el store.
- **−** Los límites son **por instancia**: con N réplicas el límite efectivo se multiplica. No hay
  coordinación entre nodos.
- **−** El estado es efímero: un reinicio resetea los buckets.
- **−** No reemplaza CAPTCHA, WAF ni protección DDoS.
- **−** El fail-open prioriza disponibilidad; un bug del limitador permitiría tráfico no limitado
  hasta que se detecte el `ERROR`.

## Futuro

Cuando el despliegue escale horizontalmente o se incorpore Redis (con spec/ADR propia), migrar el
store a Bucket4j distribuido manteniendo los mismos scopes, límites y contrato HTTP. Evaluar también
métricas/Actuator, CAPTCHA y reglas en el proxy/WAF como capas adicionales.
