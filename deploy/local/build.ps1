# Build backend jars inside a Docker container, then copy the fat jars back to the host.
# Why: the host has no JDK/Maven, and repo1.maven.org is unreachable from this machine,
# so we build in-container with a China mirror for the "central" repository.
# Usage:
#   powershell -ExecutionPolicy Bypass -File deploy/local/build.ps1
#   powershell -ExecutionPolicy Bypass -File deploy/local/build.ps1 -Modules txgy-gateway
#   powershell -ExecutionPolicy Bypass -File deploy/local/build.ps1 -Modules txgy-module-training/txgy-module-training-server
#
# 注意：docker 参数必须通过 -Command 以数组传入。
# 不要改回 ValueFromRemainingArguments：那样 PowerShell 会把 "-v" 当成公共参数
# -Verbose 的缩写来绑定，出现两个 -v 时直接报「参数 v 被指定了多次」而中断构建。

param(
    [string[]]$Modules = @(
        'txgy-gateway',
        'txgy-module-system/txgy-module-system-server',
        'txgy-module-infra/txgy-module-infra-server',
        'txgy-module-member/txgy-module-member-server'
    ),
    [string]$MemLimit = '4g',
    [string]$MavenImage = 'm.daocloud.io/docker.io/library/maven:3.9-eclipse-temurin-25',
    [string]$JarDirectory = ''
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$localDir  = $PSScriptRoot
$deployDir = Split-Path -Parent $localDir
$rootDir   = Split-Path -Parent $deployDir
$jarDirectory = if ($JarDirectory) {
    [IO.Path]::GetFullPath($JarDirectory)
} else {
    Join-Path $localDir 'jars'
}
Set-Location -LiteralPath $rootDir
Write-Host "Project root: $rootDir" -ForegroundColor Cyan

function Invoke-Docker {
    param([Parameter(Mandatory = $true)][string[]]$Command)
    & docker @Command
    if ($LASTEXITCODE -ne 0) { throw "docker $($Command -join ' ') failed (exit=$LASTEXITCODE)" }
}

# 1. containers / volumes for the build
try {
    $previousEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & docker rm -f txgy-build 2>&1 | Out-Null
    $ErrorActionPreference = $previousEap

    Invoke-Docker -Command @('volume', 'create', 'txgy-m2') | Out-Null
    Invoke-Docker -Command @('volume', 'create', 'txgy-src') | Out-Null

    Invoke-Docker -Command @(
        'run', '-d', '--name', 'txgy-build', '-m', $MemLimit,
        '-v', 'txgy-m2:/root/.m2', '-v', 'txgy-src:/workspace',
        $MavenImage, 'sleep', 'infinity'
    ) | Out-Null
    Write-Host "Build container started (mem=$MemLimit)" -ForegroundColor Cyan

    # 2. ship sources into the container as one tar (much faster than docker cp of thousands of files)
    $tar = Join-Path $env:TEMP 'txgy-src.tar'
    if (Test-Path -LiteralPath $tar) { Remove-Item -LiteralPath $tar -Force }
    Write-Host "Packing sources -> $tar" -ForegroundColor Cyan
    & tar --exclude='./.git' --exclude='./node_modules' --exclude='./target' --exclude='*/target' -cf $tar -C $rootDir .
    if ($LASTEXITCODE -ne 0) { throw "tar packing failed" }
    Write-Host ("Tar size: {0:N1} MB" -f ((Get-Item -LiteralPath $tar).Length / 1MB)) -ForegroundColor Cyan

    Invoke-Docker -Command @('exec', 'txgy-build', 'mkdir', '-p', '/workspace') | Out-Null
    Invoke-Docker -Command @('cp', $tar, 'txgy-build:/tmp/txgy-src.tar') | Out-Null
    Invoke-Docker -Command @('exec', 'txgy-build', 'tar', '-xf', '/tmp/txgy-src.tar', '-C', '/workspace') | Out-Null
    Invoke-Docker -Command @('exec', 'txgy-build', 'rm', '-f', '/tmp/txgy-src.tar') | Out-Null
    Write-Host "Sources unpacked inside container" -ForegroundColor Cyan

    # 3. maven build (skip tests; use mirror settings for central)
    $moduleList = ($Modules -join ',')
    Write-Host "Building modules: $moduleList" -ForegroundColor Cyan
    Invoke-Docker -Command @(
        'exec', 'txgy-build', 'mvn',
        '-B', '-ntp',
        '-s', '/workspace/deploy/local/settings.xml',
        '-Dmaven.test.skip=true',
        '-pl', $moduleList,
        '-am',
        'package'
    )
    Write-Host "Maven build succeeded" -ForegroundColor Green

    # 4. copy fat jars back to host target dirs
    New-Item -ItemType Directory -Path $jarDirectory -Force | Out-Null
    $serviceJarNames = @{
        'txgy-gateway' = 'gateway'
        'txgy-module-system/txgy-module-system-server' = 'system'
        'txgy-module-infra/txgy-module-infra-server' = 'infra'
        'txgy-module-member/txgy-module-member-server' = 'member'
        'txgy-module-bpm/txgy-module-bpm-server' = 'bpm'
        'txgy-module-crm/txgy-module-crm-server' = 'crm'
        'txgy-module-ai/txgy-module-ai-server' = 'ai'
        'txgy-module-hrm/txgy-module-hrm-server' = 'hrm'
        'txgy-module-im/txgy-module-im-server' = 'im'
    }
    foreach ($m in $Modules) {
        $dest = Join-Path $rootDir ($m -replace '/', [IO.Path]::DirectorySeparatorChar)
        $dest = Join-Path $dest 'target'
        New-Item -ItemType Directory -Path $dest -Force | Out-Null
        Invoke-Docker -Command @('exec', 'txgy-build', 'bash', '-c', "ls -1 /workspace/$m/target/*.jar 2>/dev/null | head -5") | Out-Null
        $src = "txgy-build:/workspace/$m/target"
        Write-Host "Copying jars: $src -> $dest" -ForegroundColor Cyan
        Invoke-Docker -Command @('cp', "$src/.", $dest) | Out-Null

        if ($serviceJarNames.ContainsKey($m)) {
            $jar = Get-ChildItem -LiteralPath $dest -Filter '*.jar' |
                Where-Object { $_.Name -notlike 'original-*.jar' } |
                Sort-Object LastWriteTime -Descending |
                Select-Object -First 1
            if (-not $jar) {
                throw "No runnable jar found in $dest"
            }
            $runtimeJar = Join-Path $jarDirectory ($serviceJarNames[$m] + '.jar')
            Copy-Item -LiteralPath $jar.FullName -Destination $runtimeJar -Force
            Write-Host "Runtime jar ready: $runtimeJar" -ForegroundColor Green
        }
    }
    Write-Host "Done. Jars are available under each module target/ directory." -ForegroundColor Green
} finally {
    $ErrorActionPreference = 'Continue'
    & docker rm -f txgy-build 2>&1 | Out-Null
    if ($tar -and (Test-Path -LiteralPath $tar)) {
        Remove-Item -LiteralPath $tar -Force -ErrorAction SilentlyContinue
    }
}
