# Launch `docker compose up -d --build` from ASCII junction path in background.
$work = 'D:\txgy\deploy'
$logs = Join-Path $PSScriptRoot 'logs'
New-Item -ItemType Directory -Force -Path $logs | Out-Null
$proc = Start-Process -FilePath 'docker' `
    -ArgumentList @('compose', 'up', '-d', '--build', '--remove-orphans') `
    -WorkingDirectory $work `
    -RedirectStandardOutput (Join-Path $logs 'up2.log') `
    -RedirectStandardError (Join-Path $logs 'up2.err.log') `
    -PassThru -WindowStyle Hidden
Set-Content -Path (Join-Path $logs 'up2.pid') -Value $proc.Id
Write-Host ('compose up launched PID=' + $proc.Id)
