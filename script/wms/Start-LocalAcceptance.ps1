# Local-only deployment. Existing containers are stopped, never removed, for rollback.
$ErrorActionPreference='Stop'
$runtimeEnvironments=@{}
foreach($service in @('wms','bpm')) {
    $current=@(docker inspect "deploy-$service-1" | ConvertFrom-Json)[0]
    if(!$current) { throw "Original $service container missing; cannot prepare rollback." }
    $envLines=@($current.Config.Env)
    if($service -eq 'wms') { $envLines+= 'TXGY_WMS_SIMULATION_ENABLED=true' }
    $runtimeEnvironments[$service]=$envLines
}
docker stop deploy-wms-1 deploy-bpm-1 deploy-frontend-1
if($LASTEXITCODE -ne 0){throw 'Could not stop existing local services'}
foreach($service in @('wms','bpm')) {
    $dockerArgs=@('run','-d','--name',"wms-acceptance-$service",'--network','txgy-dev','--network-alias',$service,'--restart','unless-stopped')
    foreach($entry in $runtimeEnvironments[$service]) { $dockerArgs+=@('--env',$entry) }
    $image=if($service -eq 'wms'){'txgy-wms-acceptance:20260912'}else{'txgy-wms-acceptance-bpm:20260912'}
    $dockerArgs+=$image
    & docker @dockerArgs
    if($LASTEXITCODE -ne 0){throw "$service start failed; use Stop-LocalAcceptance.ps1 to restore"}
}
$runtimeEnvironments.Clear()
$dockerArgs=$null
docker run -d --name wms-acceptance-frontend --network txgy-dev -p 127.0.0.1:5173:80 -p 127.0.0.1:5174:80 --restart unless-stopped txgy-wms-acceptance-frontend:20260912
if($LASTEXITCODE -ne 0){throw 'Frontend start failed; use Stop-LocalAcceptance.ps1 to restore'}
Write-Output 'Local acceptance containers started. Verify health before testing. ERP container unchanged.'
