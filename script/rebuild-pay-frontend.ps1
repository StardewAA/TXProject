# 重建 pay 后端服务与前端（停 mes/ai 腾内存防引擎 500）
$ErrorActionPreference = 'Continue'
cd d:\code\tianxinguanye\txgycloud\deploy

Write-Output "=== STOP mes ai ==="
docker compose stop mes ai 2>&1 | Out-Null

Write-Output "=== BUILD pay ==="
docker compose build pay 2>&1 | Select-Object -Last 4
if ($LASTEXITCODE -ne 0) { Write-Output "PAY-BUILD-FAILED"; docker compose start mes ai 2>&1 | Out-Null; exit 1 }
docker compose up -d pay 2>&1 | Out-Null
Write-Output "PAY-UPDATED"

Write-Output "=== BUILD frontend ==="
docker compose build frontend 2>&1 | Select-Object -Last 4
if ($LASTEXITCODE -ne 0) { Write-Output "FRONTEND-BUILD-FAILED"; docker compose start mes ai 2>&1 | Out-Null; exit 1 }
docker compose up -d frontend 2>&1 | Out-Null
Write-Output "FRONTEND-UPDATED"

Start-Sleep -Seconds 10
Write-Output "=== START mes ai ==="
docker compose start mes ai 2>&1 | Out-Null

Start-Sleep -Seconds 20
docker ps --format "table {{.Names}}\t{{.Status}}"
