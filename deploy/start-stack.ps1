<#
    start-stack.ps1 -- bring the local Docker stack up in dependency layers.

    WHY THIS SCRIPT EXISTS (learned the hard way on 2026-09-14):
      After `wsl --shutdown` (needed to apply .wslconfig) every container was started at
      once. Nacos and seven Java services competed for CPU/memory, so Nacos never finished
      booting: it logged "Nacos Server is starting..." in a loop for 6+ minutes, never bound
      port 8848, and every client container crash-looped against it.
      Starting Nacos ALONE first, then the rest in layers, brings the stack up cleanly.
      That observation is the entire reason for the layering below -- please keep it.

    LAYERS (a layer only starts once the previous layer is healthy):
      base      mysql, redis, frontend      (no Java, ready in seconds)
      registry  nacos                        (must be alone: everything else depends on it)
      core      infra, system                (register with nacos)
      domain    gateway, mes, bpm, erp, wms  (register with nacos, routed by gateway)

    USAGE:
      powershell -ExecutionPolicy Bypass -File deploy\start-stack.ps1
      powershell -ExecutionPolicy Bypass -File deploy\start-stack.ps1 -TimeoutSec 420
      powershell -ExecutionPolicy Bypass -File deploy\start-stack.ps1 -Only nacos,mes

    BEHAVIOUR:
      * Already-healthy containers are left untouched (safe to re-run).
      * A container that is running but NOT healthy is restarted -- `docker start` would be
        a no-op there, which is why that case is handled explicitly.
      * Containers without a healthcheck count as healthy once running.
      * Written in ASCII on purpose: PS 5.1 decodes BOM-less UTF-8 as ANSI.

    EXIT CODE: 0 = every selected container is healthy, 1 = something did not make it.

    NOTE: this script only calls `docker start` / `docker restart` on EXISTING containers.
      It never runs `docker compose up`, because the containers here were created from a
      mixture of compose files and recreating them would risk the data stack.
#>
param(
    [int]$TimeoutSec = 300,
    [int]$PollSec = 5,
    [string[]]$Only = @()
)

$ErrorActionPreference = 'Stop'

$LayerDefs = @(
    @{ Name = 'base';     Services = @('mysql', 'redis', 'frontend') },
    @{ Name = 'registry'; Services = @('nacos') },
    @{ Name = 'core';     Services = @('infra', 'system') },
    @{ Name = 'domain';   Services = @('gateway', 'mes', 'bpm', 'erp', 'wms') }
)

# --- native-command helper -------------------------------------------------
# With $ErrorActionPreference = 'Stop', Windows PowerShell 5.1 turns ANY stderr output
# from a native command into a terminating NativeCommandError. Probing for a container
# that may not exist is a normal, expected failure here, so every docker call goes
# through this wrapper: stderr is captured as data and the exit code is returned.
# (Verified: without this, `docker inspect missing-name` aborts the whole script.)
function Invoke-Docker {
    param([string[]]$DockerArgs)

    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'SilentlyContinue'
    try {
        $lines = @(& docker @DockerArgs 2>&1)
        $code = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $prev
    }
    return @{
        Lines = @($lines | ForEach-Object { "$_" })
        Code  = $code
    }
}

function Get-CName([string]$svc) { "deploy-$svc-1" }

function Get-CState([string]$name) {
    $r = Invoke-Docker @('inspect', $name, '--format',
        '{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}')

    if ($r.Code -ne 0 -or $r.Lines.Count -eq 0) {
        return @{ Status = 'missing'; Health = 'none' }
    }
    $parts = $r.Lines[0].Trim() -split '\|'
    if ($parts.Count -lt 2) {
        return @{ Status = 'unknown'; Health = 'none' }
    }
    return @{ Status = $parts[0]; Health = $parts[1] }
}

function Test-CHelp([string]$name) {
    $s = Get-CState $name
    if ($s.Status -ne 'running') { return $false }
    if ($s.Health -eq 'healthy' -or $s.Health -eq 'none') { return $true }
    return $false
}

