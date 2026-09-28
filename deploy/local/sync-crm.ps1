<#
.SYNOPSIS
    一键把本地环境同步到最新 CRM（报价管理 + 商机阶段）。

.DESCRIPTION
    依次执行：切到 crm 分支并拉取 -> 构建前端 dist -> 编译后端 jar -> 打镜像
    -> 重启容器 -> 导入数据库修复 SQL -> 清前端缓存 -> 验证产物。
    后端编译在 Docker 容器内用 Maven 完成，本机无需 JDK/Maven。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File deploy\local\sync-crm.ps1
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File deploy\local\sync-crm.ps1 -SkipBackend -SkipSql
#>
param(
    [switch]$SkipPull,
    [switch]$SkipFrontend,
    [switch]$SkipBackend,
    [switch]$SkipSql
)

$ErrorActionPreference = 'Continue'
$ProgressPreference    = 'SilentlyContinue'

$localDir  = $PSScriptRoot
$deployDir = Split-Path -Parent $localDir
$rootDir   = Split-Path -Parent $deployDir

$composeLocal = Join-Path $localDir  'docker-compose.local.yml'
$composeMain  = Join-Path $deployDir 'docker-compose.yml'
$envFile      = Join-Path $deployDir '.env'

function Step { param($n, $msg) Write-Host ''; Write-Host "[$n] $msg" -ForegroundColor Cyan }
function Ok   { param($msg) Write-Host "    [OK] $msg" -ForegroundColor Green }
function Warn { param($msg) Write-Host "    [!!] $msg" -ForegroundColor Yellow }
function Bad  { param($msg) Write-Host "    [XX] $msg" -ForegroundColor Red }

Set-Location -LiteralPath $rootDir
Write-Host '==============================================' -ForegroundColor Cyan
Write-Host ' CRM 同步：报价管理 + 商机阶段' -ForegroundColor Cyan
Write-Host " 项目根目录: $rootDir" -ForegroundColor Cyan
Write-Host '==============================================' -ForegroundColor Cyan

# ---------------------------------------------------------------- 1. 代码
Step 1 '拉取最新代码（crm 分支）'
if ($SkipPull) {
    Warn '已跳过（-SkipPull）'
} else {
    $branch = ''
    try { $branch = (& git rev-parse --abbrev-ref HEAD).Trim() } catch { Bad 'git 不可用，请先安装 Git'; }

    if ($branch -ne 'crm') {
        Write-Host "    当前分支: $branch （应为 crm）" -ForegroundColor Yellow
        $dirty = & git status --porcelain
        if ($dirty) {
            Warn '工作区有未提交改动，切换分支可能失败。请先 commit 或 stash。'
        }
        & git checkout crm 2>&1 | Out-Null
        $branch = (& git rev-parse --abbrev-ref HEAD).Trim()
        if ($branch -eq 'crm') { Ok '已切换到 crm 分支' } else { Bad "切换失败，当前仍在 $branch，请手动处理" }
    } else {
        Ok '已在 crm 分支'
    }

    & git fetch origin --prune 2>&1 | Out-Null
    & git pull --ff-only 2>&1 | ForEach-Object { Write-Host "    $_" }
    Ok '代码已更新'
}

# ---------------------------------------------------------------- 2. 前端
Step 2 '构建前端 dist（报价页面 + 商机阶段）'
if ($SkipFrontend) {
    Warn '已跳过（-SkipFrontend）'
} else {
    $uiDir = Join-Path $rootDir 'txgy-ui-admin-vben'
    if (-not (Test-Path $uiDir)) {
        Bad "找不到前端目录: $uiDir"
    } else {
        $hasPnpm = $null -ne (Get-Command pnpm -ErrorAction SilentlyContinue)
        if (-not $hasPnpm) {
            Bad '未检测到 pnpm，请先安装（npm i -g pnpm），或跳过前端步骤手动构建'
        } else {
            Set-Location -LiteralPath $uiDir
            Write-Host '    pnpm install ...' -ForegroundColor Gray
            & pnpm install
            Write-Host '    构建 web-antd ...' -ForegroundColor Gray
            & pnpm --filter web-antd build
            if ($LASTEXITCODE -eq 0) { Ok '前端 dist 已重建' } else { Bad "前端构建失败 (exit=$LASTEXITCODE)" }
            Set-Location -LiteralPath $rootDir
        }
    }
}

# ---------------------------------------------------------------- 3. 后端
Step 3 '编译后端 jar + 打镜像（CRM）'
if ($SkipBackend) {
    Warn '已跳过（-SkipBackend）'
} else {
    $buildPs1    = Join-Path $localDir 'build.ps1'
    $buildImgPs1 = Join-Path $localDir 'build-images.ps1'

    if (Test-Path $buildPs1) {
        Write-Host '    编译中（Docker 内 Maven，首次较慢，请耐心）...' -ForegroundColor Gray
        & powershell -ExecutionPolicy Bypass -File $buildPs1 -Modules @('txgy-module-crm/txgy-module-crm-server')
        if ($LASTEXITCODE -eq 0) { Ok 'jar 编译完成' } else { Bad "编译失败 (exit=$LASTEXITCODE)，详见上方日志" }
    } else {
        Bad "找不到 $buildPs1"
    }

    if (Test-Path $buildImgPs1) {
        Write-Host '    打镜像中 ...' -ForegroundColor Gray
        & powershell -ExecutionPolicy Bypass -File $buildImgPs1 -Services @('crm')
        Ok '镜像已构建'
    } else {
        Bad "找不到 $buildImgPs1"
    }
}

