# Verify the locally started stack is reachable.
param(
    [string]$Gateway = 'http://127.0.0.1:48080',
    [string]$Nacos = 'http://127.0.0.1:8848'
)

$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'

Write-Host "=== containers ===" -ForegroundColor Cyan
docker ps -a --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' | Select-String -Pattern 'txgy|NAMES'

Write-Host "`n=== nacos ===" -ForegroundColor Cyan
try {
    $r = Invoke-WebRequest -Uri "$Nacos/nacos/" -UseBasicParsing -TimeoutSec 10
    Write-Host "Nacos console HTTP $($r.StatusCode)" -ForegroundColor Green
} catch {
    Write-Host "Nacos not reachable: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "`n=== gateway actuator health ===" -ForegroundColor Cyan
$ok = $false
foreach ($p in @('/actuator/health', '/admin-api/system/actuator/health')) {
    try {
        $r = Invoke-WebRequest -Uri ($Gateway + $p) -UseBasicParsing -TimeoutSec 15
        Write-Host "GET $p => $($r.StatusCode) : $($r.Content)" -ForegroundColor Green
        $ok = $true
    } catch {
        Write-Host "GET $p => $($_.Exception.Message)" -ForegroundColor DarkGray
    }
}

Write-Host "`n=== gateway service list (spring cloud gateway) ===" -ForegroundColor Cyan
try {
    $r = Invoke-WebRequest -Uri "$Gateway/actuator/gateway/routes" -UseBasicParsing -TimeoutSec 15
    Write-Host "routes HTTP $($r.StatusCode)" -ForegroundColor Green
} catch {
    Write-Host "routes endpoint not available (may be disabled): $($_.Exception.Message)" -ForegroundColor DarkGray
}

Write-Host "`n=== login api probe (system service) ===" -ForegroundColor Cyan
try {
    $body = '{"username":"admin","password":"admin123"}'
    $r = Invoke-WebRequest -Uri "$Gateway/admin-api/system/auth/login" -Method Post -Body $body `
         -ContentType 'application/json' -UseBasicParsing -TimeoutSec 25
    Write-Host "login HTTP $($r.StatusCode)" -ForegroundColor Green
    Write-Host ($r.Content.Substring(0, [Math]::Min(600, $r.Content.Length)))
} catch {
    $msg = $_.Exception.Message
    Write-Host "login failed (still acceptable if it returns a business error): $msg" -ForegroundColor Yellow
    if ($_.Exception.Response) {
        $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
        Write-Host "response body: " + $sr.ReadToEnd()
    }
}

if (-not $ok) {
    Write-Host "`nGateway health endpoint did not respond. Check logs with:" -ForegroundColor Yellow
    Write-Host "  docker logs --tail 200 txgy-gateway" -ForegroundColor Yellow
}
