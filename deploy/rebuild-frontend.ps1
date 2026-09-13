<#
    rebuild-frontend.ps1 -- one-shot rebuild of the web-antd frontend image.

    WHY THIS SCRIPT EXISTS (found during the 2026-09-11 outage):
      1. package.json pins packageManager=pnpm@11.7.0, but this machine has a global
         npm-installed pnpm 12.3.4. pnpm 12 refuses to run nested (turbo -> pnpm) with
         ERR_PNPM_BAD_PM_VERSION, which silently broke every frontend build.
         This script self-heals by putting a corepack pnpm@11.7.0 shim first on PATH;
         it does NOT modify any global installation.
      2. apps/web-antd/src/router/access.ts uses import.meta.glob('../views/**/*.vue'),
         so ANY .vue file under src/views that fails to compile breaks the whole build,
         even when no route references it.
      3. Do NOT bring this stack up with deploy/docker-compose.yml. That file recreates
         mysql/redis/nacos/gateway with different env var names and without
         container_name, and would damage the running data stack. This script only
         touches the image and the txgy-frontend container.

    Requires Windows PowerShell 5.1+. Written in ASCII on purpose: PS 5.1 decodes
    BOM-less UTF-8 files as ANSI, which corrupts non-ASCII content.

    Usage:
      powershell -ExecutionPolicy Bypass -File deploy\rebuild-frontend.ps1
      powershell -ExecutionPolicy Bypass -File deploy\rebuild-frontend.ps1 -BuildOnly
#>
param(
    [switch]$BuildOnly,
    [int]$HeapMb = 4096
)

$ErrorActionPreference = 'Stop'

$deployDir = $PSScriptRoot
$rootDir   = Split-Path -Parent $deployDir
$vbenDir   = Join-Path $rootDir 'txgy-ui-admin-vben'
$distDir   = Join-Path $vbenDir 'apps\web-antd\dist'

$imageNew  = 'local-frontend:candidate'
$imageLive = 'local-frontend:latest'
$ctxDir    = Join-Path $rootDir '.fe-image-context'

function Step($msg) { Write-Host "`n== $msg ==" -ForegroundColor Cyan }
function Ok($msg)   { Write-Host $msg -ForegroundColor Green }
function Warn($msg) { Write-Host $msg -ForegroundColor Yellow }

# Run a native command without letting its stderr abort the script.
# Windows PowerShell 5.1 turns native stderr into a terminating NativeCommandError
# when $ErrorActionPreference is 'Stop', even when the command succeeds.
function Invoke-Native {
    param([string]$Exe, [string[]]$Arguments, [string]$WorkDir)
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        if ($WorkDir) { Push-Location $WorkDir }
        # Out-String keeps stderr records from rendering as "System.Management.Automation.RemoteException"
        & $Exe @Arguments 2>&1 | Out-String -Stream | ForEach-Object { Write-Host $_ }
        return $LASTEXITCODE
    } finally {
        if ($WorkDir) { Pop-Location }
        $ErrorActionPreference = $prev
    }
}

Write-Host "repo = $rootDir"

# ---------- 1. pnpm version self-heal ----------
Step "1/5 prepare pnpm 11.7.0"
$shimDir = Join-Path $env:TEMP 'txgy-pnpm11-shim'
New-Item -ItemType Directory -Force -Path $shimDir | Out-Null
@"
@ECHO off
corepack pnpm@11.7.0 %*
"@ | Set-Content -Path (Join-Path $shimDir 'pnpm.cmd') -Encoding ASCII
'& corepack pnpm@11.7.0 @args' | Set-Content -Path (Join-Path $shimDir 'pnpm.ps1') -Encoding UTF8

$env:COREPACK_NPM_REGISTRY = 'https://registry.npmmirror.com'
$env:PATH = "$shimDir;$env:PATH"

$pnpmVersion = (& pnpm -v 2>&1 | Out-String).Trim()
Write-Host "pnpm -> $pnpmVersion"
if ($pnpmVersion -notlike '11.*') {
    throw "pnpm self-heal failed: got '$pnpmVersion', expected 11.x. Run: corepack prepare pnpm@11.7.0 --activate"
}

# ---------- 2. build dist ----------
Step "2/5 build web-antd"
$env:NODE_OPTIONS = "--max-old-space-size=$HeapMb"
$env:VITE_BASE_URL = ''
$env:VITE_GLOB_API_URL = '/admin-api'
$env:VITE_APP_CAPTCHA_ENABLE = 'false'
$env:VITE_APP_DOCALERT_ENABLE = 'false'

