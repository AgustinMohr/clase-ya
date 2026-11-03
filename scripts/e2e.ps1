# ClaseYa - suite E2E (Playwright)
# Levanta el stack completo (Spring Boot con perfil local + Vite) y corre los flujos criticos
# contra la base demo local. Requiere Docker Desktop activo y el contenedor claseya-pg.
# No deja procesos en segundo plano: Playwright inicia y detiene sus webServer.
[CmdletBinding()]
param(
    [switch]$ReuseBackend,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$PlaywrightArgs
)

$ErrorActionPreference = 'Stop'

Write-Host "== ClaseYa: E2E (Playwright) ==" -ForegroundColor Cyan

docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Docker Desktop no esta disponible." -ForegroundColor Yellow
    exit 1
}

$container = docker ps -a --filter "name=^claseya-pg$" --format "{{.Names}}"
if (-not $container) {
    Write-Host "No existe el contenedor claseya-pg." -ForegroundColor Yellow
    Write-Host "Crealo una vez con:" -ForegroundColor Yellow
    Write-Host "  docker run --name claseya-pg -e POSTGRES_USER=claseya -e POSTGRES_PASSWORD=claseya -e POSTGRES_DB=claseya -p 5432:5432 -d postgres:16" -ForegroundColor Yellow
    exit 1
}

docker start claseya-pg *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "No se pudo iniciar claseya-pg." -ForegroundColor Red
    exit 1
}

$env:E2E_REUSE_BACKEND = if ($ReuseBackend) { 'true' } else { 'false' }
$frontend = Join-Path $PSScriptRoot '..\frontend'
Push-Location $frontend
try {
    & npx playwright test @PlaywrightArgs
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
