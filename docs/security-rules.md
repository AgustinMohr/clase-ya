## Reglas de seguridad
- La identidad siempre sale del `SecurityContext` vía `security.CurrentUser`. Nunca aceptes
  `userId`/`studentId`/`senderId`/`participantIds` del cliente para decidir *quién* actúa.
- Contraseñas: solo BCrypt; nunca guardes ni loguees texto plano. Nunca expongas `passwordHash`.
- JWT sin estado (`jwt.secret`, `jwt.expiration`); las cuentas **PENDING/INACTIVE/SUSPENDED no
  pueden autenticarse** (solo `ACTIVE`). La visibilidad pública de un profesor exige
  `verificationStatus = VERIFIED` **y** `user.status = ACTIVE`; profesores ocultos → 404.
- Roles: `ROLE_STUDENT/ROLE_TEACHER/ROLE_ADMIN` (un único punto de mapeo en `AppUserDetails`).
  ADMIN no puede auto-registrarse. Solo STUDENT gestiona favoritos e inicia conversaciones.
- Nunca expongas emails, direcciones, coordenadas exactas, JWT/tokens ni hashes en DTOs públicos o
  personales.
- CORS restringido a `cors.allowed-origins` (`FRONTEND_URL`); sin wildcard.
- Los secretos vienen del entorno (`DB_*`, `JWT_SECRET`, `JWT_EXPIRATION`, `FRONTEND_URL`).
  `application.yml` contiene defaults **solo de desarrollo** — nunca los trates como secretos de
  producción.
- Los fallos de login devuelven un único mensaje genérico (sin enumeración de cuentas).
- Quien no participa en una conversación recibe **404** (no se revela que la conversación existe).