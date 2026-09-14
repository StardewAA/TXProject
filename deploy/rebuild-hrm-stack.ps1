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

# The runtime image context is small and contains no source build. Disable
# BuildKit here because this checkout path contains non-ASCII characters and
# the Docker Desktop Windows frontend can fail while transferring its session.
$env:DOCKER_BUILDKIT = '0'
$env:COMPOSE_DOCKER_CLI_BUILD = '0'

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

$hrmPomDirectory = Join-Path $rootDir 'txgy-module-hrm'
$hrmServerTarget = Join-Path $hrmPomDirectory 'txgy-module-hrm-server\target'
Write-Host 'Building the HRM server from its Maven aggregate.' -ForegroundColor Cyan
Push-Location -LiteralPath $hrmPomDirectory
try {
    & mvn -B -ntp -s (Join-Path $rootDir 'deploy\local\settings.xml') `
        '-Dmaven.test.skip=true' -pl 'txgy-module-hrm-server' -am package
    if ($LASTEXITCODE -ne 0) {
        throw "HRM backend jar build failed (exit=$LASTEXITCODE)"
    }
} finally {
    Pop-Location
}
$hrmJar = Get-ChildItem -LiteralPath $hrmServerTarget -Filter '*.jar' |
    Where-Object { $_.Name -notlike '*.original' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $hrmJar) {
    throw "HRM backend jar was not found in $hrmServerTarget"
}
$runtimeHrmJar = Join-Path $deployDir 'local\jars\hrm.jar'
New-Item -ItemType Directory -Path (Split-Path -Parent $runtimeHrmJar) -Force | Out-Null
Copy-Item -LiteralPath $hrmJar.FullName -Destination $runtimeHrmJar -Force
Write-Host "Runtime jar ready: $runtimeHrmJar" -ForegroundColor Green

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

Write-Host 'Building runtime images for HRM and web-antd.' -ForegroundColor Cyan
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
