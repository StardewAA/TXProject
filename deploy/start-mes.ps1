# ============================================================
# start-mes.ps1 - Safely start mes-server + ai-server and verify
#                 Nacos registration + gateway route.
#
# Background: OOM on Docker VM previously killed Nacos, mes-server
# failed with "UnknownHostException: nacos", gateway reported
# "Unable to find instance for mes-server" and every MES endpoint /
# AI assistant failed. This script waits for dependencies to be
# healthy, starts the services, polls Nacos registration, then runs
# a gateway smoke probe.
#
# Usage (inside txgycloud/deploy):
#   powershell -ExecutionPolicy Bypass -File .\start-mes.ps1
# ============================================================
$ErrorActionPreference = 'Stop'
$composeArgs = @('-f', 'docker-compose.yml', '-f', 'docker-compose.slim.yml')
$namespace = 'dev'        # must match spring.cloud.nacos.discovery.namespace
$group     = 'DEFAULT_GROUP'

function Wait-ContainerHealthy([string]$name, [int]$waitSec = 180) {
  $deadline = (Get-Date).AddSeconds($waitSec)
  while ((Get-Date) -lt $deadline) {
    $state = docker inspect --format '{{.State.Health.Status}}' $name 2>$null
    if ($state -eq 'healthy') { Write-Host "[OK] $name healthy" -ForegroundColor Green; return $true }
    Write-Host "  ... waiting $name (current: $state)"
    Start-Sleep -Seconds 10
  }
  return $false
}

function Get-NacosInstances([string]$service) {
  $u = "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=$service&groupName=$group&namespaceId=$namespace"
  try {
    $raw = docker exec txgy-slim-nacos-1 sh -c "curl -s '$u' 2>/dev/null" | Out-String
    $j = $raw | ConvertFrom-Json
    return @($j.hosts)
  } catch {
    return @()
  }
}

function Test-HealthyInstance([string]$service) {
  $hosts = Get-NacosInstances $service
  $hit = $hosts | Where-Object { $_.healthy -eq $true -and $_.enabled -eq $true }
  if ($hit) { Write-Host "[OK] $service registered in Nacos: $($hit[0].ip):$($hit[0].port)" -ForegroundColor Green; return $true }
  Write-Host "[WARN] $service not found in Nacos yet (hosts=$($hosts.Count))" -ForegroundColor Yellow
  return $false
}

Write-Host '===== 0. Validate compose config ====='
docker compose @composeArgs config --quiet
if ($LASTEXITCODE -ne 0) { throw 'compose config invalid, check deploy yml files' }

Write-Host '===== 1. Wait for base dependencies (mysql/redis/nacos) ====='
if (-not (Wait-ContainerHealthy 'txgy-slim-mysql-1')) { throw 'MySQL not healthy' }
if (-not (Wait-ContainerHealthy 'txgy-slim-redis-1')) { throw 'Redis not healthy' }
if (-not (Wait-ContainerHealthy 'txgy-slim-nacos-1')) {
  Write-Host '[RECOVER] nacos not healthy, starting it ...' -ForegroundColor Cyan
  docker compose @composeArgs up -d nacos
  if (-not (Wait-ContainerHealthy 'txgy-slim-nacos-1')) { throw 'Nacos cannot recover, see: docker logs txgy-slim-nacos-1' }
}

Write-Host '===== 2. Start mes / ai (mem_limit 2g + log rotation applied) ====='
docker compose @composeArgs up -d --no-build mes ai
if ($LASTEXITCODE -ne 0) { throw 'compose up mes/ai failed' }

Write-Host '===== 3. Wait for container health (actuator) ====='
if (-not (Wait-ContainerHealthy 'txgy-slim-mes-1')) {
  Write-Host '--- last 40 lines of mes log ---' -ForegroundColor Yellow
  docker logs --tail 40 txgy-slim-mes-1
  throw 'mes-server failed healthcheck, inspect logs above'
}
if (-not (Wait-ContainerHealthy 'txgy-slim-ai-1')) {
  Write-Host '--- last 40 lines of ai log ---' -ForegroundColor Yellow
  docker logs --tail 40 txgy-slim-ai-1
  throw 'ai-server failed healthcheck, inspect logs above'
}

Write-Host '===== 4. Poll Nacos registration (gateway lb://mes-server depends on it) ====='
$deadline = (Get-Date).AddSeconds(120)
$mesOk = $false; $aiOk = $false
while ((Get-Date) -lt $deadline) {
  $mesOk = Test-HealthyInstance 'mes-server'
  $aiOk  = Test-HealthyInstance 'ai-server'
  if ($mesOk -and $aiOk) { break }
  Start-Sleep -Seconds 10
}
if (-not $mesOk) {
  Write-Host '[FAIL] mes-server NOT registered. Diagnostics:' -ForegroundColor Red
  Write-Host '  docker logs --tail 60 txgy-slim-mes-1'
}

Write-Host '===== 5. Gateway smoke probe (401=route ok, 503=no instance) ====='
try {
  $r = Invoke-WebRequest -Uri 'http://localhost:48080/admin-api/mes/home-statistics/summary' -UseBasicParsing -TimeoutSec 10
  $body = $r.Content
  if ($body.Length -gt 120) { $body = $body.Substring(0, 120) }
  Write-Host "[OK] gateway->mes HTTP $($r.StatusCode) $body" -ForegroundColor Green
} catch {
  Write-Host "[FAIL] gateway probe error: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ''
Write-Host '===== DONE =====' -ForegroundColor Green
Write-Host 'Refresh http://localhost:5175/ in browser. If the error persists run:'
Write-Host '  docker ps | findstr nacos'
Write-Host '  docker logs --tail 50 txgy-slim-mes-1'
