# ClaseYa — test.ps1
# Ejecuta la suite completa de tests con preflight de Docker.
# Requisito: Docker Desktop activo (los tests de integración usan Testcontainers postgres:16).
# No deja procesos en segundo plano. Termina con el exit code de Maven.
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

Write-Host "== ClaseYa: suite de tests ==" -ForegroundColor Cyan

# Preflight de Docker (solo lectura, sin procesos ocultos).
docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Docker Desktop no está disponible." -ForegroundColor Yellow
    Write-Host "Los tests de integración requieren Docker (Testcontainers postgres:16)." -ForegroundColor Yellow
    Write-Host "Arrancá Docker Desktop y volvé a ejecutar este script." -ForegroundColor Yellow
    exit 1
}

Write-Host "Docker OK. Ejecutando 'mvn -B clean test'..." -ForegroundColor Green
& mvn -B clean test
exit $LASTEXITCODE
