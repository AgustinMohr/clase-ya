# ClaseYa — Workflow de desarrollo

Proceso obligatorio para TODO cambio de comportamiento en este repositorio. El agente actúa como
implementador y revisor técnico; el humano (arquitecto/director) aprueba specs, planes y cambios
de arquitectura.

## Ciclo

```
SPEC
 ↓
REVIEW
 ↓
PLAN
 ↓
IMPLEMENT
 ↓
TEST
 ↓
VERIFY
 ↓
REVIEW
 ↓
DONE
```

- **SPEC**: toda feature comienza como spec en `docs/specs/active/` con el formato de
  `docs/specs/README.md`.
- **REVIEW**: el humano aprueba spec y, si aplica, ADR. No se implementa sin spec aprobada.
- **PLAN**: el agente declara el **change budget** (scope, archivos esperados, migraciones
  esperadas, dependencias nuevas esperadas) antes de tocar código.
- **IMPLEMENT**: solo lo declarado en el plan. Si se excede o aparece algo no declarado → DETENERSE
  y reportar.
- **TEST**: tests obligatorios para el comportamiento nuevo.
- **VERIFY**: verificación determinista (ver abajo y `docs/testing/testing-strategy.md`).
- **REVIEW**: revisión técnica del cambio contra la spec.
- **DONE**: se cumple el Definition of Done de AGENTS.md y se reporta al humano.

## Reglas

1. No implementar una feature sin spec.
2. No inventar requisitos (todo trazable a la spec).
3. No cambiar arquitectura sin ADR.
4. No hacer migración sin justificarla (nueva `V{n+1}`, nunca editar históricas).
5. No agregar dependencia sin justificarla (motivo documentado).
6. No cambiar seguridad silenciosamente.
7. Tests obligatorios.
8. Verification obligatoria.
9. Si una implementación excede el scope, detenerse.
10. Si existe una ambigüedad arquitectónica importante, reportarla (no adivinarla).

## Verificación determinista

```powershell
mvn -B clean test          # suite completa (unit + integración; requiere Docker Desktop activo)
scripts/test.ps1           # preflight de docker + mvn -B clean test
scripts/verify.ps1         # verificación reproducible, reporta PASS/FAIL
```

Reglas de ejecución: no dejar procesos Java/Maven/Docker en segundo plano; no usar
`spring-boot:run` oculto para "verificar" un build; preferir `mvn test`.

## Roles

- **Humano (arquitecto/director)**: decide specs, alcance, arquitectura y seguridad.
- **Agente**: implementa dentro del budget aprobado, detecta y reporta problemas (nunca "silent
  fixes"), verifica y reporta.

## Fuente de verdad

El repositorio (código, specs, ADRs, docs, scripts) es la única fuente de verdad. Las decisiones
de fases previas están registradas en ADRs y documentación; no dependas de prompts históricos.
