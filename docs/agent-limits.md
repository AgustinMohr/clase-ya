## Límites del agente / prohibiciones
- Nunca implementes una feature de producto (Nivel 2) sin spec aprobada.
- Nunca cambies la arquitectura sin un ADR.
- Nunca crees/modifiques una migración sin justificación, y nunca edites las históricas.
- Nunca agregues una dependencia sin justificación (motivo documentado).
- Nunca cambies el comportamiento de seguridad en silencio.
- Nunca expongas secretos; nunca commitees credenciales reales.
- Nunca rehagas una fase que ya funciona "para mejorarla" dentro de una tarea no relacionada.
- Fuera de alcance hasta que exista spec: bookings/Calendly, reviews, notificaciones, pagos,
  WebSockets, frontend, Elasticsearch, Redis, PostGIS, microservicios.
- Nunca modifiques el backend desde una tarea exclusivamente de frontend.
- Nunca cambies contratos de API para acomodar el frontend sin aprobación.
- Nunca implementes componentes visuales fuera del Design System si pueden ser reutilizables.
- Nunca agregues una librería de UI completa (Material UI, Ant Design, Chakra, etc.) sin aprobación explícita.