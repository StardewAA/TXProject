<#
  AI 助手「查询条件解析」回归自测 —— 一键运行

  用途：改动 AI 助手的条件解析（queryConditions.ts / mock.ts）后，
        先跑这份清单，全绿再重建部署。

  用法（在仓库根目录执行）：
      powershell -ExecutionPolicy Bypass -File deploy\selftest-ai-conditions.ps1

  说明：本机没有安装 Node 也能跑 —— 脚本用 Docker 里的 node:22 执行，
        不需要 pnpm/npm 安装依赖，通常几秒钟出结果。
        返回 0 = 全部通过；返回 1 = 有用例未通过（不要部署）。
#>

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$frontDir = Join-Path $repoRoot 'txgy-ui-admin-vben'
$selftest = 'apps/web-antd/src/views/crm/ai-chat/queryConditions.selftest.ts'

if (-not (Test-Path $frontDir)) {
    Write-Host "未找到前端目录：$frontDir" -ForegroundColor Red
    exit 1
}

Write-Host '==== 运行 AI 查询条件自测（Docker + node:22）====' -ForegroundColor Cyan

docker run --rm `
    -v "${frontDir}:/app" `
    -w /app `
    node:22-bookworm-slim `
    node --experimental-strip-types $selftest

$code = $LASTEXITCODE
if ($code -eq 0) {
    Write-Host '自测通过：可以重建部署。' -ForegroundColor Green
} else {
    Write-Host '自测未通过：请先修复，勿部署。' -ForegroundColor Red
}
exit $code
