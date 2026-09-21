# 天信防腐保温平台 —— 本地一键启动（单体模式 / standalone）
#
# 用法（在 deploy 目录下以 PowerShell 运行）：
#   .\start-local.ps1          # 构建并后台启动整套本地环境
#   .\start-local.ps1 -NoBuild # 不重新构建镜像，直接启动已有镜像
#
# 启动后访问：
#   管理后台： http://localhost:5173
#   后端接口： http://localhost:48081/doc.html
#
# 说明：
#   - 数据库初始化脚本位于 deploy/mysql/standalone（已提交仓库），首次启动会自动建库。
#   - 环境变量的默认值见 deploy/.env（MYSQL_ROOT_PASSWORD / MYSQL_DATABASE / TZ）。
#   - 如需彻底重置数据：docker compose -f docker-compose.standalone.yml down -v
[CmdletBinding()]
param(
    [switch]$NoBuild
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

# 前置检查：数据库初始化脚本必须存在
if (-not (Test-Path 'mysql\standalone\001-ruoyi-vue-pro.sql')) {
    Write-Error "缺少数据库初始化脚本：deploy/mysql/standalone/001-ruoyi-vue-pro.sql。`n请确认已提交该目录，或先准备本地初始化脚本。"
    exit 1
}

# 前置检查：Docker 可用
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error "未检测到 docker 命令，请先安装 Docker Desktop 并启动。"
    exit 1
}

$composeArgs = @('-f', 'docker-compose.standalone.yml', 'up', '-d')
if (-not $NoBuild) { $composeArgs += '--build' }

Write-Host "==> 启动本地单体环境（txgy-local）..." -ForegroundColor Cyan
docker compose @composeArgs

Write-Host "`n==> 容器状态" -ForegroundColor Cyan
docker compose -f docker-compose.standalone.yml ps

Write-Host "`n==> 访问地址" -ForegroundColor Green
Write-Host "    管理后台： http://localhost:5173"
Write-Host "    后端接口： http://localhost:48081/doc.html"
Write-Host "`n提示：MySQL 首次启动会从 deploy/mysql/standalone 初始化，约需 1-2 分钟；可观察 txgy-server 日志确认就绪。"

# 确保 WMS AI 助手就绪：新库 init 会自动装好；已有库补跑一次（脚本幂等，无副作用）
Write-Host "`n==> 检查 WMS AI 助手配置..." -ForegroundColor Cyan
$enableScript = Join-Path $PSScriptRoot 'scripts\enable-wms-ai.js'
if ((Get-Command node -ErrorAction SilentlyContinue) -and (Test-Path $enableScript)) {
    node $enableScript --wait 180
    if ($LASTEXITCODE -ne 0) {
        Write-Host "WARN: WMS AI 配置检查失败，详见 docs\wms-ai-deployment.md" -ForegroundColor Yellow
    }
} else {
    Write-Host "WARN: 未找到 Node.js 或脚本，已跳过。可稍后手动执行：" -ForegroundColor Yellow
    Write-Host "      node deploy\scripts\enable-wms-ai.js"
}
