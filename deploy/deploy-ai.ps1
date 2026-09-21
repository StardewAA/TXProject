$ErrorActionPreference = 'Continue'

# ASCII-ONLY on purpose: PS 5.1 decodes BOM-less UTF-8 as ANSI and non-ASCII comments
# can swallow the following line (learned the hard way with the WMS deploy script).

$Root   = 'D:\tianxinfangfu'
$ctx    = Join-Path $Root '.ai-image-context'
$jar    = Join-Path $Root 'txgy-module-ai\txgy-module-ai-server\target\txgy-module-ai-server.jar'
$dockerfile = Join-Path $Root 'deploy\local\Dockerfile.runtime'
$candidate = 'local-ai:candidate'
$live      = 'local-ai:latest'
$ctr       = 'txgy-ai'
$network   = 'txgy-dev'
$port      = '48090'

function Step($m) { Write-Host "`n== $m ==" -ForegroundColor Cyan }
function Ok($m)   { Write-Host $m -ForegroundColor Green }

if (-not (Test-Path $jar)) { Write-Host "jar not found: $jar"; exit 1 }
Write-Host "jar = $('{0:N1}' -f ((Get-Item $jar).Length / 1MB)) MB  ($((Get-Item $jar).LastWriteTime))"

# ---------- 1. build image ----------
Step "1/3 build image $candidate"
if (Test-Path $ctx) { Remove-Item $ctx -Recurse -Force }
New-Item -ItemType Directory -Force -Path $ctx | Out-Null
Copy-Item $jar (Join-Path $ctx 'ai.jar') -Force
Copy-Item $dockerfile (Join-Path $ctx 'Dockerfile.runtime') -Force

# BuildKit off: repo path has non-ASCII ancestors which break its session keys.
# --no-cache is required: the jar content changes without changing its path, so a
# cached COPY layer would silently ship the previous build (this bit us on WMS).
$env:DOCKER_BUILDKIT = '0'
$sw = [System.Diagnostics.Stopwatch]::StartNew()
docker build --no-cache -f (Join-Path $ctx 'Dockerfile.runtime') --build-arg JAR_FILE=ai.jar -t $candidate $ctx 2>&1 |
    Select-Object -Last 6
$sw.Stop()
if ($LASTEXITCODE -ne 0) { Write-Host "docker build FAILED (exit=$LASTEXITCODE)"; exit 1 }
Ok "image built: $candidate  ($([int]$sw.Elapsed.TotalSeconds)s)"

# ---------- 2. tag as live (this step was missing for WMS once, causing stale containers) ----------
Step "2/3 tag $candidate -> $live"
docker tag $candidate $live
foreach ($l in (docker images --filter "reference=local-ai*" --format '{{.Repository}}:{{.Tag}} {{.ID}} {{.CreatedAt}}')) { Write-Host "  $l" }

# ---------- 3. recreate container ----------
Step "3/3 recreate $ctr"
docker rm -f $ctr 2>&1 | Out-Null

$runArgs = @(
  'run','-d','--name',$ctr,'--network',$network,'--restart','unless-stopped',
  '--memory','1073741824',
  '-e','TZ=Asia/Shanghai',
  '-e','LANG=en_US.UTF-8',
  '-e','LC_ALL=en_US.UTF-8',
  '-e','JAVA_OPTS=-Xms384m -Xmx768m -XX:+UseG1GC',
  '-e','SERVER_PORT=' + $port,
  '-e','SPRING_PROFILES_ACTIVE=local',
  # Nacos: same namespace (empty/public) as the other services, otherwise they cannot discover each other
  '-e','SPRING_CLOUD_NACOS_SERVER_ADDR=nacos:8848',
  '-e','SPRING_CLOUD_NACOS_DISCOVERY_NAMESPACE=',
  '-e','SPRING_CLOUD_NACOS_CONFIG_NAMESPACE=',
  # MySQL
  '-e','TXGY_DB_PORT=3306',
  '-e','TXGY_DB_USERNAME=root',
  '-e','TXGY_DB_PASSWORD=root',
  '-e','SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_URL=jdbc:mysql://mysql:3306/ruoyi-vue-pro?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true&rewriteBatchedStatements=true',
  '-e','SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_USERNAME=root',
  '-e','SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_PASSWORD=root',
  # Redis
  '-e','SPRING_DATA_REDIS_HOST=redis',
  '-e','SPRING_DATA_REDIS_PORT=6379',
  $live
)
& docker @runArgs
Write-Host "run exit=$LASTEXITCODE"

Remove-Item $ctx -Recurse -Force -ErrorAction SilentlyContinue

Step "wait for healthy"
$sw2 = [System.Diagnostics.Stopwatch]::StartNew()
$st = 'unknown'
for ($i = 1; $i -le 120; $i++) {
    Start-Sleep -Seconds 5
    $st = (& docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' $ctr 2>$null | Out-String).Trim()
    if ($st -eq 'healthy') { Ok "healthy after $([int]$sw2.Elapsed.TotalSeconds)s"; break }
    if ($st -eq 'unhealthy') { Write-Host "UNHEALTHY after $([int]$sw2.Elapsed.TotalSeconds)s"; break }
    if ($i % 12 -eq 0) { Write-Host "  ...$([int]$sw2.Elapsed.TotalSeconds)s state=$st" }
}
if ($st -ne 'healthy') {
    Write-Host "`n== last 50 log lines =="
    docker logs --tail 50 $ctr 2>&1 | ForEach-Object { Write-Host "  $_" }
}
Write-Host "`nfinal state: $st"
