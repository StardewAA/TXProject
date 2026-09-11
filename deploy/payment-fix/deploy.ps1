param([Parameter(Mandatory = $true)][ValidateSet('pay', 'trade')][string]$Service)
$ErrorActionPreference = 'Stop'
$containerName = "deploy-$Service-1"
$previous = (docker inspect $containerName | ConvertFrom-Json)[0]
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect existing container' }
if ($previous.Mounts.Count -gt 0 -or $previous.HostConfig.PortBindings.PSObject.Properties.Count -gt 0) {
    throw 'Unexpected mounts or published ports; review manually'
}
$backupName = "$containerName-before-payment-fix"
$existingNames = @(docker ps -a --format '{{.Names}}')
if ($LASTEXITCODE -ne 0) { throw 'Cannot check rollback containers' }
if ($existingNames -contains $backupName) { throw 'Rollback container already exists; do not overwrite it' }
$networks = @($previous.NetworkSettings.Networks.PSObject.Properties.Name)
if ($networks.Count -ne 1 -or $networks[0] -ne 'txgy-dev') {
    throw 'Unexpected container network; review manually'
}
$repoPath = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$modulePath = if ($Service -eq 'pay') { 'txgy-module-pay/txgy-module-pay-server' } else { 'txgy-module-mall/txgy-module-trade-server' }
Copy-Item -LiteralPath "$repoPath/$modulePath/target/txgy-module-$Service-server.jar" -Destination "$PSScriptRoot/app.jar"
$imageTag = "deploy-${Service}:payment-fix-20260911"
$baseTag = "deploy-${Service}:before-payment-fix-20260911"
docker tag $previous.Image $baseTag
if ($LASTEXITCODE -ne 0) { throw 'Cannot preserve base image' }
docker build --build-arg "BASE_IMAGE=$baseTag" -t $imageTag $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'Image build failed; original container unchanged' }
$runArgs = @('run', '-d', '--name', $containerName, '--restart', $previous.HostConfig.RestartPolicy.Name,
    '--network', 'txgy-dev', '--network-alias', $Service)
if ($previous.Config.Healthcheck.Test[0] -eq 'CMD-SHELL') {
    $runArgs += @('--health-cmd', $previous.Config.Healthcheck.Test[1],
        '--health-interval', '15s', '--health-timeout', '5s', '--health-start-period', '90s', '--health-retries', '20')
}
foreach ($entry in $previous.Config.Env) { $runArgs += @('--env', $entry) }
if ($Service -eq 'pay') {
    $runArgs += @('--env', 'YUDAO_PAY_LOCAL_NOTIFY_RETRY_ENABLED=true',
        '--env', 'YUDAO_PAY_LOCAL_NOTIFY_RETRY_MIN_ID=584', '--env', 'YUDAO_PAY_LOCAL_NOTIFY_RETRY_APP_ID=1')
}
foreach ($label in $previous.Config.Labels.PSObject.Properties) { $runArgs += @('--label', "$($label.Name)=$($label.Value)") }
$runArgs += $imageTag
docker stop $containerName
if ($LASTEXITCODE -ne 0) { throw 'Stop failed' }
docker rename $containerName $backupName
if ($LASTEXITCODE -ne 0) { docker start $containerName; throw 'Rename failed' }
docker @runArgs
if ($LASTEXITCODE -ne 0) {
    throw "Start failed; original container retained as $backupName for rollback"
}
