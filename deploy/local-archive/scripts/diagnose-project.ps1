$ErrorActionPreference = 'Continue'
$root = 'D:\codexshuju\tianxin-pipe-project'
$out = Join-Path $root 'diagnose-output.txt'
$dockerCandidates = @(
    (Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe'),
    (Join-Path $env:ProgramFiles 'Docker\Docker\resources\bin\docker.exe')
)
$docker = $dockerCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $docker) {
    $docker = (Get-Command docker.exe -ErrorAction Stop).Source
}

"=== Containers ===" | Tee-Object -FilePath $out
& $docker ps -a --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' | Tee-Object -FilePath $out -Append
"" | Tee-Object -FilePath $out -Append
"=== Health endpoints ===" | Tee-Object -FilePath $out -Append

$endpoints = @(
    'http://127.0.0.1:8080/',
    'http://127.0.0.1:48080/actuator/health',
    'http://127.0.0.1:8848/nacos/'
)
foreach ($url in $endpoints) {
    try {
        $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 10
        "$url => HTTP $($r.StatusCode)" | Tee-Object -FilePath $out -Append
    } catch {
        "$url => ERROR: $($_.Exception.Message)" | Tee-Object -FilePath $out -Append
    }
}

"" | Tee-Object -FilePath $out -Append
"=== Container logs ===" | Tee-Object -FilePath $out -Append
foreach ($name in @('txgy-gateway','txgy-system','txgy-infra','txgy-member','txgy-ui-nginx','txgy-mysql','txgy-project-redis','txgy-nacos')) {
    "" | Tee-Object -FilePath $out -Append
    "--- $name ---" | Tee-Object -FilePath $out -Append
    & $docker logs --tail 80 $name 2>&1 | Tee-Object -FilePath $out -Append
}

Write-Host ''
Write-Host "Diagnostic report written to: $out" -ForegroundColor Green
