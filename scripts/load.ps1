# ClaseYa - load smoke (TEST-01)
# Runs the k6 scenarios against a RUNNING backend. The backend and claseya-pg must be up first.
# k6 comes from the official grafana/k6 Docker image, so there is no Maven/npm dependency.
#
# Example:
#   docker start claseya-pg
#   mvn spring-boot:run "-Dspring-boot.run.profiles=local" "-Dspring-boot.run.jvmArguments=-Duser.timezone=UTC"
#   scripts\load.ps1 -Vus 10 -InboxVus 5 -Duration 30s
[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [int]$Vus = 10,
    [int]$InboxVus = 5,
    [string]$Duration = '30s',
    [string]$Email = 'student@claseya.dev',
    [string]$Password = 'Password123',
    [string]$K6Image = 'grafana/k6:2.3.0'
)

$ErrorActionPreference = 'Stop'

Write-Host "== ClaseYa: load smoke (k6) ==" -ForegroundColor Cyan

docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Docker Desktop no está disponible." -ForegroundColor Yellow
    exit 1
}

try {
    $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -TimeoutSec 5
} catch {
    Write-Host "El backend no responde en $BaseUrl." -ForegroundColor Yellow
    Write-Host "Levantá el backend con el perfil local y volvé a intentar." -ForegroundColor Yellow
    exit 1
}
if ($health.status -ne 'UP') {
    Write-Host "El backend está en estado '$($health.status)'." -ForegroundColor Yellow
    exit 1
}

# Docker Desktop resolves the host through host.docker.internal, not localhost.
$containerBase = $BaseUrl `
    -replace '//localhost', '//host.docker.internal' `
    -replace '//127\.0\.0\.1', '//host.docker.internal'

$scriptDir = Join-Path $PSScriptRoot 'load'
Write-Host "Target: $BaseUrl ($containerBase from the k6 container)" -ForegroundColor DarkGray
Write-Host "Scenarios: search=$Vus VUs, inbox=$InboxVus VUs, duration=$Duration" -ForegroundColor DarkGray

& docker run --rm `
    --add-host=host.docker.internal:host-gateway `
    --volume "${scriptDir}:/scripts" `
    --env "BASE_URL=$containerBase" `
    --env "VUS=$Vus" `
    --env "INBOX_VUS=$InboxVus" `
    --env "DURATION=$Duration" `
    --env "EMAIL=$Email" `
    --env "PASSWORD=$Password" `
    $K6Image run /scripts/search-and-inbox.js

exit $LASTEXITCODE
