# 前端重建 v2：停全部 Java 服务 -> 构建前端 -> 全部恢复
$ErrorActionPreference = 'Continue'
cd d:\code\tianxinguanye\txgycloud\deploy

Write-Output "=== STOP ALL JAVA SERVICES ==="
docker compose stop mes ai pay system infra gateway 2>&1 | Out-Null
Write-Output "STOPPED"

Write-Output "=== BUILD FRONTEND ==="
docker compose build frontend 2>&1 | Select-Object -Last 3
$buildOk = ($LASTEXITCODE -eq 0)
Write-Output ("BUILD-RESULT code=" + $LASTEXITCODE)

if ($buildOk) {
    docker compose up -d frontend 2>&1 | Out-Null
    Write-Output "FRONTEND-UPDATED"
}

Write-Output "=== START ALL SERVICES ==="
docker compose start mes ai pay system infra gateway 2>&1 | Out-Null
Write-Output "STARTED"

Start-Sleep -Seconds 30
docker ps --format "table {{.Names}}\t{{.Status}}"
