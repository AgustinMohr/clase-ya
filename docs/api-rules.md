## Reglas de API
- REST bajo `/api`. Las respuestas son **DTOs (records)**, nunca entidades JPA.
- Errores con la forma compartida `ApiError` vía `common.exception.GlobalExceptionHandler`
  (400/401/403/404/405/409 según corresponda). Nunca filtres stack traces/SQL.
- La paginación reutiliza la forma genérica (`teacher.dto.SearchResultPage`); las opciones de orden
  deben estar en whitelist (nunca nombres de columna arbitrarios del cliente).
- Solo los controllers mapean HTTP; las reglas de negocio viven en services; el acceso a datos vive
  en repositories. Sin lógica de negocio en controllers/entidades.