# Kill leftover compose/buildx CLIs, fully restart Docker Desktop, wait until engine is ready.
$exe = Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\Docker Desktop.exe'
if (-not (Test-Path $exe)) { Write-Host 'ERR: Docker Desktop exe not found: ' $exe; exit 1 }

taskkill /IM docker-compose.exe /F 2>$null | Out-Null
taskkill /IM docker-buildx.exe /F 2>$null | Out-Null
taskkill /IM "Docker Desktop.exe" /T /F 2>$null | Out-Null
taskkill /IM com.docker.backend.exe /F 2>$null | Out-Null
Write-Host 'old processes killed, waiting 6s...'
Start-Sleep -Seconds 6

Start-Process $exe
Write-Host 'Docker Desktop starting, waiting for engine (up to 3 min)...'

$ok = $false
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 3
    cmd /c 'docker info >nul 2>&1'
    if ($LASTEXITCODE -eq 0) { $ok = $true; break }
}
if ($ok) { Write-Host 'DOCKER_READY' } else { Write-Host 'NOT_READY_YET' }
