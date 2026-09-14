param(
    [switch]$BuildOnly,
    [switch]$NoCache
)

$ErrorActionPreference = 'Stop'

$deployDir = $PSScriptRoot
$rootDir = Split-Path -Parent $deployDir
$composeFile = Join-Path $deployDir 'local\docker-compose.local.yml'
$envFile = Join-Path $deployDir '.env'
$vbenDir = Join-Path $rootDir 'txgy-ui-admin-vben'
$frontendDist = Join-Path $vbenDir 'apps\web-antd\dist\index.html'

Set-Location -LiteralPath $rootDir

$env:DOCKER_BUILDKIT = '1'
$env:COMPOSE_DOCKER_CLI_BUILD = '1'

$env:COREPACK_NPM_REGISTRY = 'https://registry.npmmirror.com'
$env:NODE_OPTIONS = '--max-old-space-size=4096'
$env:VITE_BASE_URL = ''
$env:VITE_GLOB_API_URL = '/admin-api'
$env:VITE_APP_CAPTCHA_ENABLE = 'false'
$env:VITE_APP_DOCALERT_ENABLE = 'false'

function Invoke-Compose {
    param([string[]]$Arguments)

    & docker compose -f $composeFile --env-file $envFile @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose $($Arguments -join ' ') failed (exit=$LASTEXITCODE)"
    }
}

Write-Host 'Building web-antd on the host before creating the lightweight frontend image.' -ForegroundColor Cyan
Push-Location -LiteralPath $vbenDir
try {
    & corepack pnpm@11.7.0 exec turbo build --filter=@vben/web-antd --concurrency=2
    if ($LASTEXITCODE -ne 0) {
        throw "web-antd build failed (exit=$LASTEXITCODE)"
    }
} finally {
    Pop-Location
}
if (-not (Test-Path -LiteralPath $frontendDist)) {
    throw "web-antd build completed without $frontendDist"
}

$buildArguments = @('build', '--pull')
if ($NoCache) {
    $buildArguments += '--no-cache'
}
$buildArguments += @('hrm', 'frontend')

Write-Host 'Building HRM backend and web-antd frontend from the current working tree.' -ForegroundColor Cyan
Invoke-Compose $buildArguments

if ($BuildOnly) {
    Write-Host 'Build completed; running containers were not changed.' -ForegroundColor Green
    exit 0
}

Invoke-Compose @('up', '-d', '--no-deps', '--force-recreate', 'hrm', 'frontend')

function Wait-Healthy {
    param([string]$Container, [int]$TimeoutSec = 300)

    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    do {
        $status = docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' $Container 2>$null
        if ($status -eq 'healthy') {
            return
        }
        Start-Sleep -Seconds 5
    } while ((Get-Date) -lt $deadline)

    throw "$Container did not become healthy (status=$status)"
}

Wait-Healthy 'txgy-frontend' 120
Wait-Healthy 'txgy-hrm' 300
Write-Host 'HRM backend and web-antd frontend are running.' -ForegroundColor Green
