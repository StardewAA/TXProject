# Create the "dev" namespace in Nacos so that services using
#   spring.cloud.nacos.discovery.namespace=dev / config.namespace=dev
# can register and start correctly.
param(
    [string]$BaseUrl = 'http://127.0.0.1:8848',
    [string]$User = 'nacos',
    [string]$Password = 'nacos',
    [string]$NamespaceId = 'dev'
)

$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'

function Get-Endpoints { param($b) @("$b/nacos/v1", "$b/nacos/v3", "$b/nacos") }

Write-Host "Waiting for Nacos at $BaseUrl ..." -ForegroundColor Cyan
$ready = $false
for ($i = 0; $i -lt 60; $i++) {
    try {
        $r = Invoke-WebRequest -Uri "$BaseUrl/nacos/" -UseBasicParsing -TimeoutSec 5
        if ($r.StatusCode -lt 500) { $ready = $true; break }
    } catch { }
    Start-Sleep -Seconds 5
}
if (-not $ready) { Write-Host "Nacos is not reachable" -ForegroundColor Red; exit 1 }
Write-Host "Nacos is up" -ForegroundColor Green

$token = $null
foreach ($ep in (Get-Endpoints $BaseUrl)) {
    foreach ($p in @('/auth/users/login', '/auth/user/login')) {
        $url = "$ep$p" + "?username=$User&password=$Password"
        try {
            $r = Invoke-WebRequest -Uri $url -Method Post -UseBasicParsing -TimeoutSec 10
            if ($r.StatusCode -eq 200) {
                $j = $r.Content | ConvertFrom-Json
                $token = $j.accessToken
                if (-not $token -and $j.data) { $token = $j.data.accessToken }
                if ($token) { Write-Host "Login OK via $ep$p" -ForegroundColor Green; break }
            }
        } catch { }
    }
    if ($token) { break }
}

if (-not $token) {
    Write-Host "Login failed - Nacos auth may be disabled (that is fine), continuing without token" -ForegroundColor Yellow
}

$created = $false
foreach ($ep in (Get-Endpoints $BaseUrl)) {
    foreach ($p in @('/console/namespaces', '/core/namespace')) {
        $url = "$ep$p" + "?customNamespaceId=$NamespaceId&namespaceName=$NamespaceId&namespaceDesc=local-dev"
        if ($token) { $url += "&accessToken=$token" }
        try {
            $r = Invoke-WebRequest -Uri $url -Method Post -UseBasicParsing -TimeoutSec 10
            Write-Host "Create namespace via $ep$p => HTTP $($r.StatusCode) : $($r.Content)" -ForegroundColor Cyan
            if ($r.StatusCode -eq 200) { $created = $true; break }
        } catch {
            Write-Host "Create namespace via $ep$p => $($_.Exception.Message)" -ForegroundColor DarkGray
        }
    }
    if ($created) { break }
}

# verify
foreach ($ep in (Get-Endpoints $BaseUrl)) {
    foreach ($p in @('/console/namespaces', '/core/namespace/list')) {
        $url = "$ep$p"
        if ($token) { $url += "?accessToken=$token" }
        try {
            $r = Invoke-WebRequest -Uri $url -Method Get -UseBasicParsing -TimeoutSec 10
            if ($r.StatusCode -eq 200) {
                Write-Host "Namespaces: $($r.Content)" -ForegroundColor Cyan
                return
            }
        } catch { }
    }
}
