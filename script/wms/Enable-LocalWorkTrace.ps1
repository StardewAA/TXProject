# Enable API event collection on the already-running local business services.
# Does not change role grants, business data, credentials, ports or network exposure.
$ErrorActionPreference='Stop'
$projectRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$config=Join-Path $projectRoot 'deploy/local/work-trace/application.properties'
$services=@('wms-acceptance-wms','wms-acceptance-bpm','deploy-erp-1','deploy-iot-1',
    'deploy-iot-gateway-1','deploy-report-1','deploy-im-1','deploy-ai-1','deploy-statistics-1',
    'deploy-mp-1','deploy-member-1','deploy-promotion-1')
$running=@(docker ps --format '{{.Names}}')
foreach($service in $services) {
    if($service -notin $running){continue}
    $current=@(docker inspect $service | ConvertFrom-Json)[0]
    if($current.Config.WorkingDir -ne '/app'){throw "Unexpected working directory for $service"}
    $current=$null
    docker exec $service test -f /app/config/work-trace/application.properties
    if($LASTEXITCODE -eq 0) { Write-Output "Tracing override already installed: $service"; continue }
    docker exec $service mkdir -p /app/config/work-trace
    if($LASTEXITCODE -ne 0){throw "Cannot prepare config for $service"}
    docker cp $config "${service}:/app/config/work-trace/application.properties"
    if($LASTEXITCODE -ne 0){throw "Cannot copy config for $service"}
    docker restart $service | Out-Null
    if($LASTEXITCODE -ne 0){throw "Cannot restart $service"}
    Write-Output "Enabled API tracing: $service"
}
