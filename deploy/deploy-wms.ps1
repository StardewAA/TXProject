<#
    deploy-wms.ps1 -- rebuild and deploy the WMS service image from the compiled jar.

    WHY
      The local stack serves the WMS backend from local-wms:latest, built from an
      already compiled Spring Boot fat jar via deploy/local/Dockerfile.runtime.
      Adding fields to the WMS entities/VOs requires repackaging that jar and
      rebuilding the image, then recreating the txgy-wms container.

    PREREQUISITE -- package the jar first (PowerShell splits -D on '=' if you do not
    pass an argument ARRAY, which is why build-local.ps1 uses one):
      $env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot'
      $mvn = 'C:\Users\86159\tools\apache-maven-3.9.16\bin\mvn.cmd'
      $a = @('-B','-ntp','-s','D:\tianxinfangfu\deploy\local\settings.xml',
             '-Dmaven.test.skip=true',
             '-pl','txgy-module-wms/txgy-module-wms-server','-am','package')
      & $mvn @a

    USAGE
      powershell -ExecutionPolicy Bypass -File D:\tianxinfangfu\deploy\deploy-wms.ps1
      powershell -ExecutionPolicy Bypass -File D:\tianxinfangfu\deploy\deploy-wms.ps1 -ImageOnly
#>
param(
    [string]$Root = 'D:\tianxinfangfu',
    [switch]$ImageOnly
)

$ErrorActionPreference = 'Stop'

$jar     = Join-Path $Root 'txgy-module-wms\txgy-module-wms-server\target\txgy-module-wms-server.jar'
$dockerfile = Join-Path $Root 'deploy\local\Dockerfile.runtime'
$ctx     = Join-Path $Root '.wms-image-context'
$candidate = 'local-wms:candidate'
$live      = 'local-wms:latest'

function Step($m) { Write-Host "`n== $m ==" -ForegroundColor Cyan }
function Ok($m)   { Write-Host $m -ForegroundColor Green }
function Warn($m) { Write-Host $m -ForegroundColor Yellow }

if (-not (Test-Path $jar)) {
    throw "jar not found: $jar`nPackage it first (see the header of this script)."
}
$jarMb = '{0:N1}' -f ((Get-Item $jar).Length / 1MB)
Write-Host "jar = $jar ($jarMb MB)"

Step "1/2 build image $candidate"
if (Test-Path $ctx) { Remove-Item $ctx -Recurse -Force }
New-Item -ItemType Directory -Force -Path $ctx | Out-Null
Copy-Item $jar (Join-Path $ctx 'wms.jar') -Force
Copy-Item $dockerfile (Join-Path $ctx 'Dockerfile.runtime') -Force

# BuildKit is disabled for the same reason build-images.ps1 does it: this workspace
# path has non-ASCII ancestors and BuildKit's session key handling rejects them.
$env:DOCKER_BUILDKIT = '0'
$prev = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
& docker build -f (Join-Path $ctx 'Dockerfile.runtime') --build-arg JAR_FILE=wms.jar -t $candidate $ctx 2>&1 |
    ForEach-Object { Write-Host $_ }
$code = $LASTEXITCODE
$ErrorActionPreference = $prev
if ($code -ne 0) { throw "docker build FAILED (exit=$code)" }
Ok "image built: $candidate"

if ($ImageOnly) {
    Remove-Item $ctx -Recurse -Force -ErrorAction SilentlyContinue
    Warn "`n-ImageOnly: container untouched. Promote with:"
    Warn "  docker tag $candidate $live"
    Warn "  docker rm -f txgy-wms; docker run -d --name txgy-wms --network txgy-dev <env...> $live"
    exit 0
}

Step "2/2 recreate txgy-wms"
# Capture the running container's exact env/network so the replacement is identical.
$old = docker inspect txgy-wms 2>$null | ConvertFrom-Json
if (-not $old) { throw "txgy-wms container not found; run it manually first." }

$envArgs = @()
foreach ($e in $old.Config.Env) {
    if ($e -match '^(PATH|JAVA_HOME|LANG|HOSTNAME)=') { continue }
    $envArgs += '-e'; $envArgs += $e
}
$mem = $old.HostConfig.Memory
$memArgs = @()
if ($mem -gt 0) { $memArgs = @('--memory', "$mem") }

& docker tag $candidate $live
& docker rm -f txgy-wms 2>&1 | Out-Null

$runArgs = @('run','-d','--name','txgy-wms','--network','txgy-dev','--restart','unless-stopped') +
           $memArgs + $envArgs + @($live)
& docker @runArgs 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { throw "docker run FAILED" }
Ok "container recreated"

Remove-Item $ctx -Recurse -Force -ErrorAction SilentlyContinue

Write-Host "`nwaiting for the service to register in Nacos / become healthy ..." -ForegroundColor Cyan
$healthy = $false
for ($i = 1; $i -le 40; $i++) {
    Start-Sleep -Seconds 5
    $st = & docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' txgy-wms 2>$null
    if ($st -eq 'healthy') { $healthy = $true; break }
    if ($st -eq 'unhealthy') { break }
}
if ($healthy) { Ok "txgy-wms is healthy" }
else {
    Warn "txgy-wms did not report healthy; last logs:"
    & docker logs --tail 40 txgy-wms 2>&1 | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }
}
Write-Host "`ndone."
