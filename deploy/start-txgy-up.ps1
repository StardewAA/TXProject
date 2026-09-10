# Diagnose non-ASCII path build failure, then launch compose from an ASCII (junction) path in background.
$proj = Split-Path $PSScriptRoot -Parent   # real Chinese path
$jn   = 'D:\txgy'                          # ASCII junction path

function Run-Cmd([string]$cmdLine) {
    cmd /c $cmdLine
    return $LASTEXITCODE
}

# 0) ensure docker daemon
if ((Run-Cmd 'docker info > nul 2>&1') -ne 0) { Write-Host 'ERR: Docker daemon not ready. Start Docker Desktop first.'; exit 1 }
Write-Host '[0/4] Docker daemon ready'

# 1) ASCII-path sanity build test (isolate root cause)
$t = 'D:\dkrtest'
if (-not (Test-Path $t)) { New-Item -ItemType Directory -Path $t | Out-Null }
Set-Content -Path (Join-Path $t 'Dockerfile') -Value 'FROM alpine:latest'
$code = Run-Cmd ("docker build -t dkrtest . > D:\dkrtest\build.log 2>&1 & cd /d " + $t)
$code = $LASTEXITCODE
if ($code -ne 0) {
    Get-Content (Join-Path $t 'build.log') -ErrorAction SilentlyContinue
    Write-Host ("ASCII-path build FAILED (exit=" + $code + "). Root cause is NOT the Chinese project path.")
    exit 1
}
Write-Host '[1/4] ASCII-path build OK -> Chinese path is the likely cause'

# 2) create junction if missing (PowerShell native Junction, no cmd encoding issue)
if (Test-Path $jn) {
    $li = Get-Item $jn
    if ($li.LinkType -eq 'Junction') { Remove-Item $jn -Force }
    else { Write-Host "ERR: $jn exists as a real directory; please remove it manually."; exit 1 }
}
try {
    New-Item -ItemType Junction -Path $jn -Target $proj | Out-Null
} catch {
    Write-Host ("ERR: create junction failed: " + $_.Exception.Message)
    exit 1
}
Write-Host '[2/4] Junction ready: D:\txgy'

# 3) launch compose up -d --build from the ASCII working dir, in background
$work = Join-Path $jn 'deploy'
if (-not (Test-Path $work)) { Write-Host 'ERR: deploy not found under junction'; exit 1 }
$logs = Join-Path $PSScriptRoot 'logs'
New-Item -ItemType Directory -Force -Path $logs | Out-Null
$proc = Start-Process -FilePath 'docker' `
    -ArgumentList @('compose', 'up', '-d', '--build') `
    -WorkingDirectory $work `
    -RedirectStandardOutput (Join-Path $logs 'compose-up.log') `
    -RedirectStandardError (Join-Path $logs 'compose-up.err.log') `
    -PassThru -WindowStyle Hidden
Set-Content -Path (Join-Path $logs 'compose-up.pid') -Value $proc.Id
Write-Host ('[3/4] compose up launched (PID ' + $proc.Id + ') from ' + $work)
Write-Host ('[4/4] Logs: ' + (Join-Path $logs 'compose-up.log'))