# ---------------------------------------------------------------- 4. 重启容器
Step 4 '重启容器'
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Bad '未检测到 docker'
} else {
    Write-Host '    重启 txgy-crm ...' -ForegroundColor Gray
    & docker compose -f $composeLocal --env-file $envFile up -d --build crm
    if ($LASTEXITCODE -eq 0) { Ok 'txgy-crm 已启动' } else { Bad "CRM 容器启动失败 (exit=$LASTEXITCODE)" }

    # 前端容器：主 compose 的 frontend（端口 5173）
    $fe = & docker ps -a --format '{{.Names}}' 2>$null | Where-Object { $_ -match 'frontend' }
    if ($fe) {
        Write-Host "    发现前端容器: $($fe -join ', ')，重建中 ..." -ForegroundColor Gray
        & docker compose -f $composeMain --env-file $envFile up -d --build frontend
        if ($LASTEXITCODE -eq 0) { Ok '前端容器已更新' } else { Bad "前端容器更新失败 (exit=$LASTEXITCODE)" }
    } else {
        Warn '未发现运行中的前端容器（跳过）。若是 pnpm dev 本地起的前端，重启 dev server 即可。'
    }
}

# ---------------------------------------------------------------- 5. SQL
Step 5 '导入数据库修复 SQL'
if ($SkipSql) {
    Warn '已跳过（-SkipSql）'
} else {
    $sqlFiles = @(
        '2026-09-11-crm-quotation.sql',
        '2026-09-12-crm-customer-type.sql',
        '2026-09-14-crm-after-sale-visit-record-status.sql',
        '2026-09-15-crm-business-status.sql'
    )
    $repairDir = Join-Path $deployDir 'mysql\repair'

    # 自动识别 MySQL 容器
    $mysqlCtr = $null
    foreach ($c in @('txgy-mysql', 'deploy-mysql-1', 'mysql')) {
        $hit = & docker ps --format '{{.Names}}' 2>$null | Where-Object { $_ -eq $c }
        if ($hit) { $mysqlCtr = $c; break }
    }
    if (-not $mysqlCtr) {
        Bad '未找到运行中的 MySQL 容器（txgy-mysql / deploy-mysql-1 / mysql）'
    } else {
        Ok "MySQL 容器: $mysqlCtr"
        $db = 'ruoyi-vue-pro'
        foreach ($f in $sqlFiles) {
            $src = Join-Path $repairDir $f
            if (-not (Test-Path $src)) { Warn "不存在，跳过: $f"; continue }
            & docker cp $src "${mysqlCtr}:/tmp/$f" | Out-Null
            & docker exec $mysqlCtr sh -c "mysql -uroot -proot --default-character-set=utf8mb4 $db < /tmp/$f" 2>&1 |
                Where-Object { $_ -notmatch 'Using a password' } | ForEach-Object { Write-Host "    $_" }
            & docker exec $mysqlCtr sh -c "rm -f /tmp/$f" | Out-Null
            Ok "已导入 $f"
        }
    }
}

# ---------------------------------------------------------------- 6. 清缓存
Step 6 '清理前端缓存'
$vite = Join-Path $rootDir 'txgy-ui-admin-vben\node_modules\.vite'
if (Test-Path $vite) {
    Remove-Item -LiteralPath $vite -Recurse -Force -ErrorAction SilentlyContinue
    Ok '已删除 node_modules/.vite'
} else {
    Warn '无 .vite 缓存（无需清理）'
}

# ---------------------------------------------------------------- 7. 验证
Step 7 '验证产物'
$crmCtr = & docker ps --format '{{.Names}}' 2>$null | Where-Object { $_ -match 'crm' } | Select-Object -First 1
if ($crmCtr) {
    $hit = & docker exec $crmCtr sh -c "unzip -l /app/app.jar 2>/dev/null | grep -ci quotation" 2>$null
    if ($hit -and [int]$hit -gt 0) { Ok "后端 jar 含报价管理（$hit 处）" }
    else { Warn "后端 jar 未检出 quotation，请确认 build.ps1 是否成功" }
} else {
    Warn '未找到运行中的 crm 容器'
}

$feCtr = & docker ps --format '{{.Names}}' 2>$null | Where-Object { $_ -match 'frontend' } | Select-Object -First 1
if ($feCtr) {
    $hit2 = & docker exec $feCtr sh -c "ls /usr/share/nginx/html/js 2>/dev/null | grep -ci quotation" 2>$null
    if ($hit2 -and [int]$hit2 -gt 0) { Ok "前端 dist 含报价页面（$hit2 处）" }
    else { Warn '前端 dist 未检出 quotation，请确认 pnpm build 是否成功' }
}

Write-Host ''
Write-Host '==============================================' -ForegroundColor Cyan
Write-Host ' 完成。请浏览器 Ctrl+Shift+R 强刷，或退出重新登录。' -ForegroundColor Cyan
Write-Host ' 访问地址: http://localhost:5173' -ForegroundColor Cyan
Write-Host ' 详细说明见: deploy/SYNC-CRM-GUIDE.md' -ForegroundColor Cyan
Write-Host '==============================================' -ForegroundColor Cyan

Set-Location -LiteralPath $rootDir