$buildExit = Invoke-Native -Exe 'pnpm' -Arguments @('run', 'build:antd') -WorkDir $vbenDir
if ($buildExit -ne 0) {
    throw ("frontend build FAILED (exit=$buildExit). " +
           "Most likely a .vue/.ts under src/views does not compile -- access.ts globs " +
           "all of src/views, so an unreferenced broken file still breaks the build. " +
           "Find the file named in the error above, fix or move it out, then re-run.")
}

if (-not (Test-Path (Join-Path $distDir 'index.html'))) { throw "build finished but $distDir\index.html is missing" }
$distFiles = (Get-ChildItem $distDir -Recurse -File).Count
$distMb = '{0:N1}' -f ((Get-ChildItem $distDir -Recurse -File | Measure-Object Length -Sum).Sum / 1MB)
Ok "dist ready: $distFiles files / $distMb MB"

# ---------- 3. build image (minimal context) ----------
# A minimal context is used on purpose: the repo root holds a 1.6 GB .pnpm-store that is
# NOT in .dockerignore, so building with the repo root as context would ship it to the daemon.
Step "3/5 build image $imageNew"
if (Test-Path $ctxDir) { Remove-Item $ctxDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $ctxDir 'deploy\frontend') | Out-Null
Copy-Item $distDir -Destination (Join-Path $ctxDir 'dist') -Recurse -Force
Copy-Item (Join-Path $deployDir 'frontend\nginx.conf') -Destination (Join-Path $ctxDir 'deploy\frontend\nginx.conf') -Force
@"
FROM nginx:1.27-alpine
COPY deploy/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY dist/ /usr/share/nginx/html/
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=5s --retries=10 CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
"@ | Set-Content -Path (Join-Path $ctxDir 'Dockerfile') -Encoding ASCII

$dockerExit = Invoke-Native -Exe 'docker' -Arguments @('build', '-t', $imageNew, $ctxDir)
if ($dockerExit -ne 0) { throw "docker build FAILED (exit=$dockerExit)" }
Ok "image built: $imageNew"

if ($BuildOnly) {
    Remove-Item $ctxDir -Recurse -Force -ErrorAction SilentlyContinue
    Warn "`n-BuildOnly: running containers were NOT touched. To promote manually:"
    Warn "  docker tag $imageNew $imageLive"
    Warn "  docker rm -f txgy-frontend"
    Warn "  docker run -d --name txgy-frontend --network txgy-dev -p 5173:80 --restart unless-stopped $imageLive"
    exit 0
}

# ---------- 4. smoke test on a spare port ----------
Step "4/5 smoke test on port 5174"
& docker rm -f fe-smoke 2>&1 | Out-Null
& docker run -d --name fe-smoke --network txgy-dev -p 5174:80 $imageNew 2>&1 | Out-Null
Start-Sleep -Seconds 6

function Test-Frontend([string]$Base) {
    for ($i = 1; $i -le 12; $i++) {
        try {
            $root = Invoke-WebRequest "$Base/" -UseBasicParsing -TimeoutSec 10
            $spa  = Invoke-WebRequest "$Base/r/wms/md/item" -UseBasicParsing -TimeoutSec 10
            if ($root.StatusCode -eq 200 -and $spa.StatusCode -eq 200) { return $true }
        } catch {
            Start-Sleep -Seconds 3
        }
    }
    return $false
}

if (-not (Test-Frontend 'http://localhost:5174')) {
    & docker logs --tail 20 fe-smoke 2>&1 | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }
    & docker rm -f fe-smoke 2>&1 | Out-Null
    throw "smoke test FAILED. txgy-frontend was left untouched."
}
Ok "smoke test passed (/ and SPA route both 200)"
& docker rm -f fe-smoke 2>&1 | Out-Null

# ---------- 5. promote ----------
Step "5/5 promote to txgy-frontend"
& docker tag $imageNew $imageLive
& docker rm -f txgy-frontend 2>&1 | Out-Null
& docker run -d --name txgy-frontend --network txgy-dev -p 5173:80 --restart unless-stopped $imageLive 2>&1 | Out-Null
Start-Sleep -Seconds 6

if (Test-Frontend 'http://localhost:5173') {
    Ok "live: http://localhost:5173"
} else {
    Warn "post-promotion probe FAILED. Check: docker logs --tail 50 txgy-frontend"
    exit 1
}

Remove-Item $ctxDir -Recurse -Force -ErrorAction SilentlyContinue
Write-Host "`ndone."
