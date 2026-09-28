<#
  guard-nacos-config.ps1 —— Nacos 命名空间/端口一致性守卫（canonical = public）
  ---------------------------------------------------------------------------
  为什么需要它：本机 docker 配置反复被"静默覆盖"，表现为「服务都起不来」：
    · deploy/docker-compose.yml 与 txgy-module-bidding/deploy-bidding-local.override.yml
      在外层仓库被标记 skip-worktree（git 装作看不到本地改动），任何 checkout/reset/merge
      都会用索引里的旧版本盖掉磁盘 → 覆盖里给 gateway/system/infra 置空的 namespace 没了；
    · 同时 application-local.yaml 曾被写成 namespace: dev → bidding 注册进 dev，
      网关在 public 找不到实例（Unable to find instance for bidding-server），前端全线 503。
  口径（2026-09-20 与用户确认）：
    · 远端 origin/master = dev（团队口径，别人拉的默认值）；本机 = public（容器与 run-local.cmd 都是 public）。
    · 两者靠 git skip-worktree 隔离：本地改 public 不会被 push 上去，远端 dev 也不会在 pull 时覆盖本地。
  本脚本做四件事：
    1) 校验本地三处命名空间口径一致为 public（yaml / override / 运行中容器）；
    2) 校验 compose 里 nacos 的 8848/9848/9849 映射没被改花；
    3) 校验 skip-worktree 标记在位（丢了就会「pull 覆盖本地 public / push 把 public 推上去」）+ 识别抢 88xx/98xx 的外来 nacos 容器；
    4) -Fix：把本地配置修回 public（幂等）、把 skip-worktree 标记补回来。
  用法：
    powershell -NoProfile -ExecutionPolicy Bypass -File deploy/guard-nacos-config.ps1
    powershell -NoProfile -ExecutionPolicy Bypass -File deploy/guard-nacos-config.ps1 -Fix
    powershell -NoProfile -ExecutionPolicy Bypass -File deploy/guard-nacos-config.ps1 -Seal   # 改动过保护脚本后重新封印
  退出码：0=通过（或已自动修复）  1=存在漂移未修复
#>
[CmdletBinding()]
param([switch]$Fix, [switch]$Seal)

$ErrorActionPreference = 'Continue'
$root     = Split-Path -Parent $PSScriptRoot
$yamlPath = Join-Path $root 'txgy-module-bidding\txgy-module-bidding-server\src\main\resources\application-local.yaml'
$ovrPath  = Join-Path $root 'txgy-module-bidding\deploy-bidding-local.override.yml'
$cfgPath  = Join-Path $root 'deploy\docker-compose.yml'
$ovrRel   = 'txgy-module-bidding/deploy-bidding-local.override.yml'
$yamlRel  = 'txgy-module-bidding/txgy-module-bidding-server/src/main/resources/application-local.yaml'

$script:fail = 0
function Say([string]$L, [string]$M, [string]$C) { Write-Host ("  [{0}] {1}" -f $L, $M) -ForegroundColor $C }
function Ok([string]$m)   { Say 'OK'   $m 'Green' }
function Bad([string]$m)  { Say 'FAIL' $m 'Red'; $script:fail++ }
function Warn([string]$m) { Say 'WARN' $m 'Yellow' }
function Info([string]$m) { Say 'INFO' $m 'DarkGray' }
function WriteNoBom([string]$p, [string]$text) {
  [System.IO.File]::WriteAllText($p, $text, (New-Object System.Text.UTF8Encoding($false)))
}

Write-Host ''
Write-Host '== Nacos 配置守卫（canonical = public）== ' -ForegroundColor Cyan
Write-Host ("   仓库根: {0}" -f $root) -ForegroundColor DarkGray
if ($Fix) { Write-Host '   模式: -Fix（发现漂移自动修复）' -ForegroundColor Yellow }

