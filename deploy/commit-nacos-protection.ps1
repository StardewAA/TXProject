<#
  commit-nacos-protection.ps1 — 安全提交「Nacos 命名空间保护」相关文件（只碰白名单）
  -----------------------------------------------------------------------------
  为什么需要它：本仓库工作区里同时躺着大量他人/其它会话的改动（后端 Java、文档、SQL…）。
  用 git add -A / git commit -a 会把它们一并写进历史，就是「把人家的给改了」。
  本脚本只对白名单文件做 add + pathspec commit，其余文件一个都不碰。

  白名单（6 个）—— 只提交「保护工具链」，**不含命名空间值本身**：
    deploy/guard-nacos-config.ps1                                              守卫脚本
    deploy/nacos-config.lock.json                                              封印
    deploy/start.bat                                                           [2.5/5] 接守卫
    deploy/README-docker.md                                                    口径文档
    txgy-module-bidding/txgy-module-bidding-server/run-local.cmd               启动前过守卫
    txgy-module-bidding/.githooks/pre-commit                                   ns-guard 提交闸门

  ⛔ 永不提交（本机专用；靠 skip-worktree 与远端 dev 隔离，提交它们 = 把 public 推给全组）：
    txgy-module-bidding/txgy-module-bidding-server/src/main/resources/application-local.yaml
    txgy-module-bidding/deploy-bidding-local.override.yml
    deploy/docker-compose.yml

  用法：
    powershell -NoProfile -ExecutionPolicy Bypass -File deploy/commit-nacos-protection.ps1           # 预览（不写历史）
    powershell -NoProfile -ExecutionPolicy Bypass -File deploy/commit-nacos-protection.ps1 -Apply    # 真提交（外层仓库）
    powershell -NoProfile -ExecutionPolicy Bypass -File deploy/commit-nacos-protection.ps1 -Apply -Nested   # 顺带提交嵌套仓库

  安全保证：
    · 只 git add 白名单路径；从不 git add -A / git add . / git commit -a；
    · 用 "git commit -m ... -- <paths>"（pathspec 提交），即使索引里已有他人 staged 的改动也不会被带进来；
    · 默认预览模式，必须显式 -Apply 才写历史。
#>
[CmdletBinding()]
param([switch]$Apply, [switch]$Nested, [switch]$SkipGuard)

$ErrorActionPreference = 'Continue'
$root   = Split-Path -Parent $PSScriptRoot
$nested = Join-Path $root 'txgy-module-bidding'

$files = @(
  'deploy/guard-nacos-config.ps1'
  'deploy/nacos-config.lock.json'
  'deploy/start.bat'
  'deploy/README-docker.md'
  'txgy-module-bidding/txgy-module-bidding-server/run-local.cmd'
  'txgy-module-bidding/.githooks/pre-commit'
)

# ⛔ 本机专用文件：绝不提交（远端 master 是 dev，提交 public 会推给全组）
$neverCommit = @(
  'txgy-module-bidding/txgy-module-bidding-server/src/main/resources/application-local.yaml'
  'txgy-module-bidding/deploy-bidding-local.override.yml'
  'deploy/docker-compose.yml'
)

# 反推送闸：这些文件若已在暂存区，直接中止（绝不连带提交）
$staged = @(& git -C $root diff --cached --name-only 2>$null)
$bad = @($neverCommit | Where-Object { $staged -contains $_ })
if ($bad.Count -gt 0) {
  Write-Host '  [中止] 本机专用文件已被 git add 进暂存区：' -ForegroundColor Red
  foreach ($b in $bad) { Write-Host ('         ' + $b) -ForegroundColor Red }
  Write-Host '         本机 public 配置不应进入任何提交（远端 master 是 dev）。先取消暂存：' -ForegroundColor Yellow
  Write-Host ('         git -C "' + $root + '" restore --staged ' + ($bad -join ' ')) -ForegroundColor Yellow
  exit 1
}

Write-Host ''
Write-Host '== 安全提交：Nacos 命名空间保护（只提交白名单文件）==' -ForegroundColor Cyan

