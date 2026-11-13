## Flujo de trabajo y reglas de Git

El proyecto utiliza una estrategia GitFlow simplificada.

### 1. Estrategia de ramas

**Ramas permanentes:**
- `main`: Código estable y listo para producción.
- `dev`: Rama de integración para cambios aprobados.

**Ramas temporales:**

| Prefijo | Propósito | Rama base |
|---|---|---|
| `feat/` | Nuevas funcionalidades | `dev` |
| `fix/` | Corrección de errores | `dev` |
| `refactor/` | Mejoras de código | `dev` |
| `test/` | Pruebas | `dev` |
| `docs/` | Documentación | `dev` |
| `chore/` | Mantenimiento | `dev` |
| `hotfix/` | Correcciones críticas en producción | `main` |

Utilizar nombres descriptivos en kebab-case. Ejemplo: `feat/teacher-search`.

### 2. Antes de realizar cambios

Inspeccionar siempre:

```bash
git status --short --branch
git branch --show-current
git log -5 --oneline
```

Reglas:
- Nunca desarrollar directamente en `main` o `dev`.
- Trabajar únicamente en ramas específicas para cada tarea.
- Si la rama actual es permanente, detenerse y solicitar al usuario que cree o cambie a una rama de trabajo.
- Nunca sobrescribir, descartar ni modificar cambios ajenos a la tarea actual.
- Nunca asumir que el árbol de trabajo está limpio.

### 3. Permisos de Git

**Permitido sin autorización:** Comandos de solo lectura como `status`, `branch`, `log`, `diff`, `show` y `remote -v`.

**Requiere autorización explícita del usuario:**
- Crear o cambiar de rama.
- Preparar archivos (`git add`) y crear commits.
- Obtener o enviar cambios (`pull`, `push` o `fetch`).
- Realizar merges, rebases o cherry-picks.
- Crear o integrar Pull Requests.
- Eliminar ramas o modificar la configuración de Git.

Nunca ejecutar operaciones destructivas como `reset --hard`, `clean -fd`, `push --force` ni comandos que descarten cambios sin autorización explícita.

Nunca agregar todos los archivos indiscriminadamente con `git add .`.

### 4. Flujo de desarrollo

Para cada tarea:

1. **Inspeccionar:** Comprender la implementación existente, la arquitectura y el estado del repositorio.
2. **Planificar:** Identificar archivos afectados, pasos de implementación, riesgos y pruebas necesarias. Obtener aprobación antes de implementar.
3. **Implementar:** Respetar el alcance de la tarea, seguir las convenciones existentes y evitar refactorizaciones ajenas al objetivo o dependencias innecesarias.
4. **Validar:** Ejecutar los tests relevantes de backend y frontend, los builds y `git diff --check`.
5. **Revisar:** Inspeccionar el diff completo para detectar regresiones, secretos, archivos generados y cambios no relacionados.
6. **Informar:** Resumir los cambios, archivos modificados, resultados de las pruebas, riesgos y estado de Git.

Nunca eliminar ni debilitar tests para conseguir que pasen. Nunca afirmar que una validación fue exitosa sin haberla ejecutado.

### 5. Commits

Seguir la convención Conventional Commits:

- `feat:` nuevas funcionalidades.
- `fix:` corrección de errores.
- `refactor:` reestructuración del código.
- `test:` pruebas.
- `docs:` documentación.
- `chore:` mantenimiento.
- `perf:` mejoras de rendimiento.

Ejemplos:
- `feat: add teacher search filters`
- `fix: handle expired JWT sessions`

Crear commits únicamente con autorización explícita del usuario. Revisar los cambios preparados antes de confirmar un commit.

### 6. Integración y releases

- Las ramas de funcionalidades y correcciones se integran en `dev` mediante Pull Requests.
- Utilizar Squash and Merge para las ramas temporales.
- Las versiones de producción siguen el flujo `dev → main` mediante un Pull Request.
- Verificar CI, tests y builds antes de publicar.
- Preferir Merge Commit para integrar `dev → main`.
- Las correcciones críticas de producción se originan en `main` y también deben integrarse en `dev`.

Nunca realizar merges ni pushes de forma autónoma.

### 7. Desarrollo en paralelo

Cuando varios agentes trabajen simultáneamente:
- Utilizar ramas Git y Git Worktrees independientes.
- Nunca compartir un mismo directorio de trabajo entre agentes.
- Evitar modificaciones simultáneas sobre los mismos archivos.
- Coordinar los contratos de API y las dependencias.
- Preferir el desarrollo secuencial cuando el paralelismo agregue complejidad innecesaria.

### 8. Principios fundamentales

1. Preservar todos los cambios existentes del usuario.
2. Nunca modificar directamente `main` o `dev`.
3. Nunca realizar operaciones destructivas de Git de forma autónoma.
4. Nunca crear commits, hacer push o merges sin autorización.
5. Nunca ocultar tests fallidos, conflictos o problemas pendientes.
6. Inspeccionar siempre el diff final.
7. El usuario conserva el control sobre el historial de Git y las publicaciones en producción.