# --- preflight -------------------------------------------------------------
$info = Invoke-Docker @('info', '--format', '{{.ServerVersion}}')
if ($info.Code -ne 0) {
    Write-Host 'ERROR: the Docker daemon is not reachable.' -ForegroundColor Red
    Write-Host '       Start Docker Desktop, wait for "Engine running", then re-run this script.'
    exit 1
}
Write-Host ("Docker {0} is reachable. Starting stack in {1} layer(s), per-layer timeout {2}s." -f `
        $info.Lines[0], $LayerDefs.Count, $TimeoutSec)

$anyFailed = $false

foreach ($layer in $LayerDefs) {
    $svcs = @($layer.Services)
    if ($Only.Count -gt 0) {
        $svcs = @($svcs | Where-Object { $Only -contains $_ })
    }
    if ($svcs.Count -eq 0) { continue }

    Write-Host ''
    Write-Host ("=== layer [{0}]: {1} ===" -f $layer.Name, ($svcs -join ', ')) -ForegroundColor Cyan

    foreach ($svc in $svcs) {
        $name = Get-CName $svc
        $s = Get-CState $name

        if ($s.Status -eq 'missing') {
            Write-Host ("  {0,-18} MISSING (container does not exist)" -f $name) -ForegroundColor Red
            $anyFailed = $true
            continue
        }
        if (Test-CHelp $name) {
            Write-Host ("  {0,-18} already healthy" -f $name) -ForegroundColor DarkGray
            continue
        }
        if ($s.Status -eq 'running' -and $s.Health -eq 'unhealthy') {
            # the healthcheck is failing -- `docker start` would be a no-op, so restart instead
            $null = Invoke-Docker @('restart', $name)
            Write-Host ("  {0,-18} was running/unhealthy -> restarted" -f $name) -ForegroundColor Yellow
        }
        elseif ($s.Status -eq 'running') {
            # running and still inside its health start-period: `docker start` is a no-op and a
            # restart would throw away a good boot. (Learned the hard way: this used to restart
            # any container that was merely 'health: starting', e.g. right after a rebuild.)
            Write-Host ("  {0,-18} is still starting -> waiting" -f $name) -ForegroundColor DarkGray
        }
        else {
            $null = Invoke-Docker @('start', $name)
            Write-Host ("  {0,-18} was {1} -> started" -f $name, $s.Status) -ForegroundColor Yellow
        }
    }

    # --- wait for this layer to become healthy ---
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ($true) {
        $pending = @()
        foreach ($svc in $svcs) {
            $name = Get-CName $svc
            if (-not (Test-CHelp $name)) {
                $s = Get-CState $name
                $pending += ('{0}({1}/{2})' -f $name, $s.Status, $s.Health)
            }
        }
        if ($pending.Count -eq 0) {
            Write-Host ("  -> layer [{0}] healthy" -f $layer.Name) -ForegroundColor Green
            break
        }
        if ((Get-Date) -ge $deadline) {
            Write-Host ("  -> TIMEOUT after {0}s; still pending: {1}" -f $TimeoutSec, ($pending -join ', ')) -ForegroundColor Red
            if ($svcs -contains 'nacos') {
                Write-Host '     Nacos is the usual culprit. Inspect it with:' -ForegroundColor Yellow
                Write-Host '       docker logs deploy-nacos-1 --tail 50' -ForegroundColor Yellow
                Write-Host '     If it is stuck logging "Nacos Server is starting...", stop the client' -ForegroundColor Yellow
                Write-Host '     containers so Nacos can boot alone, then re-run this script.' -ForegroundColor Yellow
            }
            $anyFailed = $true
            break
        }
        Start-Sleep -Seconds $PollSec
    }
}

# --- summary ---------------------------------------------------------------
Write-Host ''
Write-Host '=== final state ===' -ForegroundColor Cyan
$allHealthy = $true
foreach ($layer in $LayerDefs) {
    foreach ($svc in $layer.Services) {
        if ($Only.Count -gt 0 -and -not ($Only -contains $svc)) { continue }
        $name = Get-CName $svc
        $s = Get-CState $name
        $ok = Test-CHelp $name
        if (-not $ok) { $allHealthy = $false }
        $color = if ($ok) { 'Green' } else { 'Red' }
        Write-Host ("  {0,-18} {1,-12} health={2}" -f $name, $s.Status, $s.Health) -ForegroundColor $color
    }
}

Write-Host ''
if ($allHealthy -and -not $anyFailed) {
    Write-Host 'All selected containers are healthy.' -ForegroundColor Green
    exit 0
}
Write-Host 'Some containers are NOT healthy -- see the pending list above.' -ForegroundColor Red
exit 1
