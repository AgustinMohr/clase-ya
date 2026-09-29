# ClaseYa — Workflow de desarrollo

Proceso obligatorio para todo cambio de comportamiento en este repositorio. El agente actúa como
implementador y revisor técnico; el humano (arquitecto/director) aprueba specs, planes y cambios
de arquitectura.

**La profundidad del proceso depende del nivel del cambio** (ver "Niveles de cambio"). El ciclo
completo aplica a features de producto; los cambios chicos y los no funcionales tienen un camino
corto, para no gastar esfuerzo donde no aporta.

## Niveles de cambio

| Nivel | Qué incluye | Proceso exigido |
|-------|-------------|-----------------|
| **0 — No funcional** | docs, tooling, scripts, configuración de entorno/plataforma, typos, copy de UI sin cambio de comportamiento | Sin spec ni plan. Implementación directa. Se reporta qué se tocó y cómo se verificó. |
| **1 — Cambio chico de comportamiento** | bugfix, ajuste de UI, validación, filtro o parámetro acotado, refactor interno sin cambio de contrato | Sin spec formal. Change budget de 3 líneas + test de regresión + verificación determinista. |
| **2 — Feature de producto** | capacidad nueva, endpoint o campo de contrato nuevo, migración, regla de negocio, seguridad o visibilidad | Ciclo completo: spec (`docs/specs/README.md`) + REVIEW humano + PLAN + change budget. ADR si decide arquitectura. |

**Economía de artefactos:** no se produce ningún artefacto (spec, plan, ADR) que el nivel no pida.
En Nivel 0 y 1 el reporte va en el chat, no en archivos.

### Ciclos por nivel

- **Nivel 0**: `IMPLEMENT → VERIFY → DONE`
- **Nivel 1**: `IMPLEMENT → TEST → VERIFY → DONE` (el change budget de 3 líneas reemplaza a SPEC/PLAN/REVIEW)
- **Nivel 2**: ciclo completo (ver "Ciclo")

### Cómo se decide el nivel

1. El agente **propone el nivel** en su primera respuesta, en una línea y con la justificación mínima.
2. El humano **confirma o corrige**. Ante duda entre dos niveles, se elige el **mayor**.
3. Un cambio **sube a Nivel 2 siempre** —aunque el diff sea de tres líneas— si toca: seguridad,
   autenticación/autorización/visibilidad, schema o migraciones, contrato de API pública,
   dependencias nuevas, o una decisión de arquitectura.

### Límites de Nivel 0 y 1

- No pueden introducir **requisitos de producto nuevos**. Si aparece uno: se reporta y se convierte
  en Nivel 2, o queda fuera de alcance.
- No eximen de las reglas 3, 5, 6 y 10 de más abajo (ADR, dependencias, seguridad, ambigüedad
  arquitectónica) ni de la regla de "no silent fixes".
- El comportamiento esperado de un Nivel 1 sin spec se **acuerda en el chat** y queda fijado por el
  test de regresión.

### Ejemplos

| Pedido | Nivel |
|--------|-------|
| Corregir un README o actualizar `comandos.txt` | 0 |
| Cambiar una variable de entorno de despliegue | 0 |
| Un filtro devuelve resultados vacíos (bug) | 1 |
| Corregir el orden o el formato de las tarjetas | 1 |
| "Agregar reseñas de profesores" | 2 |
| Nuevo endpoint o exponer un campo nuevo | 2 |
| Cambiar quién puede ver un perfil | 2 |

## Ciclo

Ciclo completo, exigible a **Nivel 2**. Los ciclos cortos están en "Ciclos por nivel".

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
