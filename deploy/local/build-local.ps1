# Build backend jars on the host with the locally installed JDK 25 + Maven.
# Host build is used instead of the maven docker image because the docker pull path
# on this machine is limited to ~0.19 MB/s, which would take hours for the image alone.
# Log file: %TEMP%\txgy_build.log
param(
    [string]$JavaHome = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot',
    [string]$MavenHome = 'D:\tools\apache-maven-3.9.9',
    [string]$MaxHeap = '2g'
)

$ErrorActionPreference = 'Continue'

$localDir  = $PSScriptRoot
$deployDir = Split-Path -Parent $localDir
$rootDir   = Split-Path -Parent $deployDir

Set-Location -LiteralPath $rootDir

$env:JAVA_HOME  = $JavaHome
$env:MAVEN_OPTS = "-Xmx$MaxHeap -Dfile.encoding=UTF-8"

$mvn = Join-Path $MavenHome 'bin\mvn.cmd'
$log = Join-Path $env:TEMP 'txgy_build.log'

Write-Output "root=$rootDir"
Write-Output "java=$(Join-Path $JavaHome 'bin\java.exe')"
Write-Output "mvn=$mvn"
Write-Output "log=$log"

& $mvn -v 2>&1 | Out-File -FilePath $log -Encoding ASCII

$modules = 'txgy-gateway,txgy-module-system/txgy-module-system-server,txgy-module-infra/txgy-module-infra-server,txgy-module-member/txgy-module-member-server'

Write-Output "START_BUILD $(Get-Date -Format o)" | Out-File -FilePath $log -Append -Encoding ASCII

# use an explicit argument array: PowerShell otherwise splits -Dmaven.test.skip=true
$mvnArgs = @(
    '-B',
    '-ntp',
    '-s', (Join-Path $localDir 'settings.xml'),
    '-Dmaven.test.skip=true',
    '-pl', $modules,
    '-am',
    'package'
)

& $mvn $mvnArgs 2>&1 | Out-File -FilePath $log -Append -Encoding ASCII

$code = $LASTEXITCODE
Write-Output "BUILD_EXIT=$code $(Get-Date -Format o)" | Out-File -FilePath $log -Append -Encoding ASCII
exit $code