# ---------- 1. 本地 bidding 配置 ----------
Write-Host ''
Write-Host '  1) application-local.yaml（bidding 本机启动用）'
if (-not (Test-Path -LiteralPath $yamlPath)) {
  Bad "找不到 $yamlPath"
} else {
  $lines = [System.IO.File]::ReadAllLines($yamlPath)
  $hits = @()
  for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match '^\s*namespace:\s*(.*)$') {
      $v = ($Matches[1] -replace '#.*$', '').Trim().Trim("'").Trim('"')
      $hits += [pscustomobject]@{ No = $i + 1; Raw = $lines[$i]; Val = $v }
    }
  }
  if ($hits.Count -eq 0) {
    Bad '未找到 namespace 配置行（文件被改坏？）'
  } else {
    $drift = @($hits | Where-Object { $_.Val -ne '' -and $_.Val -ne 'public' })
    if ($drift.Count -eq 0) {
      Ok ("namespace 全部为空(=public)，共 {0} 处" -f $hits.Count)
    } else {
      foreach ($d in $drift) { Bad ("第 {0} 行 namespace='{1}' 必须为空(public)：" -f $d.No, $d.Val) }
      if ($Fix) {
        $out = New-Object System.Collections.Generic.List[string]
        foreach ($l in $lines) {
          if ($l -match '^(\s*)namespace:\s*.*$') {
            $out.Add($Matches[1] + "namespace: '' # 受保护：空 = public（guard-nacos-config.ps1 修复）")
          } else { $out.Add($l) }
        }
        WriteNoBom $yamlPath (($out -join "`r`n") + "`r`n")
        Ok '-Fix 已把 namespace 修回空(=public)'
      }
    }
  }
}

# ---------- 2. 容器侧 override ----------
Write-Host ''
Write-Host '  2) deploy-bidding-local.override.yml（容器侧命名空间覆盖）'
if (-not (Test-Path -LiteralPath $ovrPath)) {
  Bad "找不到 $ovrPath"
} else {
  $t = [System.IO.File]::ReadAllText($ovrPath)
  $d = ([regex]::Matches($t, 'SPRING_CLOUD_NACOS_DISCOVERY_NAMESPACE:\s*""')).Count
  $c = ([regex]::Matches($t, 'SPRING_CLOUD_NACOS_CONFIG_NAMESPACE:\s*""')).Count
  if ($d -ge 3 -and $c -ge 3) {
    Ok "gateway/system/infra 置空(=public) 覆盖齐备（discovery $d / config $c，需各 3）"
  } else {
    Bad "置空覆盖缺失（discovery $d / config $c，需各 3）→ 容器退回 jar 内默认命名空间，网关找不到实例"
    if ($Fix) {
      $canonical = @'
# 本地联调 override：system/infra/gateway 宿主端口映射 + 空 namespace（public，与本地 bidding/其余服务一致）。
# 用法（deploy 目录）：docker compose -f docker-compose.yml -f ../txgy-module-bidding/deploy-bidding-local.override.yml -p txgy up -d system infra gateway
# 配合：bidding run-local.cmd 中 openfeign url 指向 127.0.0.1:48087/48088。
#
# !!! 受保护文件（guard-nacos-config.ps1 守护）!!!
#   下面 gateway/system/infra 三个块里的 SPRING_CLOUD_NACOS_*_NAMESPACE: "" 必须保留（空 = public）。
#   删掉它们 = 容器回到 jar 内默认命名空间(dev)，网关在 public 找不到 bidding/其它服务 → 「服务起不来」。
#   本文件在外层仓库曾被标记 skip-worktree，git 操作会静默回滚它；改前请先跑：
#     powershell -NoProfile -ExecutionPolicy Bypass -File deploy/guard-nacos-config.ps1
services:
  nacos:
    # 2026-09-11 实测：Nacos 3.x 镜像默认堆较大（-Xmx2g），而本机给容器的 cgroup 限额只有 768MB，
    # 结果启动 6 分钟后被内核 OOMKilled（ExitCode=137, OOMKilled=true），随后 infra/system/gateway
    # 因"连不上 nacos"接连退出。这里把堆固定小：768MB 限额下 512m 堆 + 非堆开销刚好放得下。
    # 规范依据：《招投标模块开发规范》本地联调环境说明；用户口径 2026-09-11「自己把后端开启」。
    environment:
      JVM_XMS: 256m
      JVM_XMX: 512m
      JVM_XMN: 128m
  gateway:
    environment:
      SPRING_CLOUD_NACOS_DISCOVERY_NAMESPACE: ""
      SPRING_CLOUD_NACOS_CONFIG_NAMESPACE: ""
  system:
    ports:
      - "48087:48080"
    environment:
      SPRING_CLOUD_NACOS_DISCOVERY_NAMESPACE: ""
      SPRING_CLOUD_NACOS_CONFIG_NAMESPACE: ""
  infra:
    ports:
      - "48088:48080"
    environment:
      SPRING_CLOUD_NACOS_DISCOVERY_NAMESPACE: ""
      SPRING_CLOUD_NACOS_CONFIG_NAMESPACE: ""
'@
      WriteNoBom $ovrPath ($canonical -replace "`r?`n", "`r`n")
      Ok '-Fix 已按 canonical 重写 override（含 3 处置空覆盖）'
    }
  }
}

