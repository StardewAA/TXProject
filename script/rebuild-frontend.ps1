# 恢复 Docker 引擎 -> 腾内存 -> 重建前端 -> 恢复大服务
$ErrorActionPreference = 'Continue'

# 1. 结束卡死的 Docker 后端，触发引擎重启
Get-Process | Where-Object { $_.Name -in @('com.docker.backend.exe', 'com.docker.build.exe', 'docker-buildx.exe', 'docker-compose.exe') } | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 5

# 2. 确保 Docker Desktop 在运行（后端会自动拉起）
$dd = Get-Process | Where-Object { $_.Name -eq 'Docker Desktop' } | Select-Object -First 1
if (-not $dd) {
    Start-Process "C:\Users\ASUS\AppData\Local\Programs\DockerDesktop\Docker Desktop.exe"
}

# 3. 轮询引擎就绪（最多 4 分钟）
$ready = $false
for ($i = 1; $i -le 24; $i++) {
    Start-Sleep -Seconds 10
    docker ps 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { $ready = $true; Write-Output ("ENGINE-READY " + ($i*10) + "s"); break }
    Write-Output ("waiting " + ($i*10) + "s")
}
if (-not $ready) { Write-Output "ENGINE-NOT-READY"; exit 1 }

# 4. 停大内存服务腾资源
docker compose -f d:\code\tianxinguanye\txgycloud\deploy\docker-compose.yml -f d:\code\tianxinguanye\txgycloud\deploy\docker-compose.override.yml stop mes ai 2>&1 | Out-Null
Write-Output "STOPPED mes ai"

# 5. 重建前端并更新容器
cd d:\code\tianxinguanye\txgycloud\deploy
docker compose build frontend 2>&1 | Select-Object -Last 5
if ($LASTEXITCODE -ne 0) { Write-Output "BUILD-FAILED"; docker compose start mes ai 2>&1 | Out-Null; exit 1 }
docker compose up -d frontend 2>&1 | Select-Object -Last 3
Write-Output "FRONTEND-UPDATED"

# 6. 恢复大内存服务
Start-Sleep -Seconds 10
docker compose start mes ai 2>&1 | Out-Null
Write-Output "STARTED mes ai"
docker ps --format "table {{.Names}}\t{{.Status}}"
