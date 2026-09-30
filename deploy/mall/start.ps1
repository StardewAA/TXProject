param([switch] $SkipOssCheck, [switch] $CheckConfigOnly)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (-not (Test-Path -LiteralPath '.env')) {
    Copy-Item -LiteralPath '.env.example' -Destination '.env'
}
function Invoke-Compose {
    param([string[]] $ComposeArguments)
    & docker compose --env-file .env -f compose.yaml @ComposeArguments
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose failed (exit $LASTEXITCODE). See the error above." }
}
Invoke-Compose -ComposeArguments @('config', '--quiet')
Invoke-Compose -ComposeArguments @('run', '--rm', '--no-deps', 'preflight')
if ($CheckConfigOnly) {
    Write-Host 'Configuration preflight passed. No application was built/started; cloud access remains unverified.'
    exit 0
}
if ($SkipOssCheck) {
    Write-Warning 'OSS checks skipped explicitly. This is local-only validation, NOT full mall acceptance.'
} else {
    Invoke-Compose -ComposeArguments @('run', '--rm', '--no-deps', 'verify-assets')
}
# Serial builds keep memory bounded and share the backend reactor cache.
foreach ($service in @('gateway', 'system', 'infra', 'member', 'pay', 'product', 'trade', 'promotion', 'statistics', 'wms', 'ai', 'mall-pc', 'mall-h5', 'admin')) {
    Invoke-Compose -ComposeArguments @('build', $service)
}
Invoke-Compose -ComposeArguments @('up', '-d', '--wait', '--wait-timeout', '900')
if ($SkipOssCheck) {
    Invoke-Compose -ComposeArguments @('run', '--rm', '--no-deps', 'verify-local')
    Write-Host 'Local checks passed; OSS remains unverified. See deploy/mall/README.md'
} else {
    Invoke-Compose -ComposeArguments @('run', '--rm', '--no-deps', 'verify')
    Write-Host 'Startup smoke checks passed (not full business/device acceptance). See deploy/mall/README.md'
}
