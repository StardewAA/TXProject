$ErrorActionPreference = 'Stop'

$root = 'D:\codexshuju\tianxin-pipe-project'
$mavenHome = Join-Path $root 'tools\apache-maven-3.9.11'
$nodeHome = Join-Path $root 'tools\node-v24.19.0-win-x64'
$composeFile = Join-Path $root 'docker\docker-compose.redis.yml'

$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
$parts = @($userPath -split ';' | Where-Object { $_ -and $_.Trim() })
foreach ($entry in @((Join-Path $mavenHome 'bin'), $nodeHome)) {
    if ($parts -notcontains $entry) {
        $parts = @($entry) + $parts
    }
}

[Environment]::SetEnvironmentVariable('MAVEN_HOME', $mavenHome, 'User')
[Environment]::SetEnvironmentVariable('M2_HOME', $mavenHome, 'User')
[Environment]::SetEnvironmentVariable('NODE_HOME', $nodeHome, 'User')
[Environment]::SetEnvironmentVariable('PNPM_HOME', $nodeHome, 'User')
[Environment]::SetEnvironmentVariable('Path', ($parts -join ';'), 'User')

Write-Host ''
Write-Host 'Installed versions:'
& (Join-Path $mavenHome 'bin\mvn.cmd') --version
& (Join-Path $nodeHome 'node.exe') --version
& (Join-Path $nodeHome 'npm.cmd') --version
& (Join-Path $nodeHome 'pnpm.cmd') --version

$dockerCandidates = @(
    (Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe'),
    (Join-Path $env:ProgramFiles 'Docker\Docker\resources\bin\docker.exe')
)
$docker = $dockerCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $docker) {
    $docker = (Get-Command docker.exe -ErrorAction Stop).Source
}

& $docker compose -f $composeFile pull
& $docker compose -f $composeFile up -d
& $docker compose -f $composeFile ps

Write-Host ''
Write-Host 'Environment setup completed. Open a new PowerShell window to use mvn, node and pnpm.' -ForegroundColor Green
