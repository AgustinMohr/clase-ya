# Load smoke con k6 (TEST-01)

Presupuesto de rendimiento para los dos caminos de lectura más calientes del directorio:

| Escenario | Request | Presupuesto inicial |
|---|---|---|
| `search` | `GET /api/teachers?page=0&size=20` | `p95 < 800 ms` |
| `inbox` | `GET /api/conversations?page=0&size=20` | `p95 < 800 ms` |

Además, `http_req_failed` debe quedar por debajo del **1 %**. Los umbrales son un punto de partida
para el smoke; se ajustan con el dataset real (el objetivo de la revisión es un fixture de ~5.000
profesores).

## Dependencia

k6 se ejecuta desde la imagen oficial **`grafana/k6`** de Docker. No agrega dependencias Maven ni
npm. El runner usa `grafana/k6:latest` por defecto y acepta `-K6Image` para fijar una versión.

Alternativa sin Docker: instalar k6 nativo (`winget install k6` o el binario oficial) y ejecutar
`k6 run scripts/load/search-and-inbox.js` con las mismas variables de entorno.

## Cómo correrlo

1. Levantar Postgres:

   ```powershell
   docker start claseya-pg
   ```

2. Levantar el backend con el perfil local (la semilla demo queda activa):

   ```powershell
   mvn spring-boot:run "-Dspring-boot.run.profiles=local" "-Dspring-boot.run.jvmArguments=-Duser.timezone=UTC"
   ```

3. En otra terminal:

   ```powershell
   scripts\load.ps1 -Vus 10 -InboxVus 5 -Duration 30s
   ```

   El runner verifica `/actuator/health`, traduce `localhost` a `host.docker.internal` para el
   contenedor de k6 y propaga el exit code (k6 devuelve distinto de cero si falla un umbral).

   Para un target que ya está corriendo:

   ```powershell
   scripts\load.ps1 -BaseUrl https://staging.example.com -Vus 20 -InboxVus 10 -Duration 1m
   ```

## Sesión y datos

- `setup()` hace **un solo login** (`student@claseya.dev` / `Password123` por defecto) y reutiliza
  la cookie `CLASEYA_SESSION` en todas las VUs. No se loguea por VU: eso mediría el rate limiter de
  login y no los reads.
- `search` es público; `inbox` usa la cookie de la sesión creada en `setup()`.
- El target debe tener conversaciones para el usuario de carga si se quiere medir una bandeja con
  datos. El demo local ya trae conversaciones de las corridas E2E.
- Para credenciales/entorno distintos: `-Email` y `-Password` en el runner o `EMAIL`/`PASSWORD` en
  k6. Nunca commitear credenciales reales.

## Variables

| Variable | Default | Uso |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | Target |
| `VUS` | `10` | VUs del escenario `search` |
| `INBOX_VUS` | `5` | VUs del escenario `inbox` |
| `DURATION` | `30s` | Duración de cada escenario |
| `EMAIL` / `PASSWORD` | demo local | Login de `setup()` |

## Resultado

k6 imprime el resumen con `p95`/`p99` por escenario y falla si se supera un umbral. El exit code se
propaga al runner y a CI. Este smoke no corre en cada PR (necesita un target con dataset); se usa
contra staging o el entorno local con el fixture grande.
