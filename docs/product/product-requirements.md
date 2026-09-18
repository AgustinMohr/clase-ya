# Requisitos de producto — ClaseYa

**Estado:** Aprobado por el director (2026-09-02) como referencia funcional de producto.
**Alcance:** documento de producto; no es una spec técnica (las specs viven en `docs/specs/`).

> **Nota de dirección:** ClaseYa se define como un **directorio + contacto seguro** entre alumnos y
> profesores particulares, **sin reservas ni agenda online**. Como consecuencia, la dirección de
> reservas/agenda de AVAIL-001 y BOOK-001 (incluido Calendly) **queda obsoleta como rumbo de
> producto**; esos documentos se conservan como historia y no deben implementarse como rumbo actual.

---

## 1. Visión y problema

ClaseYa conecta a **estudiantes de todas las edades** (y a sus **padres/madres/tutores**) con
**profesores particulares** de la materia y la zona que necesitan. Es un portal simple: el alumno
encuentra al profesor ideal y **contacta** para acordar las clases; la coordinación de
fecha/hora/pago la realizan las partes. ClaseYa **no** reserva, no cobra y no agenda por el usuario,
lo que elimina la complejidad y los riesgos de "reservas maliciosas".

El modelo de referencia es el de plataformas consolidadas de clases particulares
(tusclasesparticulares, Superprof): gran catálogo de anuncios, buscador por materia, tarjetas de
profesor con precio/valoración/modalidad y un **contacto** protegido.

## 2. Público objetivo y usuarios

| Usuario | Qué hace |
|---|---|
| **Visitante** (sin cuenta) | Busca por materia y explora perfiles; para contactar necesita registrarse. |
| **Estudiante** | Primaria, secundaria, terciario/universidad o adulto que aprende (idiomas, informática, etc.). |
| **Padre / madre / tutor** | Gestiona la búsqueda y el contacto para un menor. **Los menores no crean la cuenta; la maneja el adulto responsable.** |
| **Profesor** | Publica su anuncio, responde contactos, recibe y responde opiniones. |
| **Administración** | Modera anuncios, opiniones y reportes; gestiona cuentas. |

## 3. Experiencia de primer ingreso

1. **Landing** con un buscador grande por **materia** (autocompletado: matemática, física, álgebra,
   lengua, inglés, …) y un toggle de **modalidad (presencial / online / ambas)**.
2. **Resultados**: tarjetas de profesor con
   - foto;
   - nombre;
   - **rating** (estrellas + nº de opiniones);
   - **ubicación/zona** (ciudad o barrio; sin dirección exacta pública);
   - **precio por hora**;
   - **modalidad** (presencial a domicilio / en casa del profe / online);
   - **disponibilidad** (mañana / tarde / noche).
3. **Perfil público del profesor**: bio/anuncio, materias y niveles, educación/experiencia,
   modalidades, zona, precio, disponibilidad, opiniones y el botón **Contactar**.

## 4. Cuentas y autenticación

- **Registro e inicio de sesión con Google OAuth (obligatorio)**. Rol a elegir al registrarse:
  **STUDENT** o **TEACHER**.
- Política de menores: la cuenta de un menor la crea/opera su adulto responsable (términos claros).
- Un usuario puede tener perfil de estudiante y, si corresponde, de profesor (a decidir en detalle
  más adelante).

## 5. Perfil del profesor (su anuncio)

- Foto, nombre (visible), **bio/anuncio** (texto libre, no se parsea).
- **Materias** que enseña (+ nivel/edad objetivo cuando aplique).
- **Precio por hora** (moneda ARS).
- **Modalidad(es)**: presencial (a domicilio y/o en su lugar) y **online**.
- **Ubicación/zona** (ciudad/barrio; sin dirección exacta ni coordenadas públicas).
- **Disponibilidad semanal simplificada**: por día, **mañana / tarde / noche**.
- Educación/títulos (opcional, editable).
- Rating calculado desde las **opiniones**.

## 6. Perfil del estudiante

- Datos mínimos (nombre visible; ciudad opcional).
- Puede **guardar profesores como favoritos** y **enviar contactos**.
- Historial de sus contactos y conversaciones.

## 7. Contacto seguro (núcleo del producto)

**Objetivo:** conectar a ambas partes **sin exponer** emails, teléfonos ni direcciones, y sin crear
reservas automáticas.

**Flujo de "Contact request" (v1):**
1. El estudiante (autenticado con Google) elige un profesor → botón **Contactar**.
2. Formulario breve: materia de interés, mensaje corto (opcional), **franja** preferida
   (mañana/tarde/noche) y rol ("soy estudiante" / "soy padre/madre de un estudiante de X").
3. ClaseYa crea una **solicitud de contacto** y un **hilo de conversación interna**.
4. ClaseYa **notifica al profesor por email del sistema** (dirección `@claseya` / relay; nunca la
   dirección del alumno) con un **enlace firmado de un solo uso** que abre la conversación.
5. La comunicación continúa **dentro de ClaseYa (mensajería asíncrona)**. Los correos son solo
   avisos con deep-link; **jamás** se comparten direcciones reales entre las partes.

**Reglas y anti-abuso:**
- Un "contacto" **no reserva** nada ni obliga a ninguna de las partes.
- Límites de contacto (por usuario/día y por profesor) + rate-limit.
- OAuth obligatorio para actuar; sin cuentas anónimas.
- Posibilidad de **reportar** contactos/profesores; moderación.
- (Futuro documentado) chat en tiempo real/videollamada y coordinación de clase solo cuando ambas
  partes lo acuerden.

**Privacidad:** nunca se expone email, teléfono, dirección exacta ni coordenadas en perfiles,
resultados ni conversaciones ajenas.

## 8. Opiniones y rating

- Solo un usuario con una **relación de contacto aceptada** puede opinar sobre un profesor
  (mitiga reseñas falsas); **una opinión por profesor por usuario**.
- El profesor puede **responder** la opinión.
- **Moderación/admin** y **reportes** de opiniones.
- El **rating promedio** se recalcula con cada opinión publicada.

## 9. Seguridad y privacidad

- Identity siempre desde el `SecurityContext`/`CurrentUser`; nunca confiar en IDs del cliente.
- Roles `ROLE_STUDENT/ROLE_TEACHER/ROLE_ADMIN`; API stateless (JWT) + CORS.
- DTOs nunca entidades; respuestas públicas sin datos sensibles.
- Menores gestionados por su adulto responsable.
- Logging sin secretos ni datos personales innecesarios.

## 10. Fuera de alcance (para mantener el producto simple)

- **Reservas y agenda online** (ni Calendly ni disponibilidad por fecha concreta).
- **Pagos** procesados por la plataforma (el acuerdo de pago es entre partes).
- **Chat en tiempo real / WebSockets** y **videollamadas integradas** (futuro).
- Anuncios pagos / créditos / destacados (futuro).
- Aplicaciones nativas (primero web).

## 11. Post-MVP (funcionalidades importantes, documentadas)

Notificaciones push/email más ricas; perfil de profesor "verificado" (documentos); búsqueda por
nivel/edad y por franja; múltiples anuncios por profesor; destacados; app móvil.

## 12. Decisiones pendientes para detalle

- Precio: ¿se muestra siempre o se oculta tras login? (propuesta: visible, como referencia).
- Review: ¿"relación aceptada" = el profesor confirma que hubo contacto/primera clase?
- ¿Un usuario puede tener ambos roles (STUDENT y TEACHER)?
- Moderación de anuncios: aprobación previa vs reportes posteriores.
