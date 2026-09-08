# Ensure Docker engine is running, then run `docker compose up -d --build` in background (log to logs/compose-up.log)
Set-Location $PSScriptRoot  # deploy dir

function Test-Docker {
    cmd /c "docker info > nul 2>&1"
    return ($LASTEXITCODE -eq 0)
}

# 1) Ensure docker daemon is ready
if (-not (Test-Docker)) {
    $dd = 'C:\Program Files\Docker\Docker\Docker Desktop.exe'
    if (-not (Test-Path $dd)) { Write-Error 'Docker daemon not running and Docker Desktop.exe not found'; exit 1 }
    Write-Host '[1/3] Starting Docker Desktop...'
    Start-Process $dd
    $ok = $false
    for ($i = 0; $i -lt 80; $i++) {  # wait up to 240s
        Start-Sleep -Seconds 3
        if (Test-Docker) { $ok = $true; break }
    }
    if (-not $ok) { Write-Error 'Docker Desktop not ready within 240s'; exit 1 }
} else {
    Write-Host '[1/3] Docker daemon already ready'
}

# 2) Run docker compose up -d --build in background, log to logs/compose-up.log
Write-Host '[2/3] Running docker compose up -d --build in background...'
$logs = Join-Path $PSScriptRoot 'logs'
New-Item -ItemType Directory -Force -Path $logs | Out-Null
$stdout = Join-Path $logs 'compose-up.log'
$stderr = Join-Path $logs 'compose-up.err.log'
$proc = Start-Process -FilePath 'docker' `
    -ArgumentList @('compose', 'up', '-d', '--build') `
    -WorkingDirectory $PSScriptRoot `
    -RedirectStandardOutput $stdout `
    -RedirectStandardError $stderr `
    -PassThru -WindowStyle Hidden
Set-Content -Path (Join-Path $logs 'compose-up.pid') -Value $proc.Id

Write-Host ('[3/3] Compose up started in background (PID ' + $proc.Id + '). Watch: Get-Content -Wait deploy\logs\compose-up.log')
