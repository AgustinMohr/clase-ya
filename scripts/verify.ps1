# ClaseYa — verify.ps1
# Verificación reproducible del estado del repositorio.
# Ejecuta la suite completa (igual que test.ps1) y reporta PASS/FAIL según el exit code de Maven.
# No deja procesos en segundo plano.
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

Write-Host "== ClaseYa: verificación reproducible ==" -ForegroundColor Cyan

docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Docker Desktop no está disponible." -ForegroundColor Yellow
    Write-Host "Los tests de integración requieren Docker (Testcontainers postgres:16)." -ForegroundColor Yellow
    Write-Host "Arrancá Docker Desktop y volvé a ejecutar este script." -ForegroundColor Yellow
    exit 1
}

Write-Host "Docker OK. Ejecutando 'mvn -B clean test'..." -ForegroundColor Green
& mvn -B clean test
$code = $LASTEXITCODE

if ($code -eq 0) {
    Write-Host ""
    Write-Host "== VERIFY: PASS ==" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "== VERIFY: FAIL (revisar target/surefire-reports) ==" -ForegroundColor Red
}
exit $code