# ---------- 3. compose 端口映射 ----------
Write-Host ''
Write-Host '  3) deploy/docker-compose.yml（nacos 端口映射）'
if (-not (Test-Path -LiteralPath $cfgPath)) {
  Warn "找不到 $cfgPath（跳过）"
} else {
  $ct = [System.IO.File]::ReadAllText($cfgPath)
  $want = @('"8848:8848"', '"9848:9848"', '"9849:9849"')
  $miss = @($want | Where-Object { $ct -notmatch [regex]::Escape($_) })
  if ($miss.Count -eq 0) {
    Ok 'nacos 8848/9848/9849 映射齐备'
  } else {
    Bad ("nacos 端口映射缺失/被改：" + ($miss -join ', '))
  }
}

# ---------- 4. git 索引标记（本地 public 与远端 dev 的隔离层）----------
Write-Host ''
Write-Host '  4) git 索引标记（skip-worktree 必须【在位】）'
if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
  Info 'git 不可用，跳过'
} else {
  $mustSkip = @('deploy/docker-compose.yml', $ovrRel, $yamlRel)
  $missing = @()
  foreach ($p in $mustSkip) {
    $o = (& git -C $root ls-files -v -- $p 2>$null | Select-Object -First 1)
    if (-not $o) { continue }
    if ($o -notmatch '^S\s') { $missing += $p }
  }
  if ($missing.Count -eq 0) {
    Ok '3 个本地化文件均带 skip-worktree：pull 不会覆盖本地 public，push 也不会把 public 带上去'
  } else {
    foreach ($p in $missing) { Warn "$p 缺 skip-worktree 标记 → 远端 master 是 dev，pull 会把本地 public 覆盖回去，push 也可能把 public 推上去" }
    if ($Fix) {
      foreach ($p in $missing) { & git -C $root update-index --skip-worktree $p 2>$null | Out-Null }
      Ok '-Fix 已补回 skip-worktree 标记（本地 public 只留本机）'
    } else {
      Warn '修复：git update-index --skip-worktree <上面列出的文件>   或跑本脚本 -Fix'
    }
  }
}

# ---------- 5. 运行中容器 ----------
Write-Host ''
Write-Host '  5) 运行中的 txgy-* 容器命名空间'
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
  Info 'docker 不可用，跳过'
} else {
  $names = @(& docker ps --format '{{.Names}}' 2>$null | Where-Object { $_ -like 'txgy-*' })
  if ($names.Count -eq 0) {
    Info '没有运行中的 txgy-* 容器'
  } else {
    foreach ($n in $names) {
      $envs = @(& docker inspect $n --format '{{range .Config.Env}}{{println .}}{{end}}' 2>$null)
      $vals = @()
      foreach ($e in $envs) {
        if ($e -match '^SPRING_CLOUD_NACOS_(DISCOVERY|CONFIG)_NAMESPACE=(.*)$') { $vals += $Matches[2] }
      }
      $nonEmpty = @($vals | Where-Object { $_ -ne '' } | Select-Object -Unique)
      $isCore = @('txgy-gateway', 'txgy-system', 'txgy-infra') -contains $n
      if ($nonEmpty.Count -eq 0) {
        Ok "$n namespace=空(public)"
      } elseif ($isCore) {
        Bad "$n namespace=$($nonEmpty -join ',')（应为空/public）→ 重建：docker compose -f deploy/docker-compose.yml -f txgy-module-bidding/deploy-bidding-local.override.yml -p txgy up -d --force-recreate $($n -replace '^txgy-','')"
      } else {
        Warn "$n namespace=$($nonEmpty -join ',')（非 public；网关可能路由不到它）"
      }
    }
  }
}

