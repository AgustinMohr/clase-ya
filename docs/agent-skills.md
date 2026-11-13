## Skills del agente
Antes de comenzar una tarea, inspecciona las habilidades instaladas disponibles y utiliza las relevantes cuando corresponda.

Usa las habilidades **bajo demanda**, no todas a la vez:

- Funcionalidad nueva/compleja → `spec-driven-development`
- Planificación/desglose → `planning-and-task-breakdown`
- Implementación en múltiples archivos → `incremental-implementation`
- Pruebas/nuevo comportamiento → `test-driven-development`
- Error/fallo en compilación o prueba → `debugging-and-error-recovery`
- Frontend/UI/UX → `frontend-ui-engineering`
- Revisión de código/refactorización → `code-review-and-quality`
- Seguridad/autenticación/cargas/dependencias → `security-and-hardening`
- Reducción extrema de tokens / Respuestas ultracortas → `caveman`

Reglas:

1. Carga únicamente las habilidades relevantes para la tarea actual para minimizar el uso de contexto/tokens.
2. Sigue el flujo de trabajo y los requisitos de verificación de la habilidad seleccionada.
3. No dupliques las instrucciones de las habilidades en este archivo.
4. Si se aplican varias habilidades, utiliza la combinación útil más pequeña.
5. Si no estás seguro de si una habilidad aplica, inspecciona su `SKILL.md` antes de continuar.
6. No afirmes que se usó una habilidad a menos que sus instrucciones se hayan cargado/leído realmente.
7. Las habilidades complementan las especificaciones del proyecto, las ADR, `AGENTS.md` y la Definición de Hecho (DoD); no las anulan.
8. Nunca omitas las pruebas del proyecto, la validación o los requisitos de seguridad solo porque una habilidad no los mencione.