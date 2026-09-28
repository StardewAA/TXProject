$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$root = 'D:\codexshuju\tianxin-pipe-project'
$repo = Join-Path $root 'project-base\txgycloud'
$localDeploy = Join-Path $repo 'deploy\local'
$composeFile = Join-Path $localDeploy 'docker-compose.local.yml'
$overrideFile = Join-Path $root 'docker\docker-compose.txgy.override.yml'
$envFile = Join-Path $repo 'deploy\.env'
$jarDir = Join-Path $localDeploy 'jars'

$dockerCandidates = @(
    (Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe'),
    (Join-Path $env:ProgramFiles 'Docker\Docker\resources\bin\docker.exe')
)
$docker = $dockerCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $docker) {
    $docker = (Get-Command docker.exe -ErrorAction Stop).Source
}
$env:Path = "$(Split-Path -Parent $docker);$env:Path"

if (-not (Test-Path -LiteralPath $envFile)) {
    $envLines = @(
        'MYSQL_ROOT_PASSWORD=root',
        'MYSQL_DATABASE=ruoyi-vue-pro',
        'NACOS_AUTH_TOKEN=eXVkYW8tbG9jYWwtZGV2ZWxvcG1lbnQtbmFjb3Mtc2VjcmV0LTIwMjY=',
        'NACOS_AUTH_IDENTITY_KEY=serverIdentity',
        'NACOS_AUTH_IDENTITY_VALUE=security',
        'TZ=Asia/Shanghai',
        'MYSQL_PORT=13307',
        'REDIS_PORT=26379',
        'NACOS_PORT=8848',
        'NACOS_GRPC_PORT=9848',
        'NACOS_GRPC_TLS_PORT=9849',
        'GATEWAY_PORT=48080',
        'FRONTEND_PORT=5173',
        'DASHSCOPE_API_KEY='
    )
    Set-Content -LiteralPath $envFile -Encoding ascii -Value $envLines
}

$env:MYSQL_ROOT_PASSWORD = 'root'
$env:MYSQL_DATABASE = 'ruoyi-vue-pro'
$env:NACOS_AUTH_TOKEN = 'eXVkYW8tbG9jYWwtZGV2ZWxvcG1lbnQtbmFjb3Mtc2VjcmV0LTIwMjY='
$env:NACOS_AUTH_IDENTITY_KEY = 'serverIdentity'
$env:NACOS_AUTH_IDENTITY_VALUE = 'security'
$env:TZ = 'Asia/Shanghai'
$env:MYSQL_PORT = '13307'
$env:REDIS_PORT = '26379'
$env:NACOS_PORT = '8848'
$env:NACOS_GRPC_PORT = '9848'
$env:NACOS_GRPC_TLS_PORT = '9849'
$env:GATEWAY_PORT = '48080'
$env:FRONTEND_PORT = '5173'

function Invoke-Compose {
    param([string[]]$ComposeArgs)
    & $docker compose -f $composeFile -f $overrideFile --env-file $envFile @ComposeArgs
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose $($ComposeArgs -join ' ') failed with exit code $LASTEXITCODE"
    }
}

function Wait-Healthy {
    param([string]$Container, [int]$TimeoutSec = 600)
    $sw = [Diagnostics.Stopwatch]::StartNew()
    $status = 'starting'
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
        $status = & $docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' $Container 2>$null
        if ($status -eq 'healthy') {
            Write-Host "$Container is healthy" -ForegroundColor Green
            return
        }
        Start-Sleep -Seconds 5
    }
    throw "$Container did not become healthy within $TimeoutSec seconds (status=$status)"
}

function Stage-Jar {
    param([string]$ModulePath, [string]$JarName)
    $target = Join-Path (Join-Path $repo ($ModulePath.Replace('/', '\'))) 'target'
    $jar = Get-ChildItem -LiteralPath $target -Filter '*.jar' -File -ErrorAction Stop |
        Where-Object { $_.Name -notlike 'original-*' } |
        Sort-Object Length -Descending |
        Select-Object -First 1
    if (-not $jar) {
        throw "No packaged jar found in $target"
    }
    New-Item -ItemType Directory -Path $jarDir -Force | Out-Null
    Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $jarDir "$JarName.jar") -Force
    Write-Host "Staged $($jar.Name) -> $JarName.jar" -ForegroundColor Cyan
}

Write-Host '=== 1. staging backend jars ===' -ForegroundColor Cyan
Stage-Jar 'txgy-gateway' 'gateway'
Stage-Jar 'txgy-module-system/txgy-module-system-server' 'system'
Stage-Jar 'txgy-module-infra/txgy-module-infra-server' 'infra'
Stage-Jar 'txgy-module-member/txgy-module-member-server' 'member'
Stage-Jar 'txgy-module-wms/txgy-module-wms-server' 'wms'
Stage-Jar 'txgy-module-cms/txgy-module-cms-server' 'cms'

Write-Host '=== 2. starting middleware ===' -ForegroundColor Cyan
Invoke-Compose -ComposeArgs @('up','-d','mysql','redis','nacos')
Wait-Healthy 'txgy-mysql' 600
Wait-Healthy 'txgy-project-redis' 120
Wait-Healthy 'txgy-nacos' 600

Write-Host '=== 3. importing database ===' -ForegroundColor Cyan
$dbMarker = Join-Path $root '.db-initialized'
if (Test-Path -LiteralPath $dbMarker) {
    Write-Host 'Database already initialized; skipping import.' -ForegroundColor Yellow
} else {
    & (Join-Path $localDeploy 'init-db.ps1')
    Set-Content -LiteralPath $dbMarker -Encoding ascii -Value (Get-Date -Format o)
}

Write-Host '=== 4. initializing Nacos namespace ===' -ForegroundColor Cyan
& (Join-Path $localDeploy 'init-nacos.ps1')

Write-Host '=== 5. building and starting backend services ===' -ForegroundColor Cyan
Invoke-Compose -ComposeArgs @('up','-d','--build','--no-deps','gateway','system','infra','member','wms','cms','ui')

Write-Host '=== 6. verifying local stack ===' -ForegroundColor Cyan
& (Join-Path $localDeploy 'verify.ps1')

Write-Host ''
Write-Host 'Project started. Frontend: http://127.0.0.1:8080/  Nacos: http://127.0.0.1:8848/nacos/  Gateway: http://127.0.0.1:48080/' -ForegroundColor Green