# ---------- 6. 外来容器 ----------
Write-Host ''
Write-Host '  6) 外来 nacos 容器（会抢 88xx/98xx 端口）'
if (Get-Command docker -ErrorAction SilentlyContinue) {
  $rows = @(& docker ps -a --format '{{.Names}}|{{.Image}}|{{.Status}}|{{.Ports}}' 2>$null)
  $stray = @()
  foreach ($r in $rows) {
    $p = $r -split '\|'
    if ($p.Count -lt 4) { continue }
    if ($p[0] -like 'txgy-*') { continue }
    if ($p[0] -match 'nacos' -or $p[1] -match 'nacos') { $stray += $r }
  }
  if ($stray.Count -eq 0) {
    Ok '无外来 nacos 容器'
  } else {
    foreach ($s in $stray) {
      $p = $s -split '\|'
      Warn "外来容器 $($p[0])  image=$($p[1])  $($p[2])  ports=$($p[3]) → 如不需要：docker rm -f $($p[0])"
    }
  }
}

# ---------- 7. 保护脚本封印 ----------
Write-Host ''
Write-Host '  7) 保护脚本封印（守卫/闸门本身不被静默改写）'
$lockPath = Join-Path $PSScriptRoot 'nacos-config.lock.json'
$protFiles = [ordered]@{
  'deploy/guard-nacos-config.ps1'      = (Join-Path $root 'deploy\guard-nacos-config.ps1')
  'deploy/start.bat'                   = (Join-Path $root 'deploy\start.bat')
  'txgy-module-bidding/.githooks/pre-commit' = (Join-Path $root 'txgy-module-bidding\.githooks\pre-commit')
  'txgy-module-bidding/txgy-module-bidding-server/run-local.cmd' = (Join-Path $root 'txgy-module-bidding\txgy-module-bidding-server\run-local.cmd')
}
function Get-FileSha([string]$p) {
  if (-not (Test-Path -LiteralPath $p)) { return 'MISSING' }
  return (Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash
}
$current = [ordered]@{}
foreach ($k in $protFiles.Keys) { $current[$k] = Get-FileSha $protFiles[$k] }

if ($Seal) {
  $lock = [ordered]@{
    sealedAt   = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
    canonical  = [ordered]@{ nacosNamespace = 'public(空值)'; overrideEmptyNamespaceCount = 3; nacosPorts = @('8848:8848','9848:9848','9849:9849') }
    guardFiles = $current
  }
  WriteNoBom $lockPath ($lock | ConvertTo-Json -Depth 5)
  Ok "已封印保护脚本（$lockPath）；改动保护脚本后请重新 -Seal"
} elseif (-not (Test-Path -LiteralPath $lockPath)) {
  Info '尚未封印（可选）：跑一次 -Seal 生成 deploy/nacos-config.lock.json'
} else {
  $lock = Get-Content -LiteralPath $lockPath -Raw | ConvertFrom-Json
  $changed = @()
  foreach ($k in $current.Keys) {
    $old = $lock.guardFiles.$k
    if ($old -and $old -ne $current[$k]) { $changed += $k }
  }
  if ($changed.Count -eq 0) {
    Ok '保护脚本与封印一致（未被改动）'
  } else {
    foreach ($k in $changed) { Warn "$k 与封印不一致（被改动过）——若非本人所改请检查；确认无误后 -Seal 重新封印" }
  }
}

# ---------- 汇总 ----------
Write-Host ''
if ($script:fail -eq 0) {
  Write-Host '== 结果：PASS（命名空间口径一致，可以启动）==' -ForegroundColor Green
  exit 0
} else {
  Write-Host ("== 结果：FAIL（{0} 项漂移）—— 加 -Fix 自动修复，或按上面提示处理 ==" -f $script:fail) -ForegroundColor Red
  exit 1
}
