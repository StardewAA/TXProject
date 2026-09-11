# Launch `docker compose up -d` (NO build - reuse existing images) in background.
$work = 'D:\txgy\deploy'
$logs = Join-Path $PSScriptRoot 'logs'
New-Item -ItemType Directory -Force -Path $logs | Out-Null
$proc = Start-Process -FilePath 'docker' `
    -ArgumentList @('compose', 'up', '-d', '--remove-orphans') `
    -WorkingDirectory $work `
    -RedirectStandardOutput (Join-Path $logs 'up3.log') `
    -RedirectStandardError (Join-Path $logs 'up3.err.log') `
    -PassThru -WindowStyle Hidden
Set-Content -Path (Join-Path $logs 'up3.pid') -Value $proc.Id
Write-Host ('compose up (no build) launched PID=' + $proc.Id)
