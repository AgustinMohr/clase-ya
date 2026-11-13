## Definition of Done
Un cambio está terminado solo si se cumplen todas:

- [ ] Impulsado por el nivel correcto de cambio: **Nivel 2** → spec aprobada; **Nivel 1** → change
      budget + test de regresión; **Nivel 0** (docs/tooling) → sin spec.
- [ ] No se inventó ningún requisito: todo sale de la spec (Nivel 2) o de lo acordado en el chat
      (Nivel 1).
- [ ] Arquitectura/seguridad/schema/API/contratos sin cambios más allá del change budget aprobado.
- [ ] El comportamiento nuevo tiene tests (Nivel 1 y 2) y `mvn -B clean test` está en verde cuando
      el cambio toca código.
- [ ] Migraciones (si las hay) como `V{n+1}` nueva con justificación.
- [ ] Docs afectados actualizados (API, ADR cuando cambia una decisión).
- [ ] El cambio no excede el scope declarado (ver Change Budget).
- [ ] Reportado al humano (arquitecto/director). El agente nunca mergea ni publica por su cuenta.