# Build the runtime images for the core services from the already compiled jars.
# BuildKit is disabled on purpose: with this workspace path (non-ASCII characters)
# buildkit fails with `x-docker-expose-session-sharedkey ... non-printable ASCII`.
# Log: %TEMP%\txgy_build_img.log
param([string[]]$Services = @('gateway', 'system', 'infra', 'member'))

$ErrorActionPreference = 'Continue'

$localDir  = $PSScriptRoot
$deployDir = Split-Path -Parent $localDir
$rootDir   = Split-Path -Parent $deployDir

$env:DOCKER_BUILDKIT = '0'
$env:COMPOSE_DOCKER_CLI_BUILD = '0'

Set-Location -LiteralPath $rootDir

$log = Join-Path $env:TEMP 'txgy_build_img.log'

& docker compose -f (Join-Path $localDir 'docker-compose.local.yml') `
                 --env-file (Join-Path $deployDir '.env') `
                 build @Services 2>&1 | Out-File -FilePath $log -Encoding ASCII

"BUILD_EXIT=$LASTEXITCODE" | Out-File -FilePath $log -Append -Encoding ASCII
