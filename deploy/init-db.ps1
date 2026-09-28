# ==============================================================
# txgycloud 一键初始化数据库（Windows PowerShell 版）
# 适用于已有旧数据的环境，不清库、幂等、可重复运行
#
# 用法（在 deploy 目录下）：
#   powershell -ExecutionPolicy Bypass -File .\init-db.ps1
#   powershell -ExecutionPolicy Bypass -File .\init-db.ps1 -Force
# ==============================================================
param([switch]$Force)

$ErrorActionPreference = 'Continue'
Set-Location $PSScriptRoot

$MysqlContainer = 'txgy-slim-mysql-1'
$RedisContainer = 'txgy-slim-redis-1'
$AiContainer    = 'txgy-slim-ai-1'
$SystemContainer= 'txgy-slim-system-1'
$Db = 'ruoyi-vue-pro'

$SqlFull     = 'mysql/init/0099-full-ai-mes-init.sql'
$SqlCloneAi  = '..\sql\clone_ai_to_all_tenants.sql'
$SqlCloneBiz = '..\sql\clone_biz_demo_to_all_tenants.sql'

Write-Host '[1/5] 检查容器...'
$null = docker inspect $MysqlContainer 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host '错误：MySQL 容器未运行。请先在 deploy 目录执行：'
    Write-Host '  docker compose -f docker-compose.yml -f docker-compose.slim.yml up -d'
    exit 1
}

$Pw = 'root'
if (Test-Path '.env') {
    $line = Get-Content '.env' -Encoding UTF8 | Where-Object { $_ -match '^MYSQL_ROOT_PASSWORD=' } | Select-Object -First 1
    if ($line) { $Pw = ($line -split '=', 2)[1].Trim() }
}

Write-Host '[2/5] 检测数据库当前状态...'
$tables = (docker exec $MysqlContainer mysql -uroot -p"$Pw" -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Db'" 2>$null | Select-Object -First 1)
if (-not $tables) { $tables = 0 }
Write-Host "      当前数据库表数量：$tables"

if ([int]$tables -gt 100 -and -not $Force) {
    Write-Host '      检测到数据库已初始化：跳过全量 SQL（如需强制重导请加 -Force）'
} else {
    Write-Host '[3/5] 导入全量初始化 SQL（覆盖同名表，不动其他表）...'
    cmd /c "docker exec -i $MysqlContainer mysql -uroot -p$Pw --default-character-set=utf8mb4 $Db < $SqlFull"
}

Write-Host '[4/5] 克隆 AI 数据到全部租户（幂等）...'
cmd /c "docker exec -i $MysqlContainer mysql -uroot -p$Pw --default-character-set=utf8mb4 $Db < $SqlCloneAi" 2>$null | Select-Object -Last 3

Write-Host '      克隆 MES 演示数据到全部租户（幂等）...'
cmd /c "docker exec -i $MysqlContainer mysql -uroot -p$Pw --default-character-set=utf8mb4 $Db < $SqlCloneBiz" 2>$null | Select-Object -Last 3

Write-Host '[5/5] 清理 Redis 缓存...'
docker exec $RedisContainer redis-cli FLUSHALL 2>$null | Out-Null

Write-Host ''
Write-Host '初始化完成！建议重启后端服务使缓存彻底刷新：'
Write-Host "  docker restart $AiContainer $SystemContainer"
Write-Host '然后浏览器访问 http://localhost:5173 ，用 huahan / admin123（租户 2010）登录即可使用 AI 助手。'
