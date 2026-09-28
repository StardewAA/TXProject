<#
  Local launcher for bidding-server (port 48110) used by the BM25 A/B retrieval runs.

  Usage:
    powershell -NoProfile -ExecutionPolicy Bypass -File start-bidding-local.ps1 -Bm25 false -CandidateFactor 4
    powershell -NoProfile -ExecutionPolicy Bypass -File start-bidding-local.ps1 -Bm25 true  -CandidateFactor 1

  Notes:
    * ASCII only on purpose: Windows PowerShell 5.1 reads .ps1 as ANSI, non-ASCII literals would be mangled.
    * All paths are derived from $PSScriptRoot, so the workspace root may contain any characters.
    * DASHSCOPE_API_KEY must already be set in the caller's environment (exported from a gitignored env
      file by the caller). It is never written to disk nor echoed here.
#>
param(
  [string]$Bm25 = 'true',
  [int]$CandidateFactor = 4,
  [switch]$StopOnly
)

$ErrorActionPreference = 'Stop'
$bm25On = ($Bm25 -eq 'true')

$setupDir = $PSScriptRoot
$root = Split-Path (Split-Path $setupDir -Parent) -Parent
$repo = Join-Path $root 'txgycloud.git'
$jdk = Join-Path $root '.tools\jdk25\jdk-25.0.4.1+1'
$module = Join-Path $repo 'txgy-module-bidding\txgy-module-bidding-server'

# 1) stop any running bidding-server (same jar) so the jar can be rebuilt / reconfigured
$running = Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
  Where-Object { $_.CommandLine -like '*txgy-module-bidding-server.jar*' }
foreach ($proc in $running) { Stop-Process -Id $proc.ProcessId -Force }
if ($running) { Start-Sleep -Seconds 3; Write-Host "stopped bidding pid(s): $($running.ProcessId -join ',')" }
if ($StopOnly) { return }

if (-not $env:DASHSCOPE_API_KEY) {
  Write-Warning 'DASHSCOPE_API_KEY is empty: cloud embedding/rerank will report cloud-unavailable.'
} else {
  Write-Host "dashscope key loaded from caller env: len=$($env:DASHSCOPE_API_KEY.Length)"
}

# 2) launch with AI enabled; BM25 switch and candidate factor are the A/B knobs
$ds = 'jdbc:mysql://127.0.0.1:3307/ruoyi-vue-pro?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true&rewriteBatchedStatements=true'
$javaArgs = @(
  "-Duser.home=$repo\.tools\home", '-jar', 'target\txgy-module-bidding-server.jar',
  '--server.port=48110', '--spring.profiles.active=local',
  "--spring.datasource.dynamic.datasource.master.url=$ds",
  '--spring.datasource.dynamic.datasource.master.username=root',
  '--spring.datasource.dynamic.datasource.master.password=Lzt1015070815',
  "--spring.datasource.dynamic.datasource.slave.url=$ds",
  '--spring.datasource.dynamic.datasource.slave.password=Lzt1015070815',
  '--spring.data.redis.host=127.0.0.1', '--spring.data.redis.port=6380',
  '--bidding.es.enabled=false', '--bidding.bid-agent.ai-enabled=true',
  "--bidding.bid-agent.retrieve.bm25-enabled=$($bm25On.ToString().ToLower())",
  "--bidding.bid-agent.retrieve.candidate-factor=$CandidateFactor"
)
$tag = "bm25-" + $(if ($bm25On) { 'on' } else { 'off' }) + "-cf$CandidateFactor"
Start-Process -FilePath (Join-Path $jdk 'bin\java.exe') -ArgumentList $javaArgs -WorkingDirectory $module `
  -WindowStyle Hidden -RedirectStandardOutput (Join-Path $setupDir "bidding-$tag-out.log") `
  -RedirectStandardError (Join-Path $setupDir "bidding-$tag-err.log")

# 3) wait for the port to accept connections (HTTP probe is left to the caller: it is faster and
#    avoids this script blocking on a slow actuator response during startup)
$deadline = (Get-Date).AddMinutes(3)
while ((Get-Date) -lt $deadline) {
  Start-Sleep -Seconds 5
  $client = New-Object System.Net.Sockets.TcpClient
  try {
    $async = $client.BeginConnect('127.0.0.1', 48110, $null, $null)
    if ($async.AsyncWaitHandle.WaitOne(1500) -and $client.Connected) {
      Write-Host "port 48110 OPEN (bm25=$bm25On, candidateFactor=$CandidateFactor)"
      return
    }
  } catch { } finally { $client.Close() }
}
throw 'bidding port 48110 did not open in time'
