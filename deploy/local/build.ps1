# Build backend jars inside a Docker container, then copy the fat jars back to the host.
# Why: the host has no JDK/Maven, and repo1.maven.org is unreachable from this machine,
# so we build in-container with a China mirror for the "central" repository.
# Usage:
#   powershell -ExecutionPolicy Bypass -File deploy/local/build.ps1
#   powershell -ExecutionPolicy Bypass -File deploy/local/build.ps1 -Modules txgy-gateway

param(
    [string[]]$Modules = @(
        'txgy-gateway',
        'txgy-module-system/txgy-module-system-server',
        'txgy-module-infra/txgy-module-infra-server',
        'txgy-module-member/txgy-module-member-server'
    ),
    [string]$MemLimit = '4g',
    [string]$MavenImage = 'maven:3.9-eclipse-temurin-25'
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$localDir  = $PSScriptRoot
$deployDir = Split-Path -Parent $localDir
$rootDir   = Split-Path -Parent $deployDir
Set-Location -LiteralPath $rootDir
Write-Host "Project root: $rootDir" -ForegroundColor Cyan

function Invoke-Docker {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
    & docker @Args
    if ($LASTEXITCODE -ne 0) { throw "docker $($Args -join ' ') failed (exit=$LASTEXITCODE)" }
}

# 1. containers / volumes for the build
Invoke-Docker rm -f txgy-build | Out-Null
Invoke-Docker volume create txgy-m2 | Out-Null
Invoke-Docker volume create txgy-src | Out-Null

Invoke-Docker run -d --name txgy-build -m $MemLimit -v txgy-m2:/root/.m2 -v txgy-src:/workspace $MavenImage sleep infinity | Out-Null
Write-Host "Build container started (mem=$MemLimit)" -ForegroundColor Cyan

# 2. ship sources into the container as one tar (much faster than docker cp of thousands of files)
$tar = Join-Path $env:TEMP 'txgy-src.tar'
if (Test-Path -LiteralPath $tar) { Remove-Item -LiteralPath $tar -Force }
Write-Host "Packing sources -> $tar" -ForegroundColor Cyan
& tar --exclude='./.git' --exclude='./node_modules' --exclude='./target' --exclude='*/target' -cf $tar -C $rootDir .
if ($LASTEXITCODE -ne 0) { throw "tar packing failed" }
Write-Host ("Tar size: {0:N1} MB" -f ((Get-Item -LiteralPath $tar).Length / 1MB)) -ForegroundColor Cyan

Invoke-Docker exec txgy-build mkdir -p /workspace | Out-Null
Invoke-Docker cp $tar txgy-build:/tmp/txgy-src.tar | Out-Null
Invoke-Docker exec txgy-build tar -xf /tmp/txgy-src.tar -C /workspace | Out-Null
Invoke-Docker exec txgy-build rm -f /tmp/txgy-src.tar | Out-Null
Write-Host "Sources unpacked inside container" -ForegroundColor Cyan

# 3. maven build (skip tests; use mirror settings for central)
$moduleList = ($Modules -join ',')
Write-Host "Building modules: $moduleList" -ForegroundColor Cyan
$mvnArgs = @(
    'exec', 'txgy-build', 'mvn',
    '-B', '-ntp',
    '-s', '/workspace/deploy/local/settings.xml',
    '-Dmaven.test.skip=true',
    '-pl', $moduleList,
    '-am',
    'package'
)
& docker @mvnArgs
if ($LASTEXITCODE -ne 0) {
    Write-Host "Maven build FAILED (exit=$LASTEXITCODE)" -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "Maven build succeeded" -ForegroundColor Green

# 4. copy fat jars back to host target dirs
foreach ($m in $Modules) {
    $dest = Join-Path $rootDir ($m -replace '/', [IO.Path]::DirectorySeparatorChar)
    $dest = Join-Path $dest 'target'
    New-Item -ItemType Directory -Path $dest -Force | Out-Null
    Invoke-Docker exec txgy-build bash -c "ls -1 /workspace/$m/target/*.jar 2>/dev/null | head -5" | Out-Null
    $src = "txgy-build:/workspace/$m/target"
    Write-Host "Copying jars: $src -> $dest" -ForegroundColor Cyan
    Invoke-Docker cp "$src/." $dest | Out-Null
}

Write-Host "Done. Jars are available under each module target/ directory." -ForegroundColor Green
