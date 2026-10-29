# ADR-028 — Caché corta del usuario autenticado

**Estado:** Propuesto (requiere aprobación; cambia una garantía de seguridad).
**Origen:** `review2.md` PERF-01.

## Contexto

`JwtAuthenticationFilter` recarga el usuario desde la base en **cada** request
(`AppUserDetailsService.loadUserById`), y varios services vuelven a pedir el mismo usuario o su
perfil (`AuthService.me`, `TeacherProfileService.requireByUser`, `ConversationService.start`). Son
2-3 round-trips antes de la lógica de negocio en todas las rutas autenticadas.

La recarga existe por una razón: un usuario `SUSPENDED`/`INACTIVE` pierde el acceso en el request
siguiente (no hay revocación de JWT). Cualquier caché amplía esa ventana.

## Opciones

| Opción | Ventaja | Costo |
|---|---|---|
| **A. No cachear; dejar de re-consultar en los services** | Sin ventana de revocación; elimina 1-2 queries | El filtro sigue consultando 1 vez por request |
| **B. Caffeine 30 s por `sub`** | Quita casi todas las queries del filtro | Un usuario suspendido sigue autenticado hasta 30 s |
| **C. Caffeine + invalidación explícita al cambiar estado** | Ventana casi nula en el camino normal | Requiere publicar eventos desde el admin y un caché compartido si hay N instancias |

## Decisión propuesta

**A ahora** (eliminar las re-consultas redundantes de los services, sin caché) y **medir** antes de
dar el siguiente paso. **B solo después de esa medición y solo con una instancia**: con N instancias
B no es seguro, porque la invalidación en memoria no cruza instancias; allí se necesita C con caché
distribuida, o volver a A.

Si la medición justifica B, con estas condiciones:

1. TTL máximo de 30 s y tamaño máximo acotado (p. ej. 10.000 entradas).
2. El caché guarda solo `id`, `email`, `role`, `status` (sin `passwordHash`).
3. Invalidación en `activateManually`, suspensión/borrado y cambio de rol (aunque B no la necesite,
   deja el camino listo para C).
4. Métrica de hits/misses y un test que documente la ventana: suspender → el request siguiente
   todavía pasa, el request tras el TTL devuelve 401.
5. `Cache-Control` y la documentación de `security.md` deben decir explícitamente que la revocación
   no es instantánea.

## Consecuencias

- + Menos carga de base y latencia en todas las rutas autenticadas.
- + El resto de los services puede leer del principal en vez de re-consultar.
- - Garantía de seguridad relajada: la suspensión tarda hasta 30 s en aplicar. **No es aceptable**
  para un flujo de moderación urgente sin invalidación explícita; por eso la condición 3 es
  obligatoria.
- - Un caché en memoria no se comparte entre instancias (misma limitación que RATE-001).

## Criterios de aceptación

- AC-1: 100 requests autenticadas seguidas generan 1 consulta de usuario (medido con un contador de
  repositorio en test).
- AC-2: suspender una cuenta invalida el caché y el request siguiente recibe 401.
- AC-3: el TTL expira y el usuario se recarga.
- AC-4: el caché está acotado y no crece sin límite con tokens válidos.

## Out of scope

- Revocación de JWT por `jti` (lista de revocación).
- Refresh tokens.
- Redis o caché distribuida antes de tener más de una instancia.
