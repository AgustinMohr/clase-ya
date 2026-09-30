# ClaseYa — seed demo (solo desarrollo)
#
# Vía MANUAL alternativa. El backend aplica este mismo SQL al arrancar
# (DemoDataSeeder, con app.demo-seed.enabled=true), así que normalmente no hace
# falta correrlo: sirve para poblar la base sin levantar la aplicación.
#
# El SQL vive en src\main\resources\db\seed\demo-data.sql (classpath) y es
# idempotente y no destructivo: se puede correr N veces sin duplicar registros
# ni borrar datos existentes. NO es una migración Flyway.
#
# Requisitos: Docker Desktop activo y el contenedor `claseya-pg` corriendo.
# Uso:  scripts\seed-demo.ps1
[CmdletBinding()]
param(
    [string]$Container = 'claseya-pg',
    [string]$DbUser = 'claseya',
    [string]$DbName = 'claseya'
)

$ErrorActionPreference = 'Stop'

$sqlFile = Join-Path $PSScriptRoot '..\src\main\resources\db\seed\demo-data.sql'
if (-not (Test-Path -LiteralPath $sqlFile)) {
    Write-Host "No se encontró el archivo de seed: $sqlFile" -ForegroundColor Red
    exit 1
}

docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host 'Docker Desktop no está disponible.' -ForegroundColor Yellow
    exit 1
}

# El archivo se copia dentro del contenedor y se ejecuta desde ahí: así psql lee
# bytes exactos y no hay riesgo de que PowerShell altere el encoding (acentos).
$inContainer = '/tmp/claseya-demo-data.sql'
docker cp $sqlFile "${Container}:$inContainer"
if ($LASTEXITCODE -ne 0) {
    Write-Host "No se pudo copiar el SQL al contenedor '$Container'. ¿Está corriendo?" -ForegroundColor Red
    Write-Host "Levantalo con:  docker start $Container" -ForegroundColor Yellow
    exit $LASTEXITCODE
}

Write-Host 'Cargando datos demo...' -ForegroundColor Cyan
docker exec -e PGCLIENTENCODING=UTF8 $Container psql -U $DbUser -d $DbName -v ON_ERROR_STOP=1 --single-transaction -f $inContainer
$exitCode = $LASTEXITCODE
docker exec $Container rm -f $inContainer *> $null

if ($exitCode -ne 0) {
    Write-Host 'El seed falló (se revirtió la transacción). Revisá el output de psql.' -ForegroundColor Red
    exit $exitCode
}

Write-Host ''
Write-Host 'Seed demo aplicado.' -ForegroundColor Green
Write-Host 'Cuentas de prueba (contraseña para todas: Password123):'
Write-Host '  - admin@claseya.dev                (ADMIN)'
Write-Host '  - profe01 .. profe55 @claseya.dev  (profesores; 51-55 con estados no utilizables)'
Write-Host '  - alumno01 .. alumno35 @claseya.dev (estudiantes; 33-35 con estados no utilizables)'
Write-Host '  - student@claseya.dev / ana.profe@claseya.dev / ... (datos previos, preservados)'
