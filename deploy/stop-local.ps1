# 天信防腐保温平台 —— 本地停止（单体模式 / standalone）
#
# 用法（在 deploy 目录下以 PowerShell 运行）：
#   .\stop-local.ps1          # 停止并移除容器（保留 MySQL 数据卷）
#   .\stop-local.ps1 -Reset   # 停止并删除容器 + 数据卷（清空所有本地数据，下次启动重新初始化）
[CmdletBinding()]
param(
    [switch]$Reset
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if ($Reset) {
    Write-Host "==> 停止并重置本地环境（删除容器与数据卷）..." -ForegroundColor Yellow
    docker compose -f docker-compose.standalone.yml down -v
    Write-Host "已清空本地数据，下次启动将从 deploy/mysql/standalone 重新初始化。" -ForegroundColor Green
} else {
    Write-Host "==> 停止本地环境（保留数据卷）..." -ForegroundColor Cyan
    docker compose -f docker-compose.standalone.yml down
    Write-Host "已停止。需要清空数据请用：.\stop-local.ps1 -Reset" -ForegroundColor Green
}
