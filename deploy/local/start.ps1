# One-shot local startup:
#   1. start middleware (mysql / redis / nacos) and wait until healthy
#   2. import business SQL into ruoyi-vue-pro (safe subset, see init-db.ps1)
#   3. create the "dev" namespace in Nacos
#   4. build service images from pre-built jars and start them
param(
    [string[]]$Services = @('gateway', 'system', 'infra', 'member'),
    [int]$MiddlewareTimeoutSec = 600
)

$ErrorActionPreference = 'Stop'

$localDir  = $PSScriptRoot
$deployDir = Split-Path -Parent $localDir
$rootDir   = Split-Path -Parent $deployDir
$compose   = Join-Path $localDir 'docker-compose.local.yml'
$envFile   = Join-Path $deployDir '.env'

Set-Location -LiteralPath $rootDir

function Invoke-Compose {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
    & docker compose -f $compose --env-file $envFile @Args
    if ($LASTEXITCODE -ne 0) { throw "docker compose $($Args -join ' ') failed (exit=$LASTEXITCODE)" }
}

function Wait-Healthy {
    param([string]$Container, [int]$TimeoutSec = 300)
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
        $st = docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' $Container 2>$null
        if ($st -eq 'healthy') { Write-Host "$Container is healthy" -ForegroundColor Green; return $true }
        Start-Sleep -Seconds 5
    }
    Write-Host "$Container did NOT become healthy within $TimeoutSec s (status=$st)" -ForegroundColor Red
    return $false
}

Write-Host "== 1/4 starting middleware ==" -ForegroundColor Cyan
Invoke-Compose up -d mysql redis nacos

Wait-Healthy txgy-mysql  $MiddlewareTimeoutSec | Out-Null
Wait-Healthy txgy-redis  120 | Out-Null
Wait-Healthy txgy-nacos  $MiddlewareTimeoutSec | Out-Null

Write-Host "== 2/4 importing database ==" -ForegroundColor Cyan
& (Join-Path $localDir 'init-db.ps1')

Write-Host "== 3/4 initializing nacos namespace ==" -ForegroundColor Cyan
& (Join-Path $localDir 'init-nacos.ps1')

Write-Host "== 4/4 building & starting services ==" -ForegroundColor Cyan
Invoke-Compose up -d --build @Services

Write-Host "Done. Use verify.ps1 to check the endpoints." -ForegroundColor Green