if (-not $SkipGuard) {
  Write-Host '  [1/4] 先过守卫……'
  & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'guard-nacos-config.ps1')
  if ($LASTEXITCODE -ne 0) {
    Write-Host '  守卫未通过：先修复（可加 -Fix）再提交。' -ForegroundColor Red
    exit 1
  }
} else {
  Write-Host '  [1/4] 已跳过守卫（-SkipGuard）' -ForegroundColor Yellow
}

Write-Host '  [2/4] 统计待提交文件（白名单内）……'
$changed = @()
$untracked = @()
foreach ($f in $files) {
  $st = (& git -C $root status --porcelain -- $f 2>$null | Select-Object -First 1)
  if (-not $st) { continue }
  $changed += $f
  if ($st -match '^\?\?') { $untracked += $f }
  Write-Host ("        {0}  {1}" -f $st.Substring(0, 2), $f)
}
$otherCount = @(& git -C $root status --porcelain 2>$null | Where-Object { $_ -notmatch '^\?\?' }).Count
Write-Host ("        仓库内另有 {0} 个已跟踪文件有改动 —— 本次不会提交它们" -f $otherCount) -ForegroundColor DarkGray

if ($changed.Count -eq 0) {
  Write-Host '  白名单内没有需要提交的改动。' -ForegroundColor Yellow
  exit 0
}

$msg = @(
  'chore(nacos): 加命名空间守卫 + 启动/提交闸门（本机 public、远端 dev，互不覆盖）'
  ''
  '- 新增 deploy/guard-nacos-config.ps1: 校验 yaml/override/容器三处命名空间 + skip-worktree 隔离 + 外来 nacos 容器；-Fix 自愈 / -Seal 封印'
  '- 新增 deploy/nacos-config.lock.json: 保护脚本自身封印（SHA256），被静默改写会告警'
  '- start.bat [2.5/5]、run-local.cmd 启动前过守卫（漂移自动 -Fix 修复）；.githooks/pre-commit 拦「改动命名空间口径」的提交'
  '- 口径: 远端 master 保持 dev（团队默认），本机保持 public；两者由 git skip-worktree 隔离，pull 不覆盖、push 不外发'
  '- 本提交不含任何命名空间值（本机 public 文件不在白名单内）'
) -join "`n"

Write-Host '  [3/4] 提交白名单文件（pathspec 提交，不碰其它改动）……'
if (-not $Apply) {
  Write-Host '        [预览模式] 未写历史。真实提交请加 -Apply' -ForegroundColor Yellow
} else {
  if ($untracked.Count -gt 0) { & git -C $root add -- @untracked | Out-Null }
  & git -C $root commit -m $msg -- @changed
  if ($LASTEXITCODE -ne 0) { Write-Host '        提交失败（可能无实际差异）。' -ForegroundColor Red; exit 1 }
  & git -C $root log -1 --stat --format='        %h %ad %s' --date=short
}

if ($Nested) {
  Write-Host '  [4/4] 嵌套仓库 txgy-module-bidding 同步提交……'
  $nfiles = @($changed | Where-Object { $_ -like 'txgy-module-bidding/*' } | ForEach-Object { $_.Substring('txgy-module-bidding/'.Length) })
  if ($nfiles.Count -eq 0) {
    Write-Host '        嵌套仓库无白名单改动' -ForegroundColor DarkGray
  } elseif ($Apply) {
    foreach ($nf in $nfiles) {
      $st = (& git -C $nested status --porcelain -- $nf 2>$null | Select-Object -First 1)
      if ($st -and $st -match '^\?\?') { & git -C $nested add -- $nf | Out-Null }
    }
    & git -C $nested commit -m $msg -- @nfiles
    & git -C $nested log -1 --stat --format='        %h %ad %s' --date=short
  } else {
    Write-Host ('        [预览] 将提交: ' + ($nfiles -join ', '))
  }
} else {
  Write-Host '  [4/4] 未指定 -Nested：嵌套仓库不动' -ForegroundColor DarkGray
}

Write-Host ''
Write-Host '完成。若要提交其它文件请另行处理——本脚本从不 git add -A。' -ForegroundColor Green
