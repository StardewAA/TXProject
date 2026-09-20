param([switch]$SchemaOnly, [switch]$SkipSchema)
$ErrorActionPreference='Stop'
$projectRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$backupRoot=Join-Path $projectRoot '.tmp/work-trace/rollback'
New-Item -ItemType Directory -Force $backupRoot | Out-Null
. (Join-Path $PSScriptRoot 'LocalAcceptanceTools.ps1')

if (!$SkipSchema) {
    $check=Invoke-WmsLocalSql "SELECT COUNT(*) FROM system_tenant WHERE id=2005 AND package_id=120 AND deleted=0; SELECT COUNT(*) FROM system_role WHERE tenant_id=2005 AND deleted=0 AND status=0 AND ((id=60096 AND name='仓库管理员') OR (id=60085 AND name='决策审批人') OR (id=60084 AND code='tenant_admin'));"
    if (($check -join ',') -ne '1,3') { throw 'Local tenant/role identities differ; migration not applied' }
    $packageBackup=Join-Path $backupRoot 'package-120-menu-ids.txt'
    if (!(Test-Path -LiteralPath $packageBackup)) {
        Invoke-WmsLocalSql "SELECT menu_ids FROM system_tenant_package WHERE id=120;" | Set-Content -LiteralPath $packageBackup
        Invoke-WmsLocalSql "SELECT id,role_id,menu_id,tenant_id FROM system_role_menu WHERE tenant_id=2005 AND deleted=0;" | Set-Content -LiteralPath (Join-Path $backupRoot 'role-menus-before.tsv')
    }
    $sql=Get-Content -LiteralPath (Join-Path $projectRoot 'deploy/mysql/repair/2026-09-15-work-trace.sql') -Raw
    Invoke-WmsLocalSql $sql | Out-Null
    Invoke-WmsLocalSql "SELECT id,name,permission FROM system_menu WHERE component='wms/work-log/index' OR permission='infra:work-log:query-all'; SELECT r.id,r.name FROM system_role r JOIN system_role_menu rm ON rm.role_id=r.id AND rm.tenant_id=r.tenant_id AND rm.deleted=0 JOIN system_menu m ON m.id=rm.menu_id WHERE r.tenant_id=2005 AND m.permission='infra:work-log:query-all' AND m.deleted=0;"
}
if ($SchemaOnly) { return }

$services=@(
    @{Name='deploy-infra-1'; Module='infra'},
    @{Name='wms-acceptance-wms'; Module='wms'},
    @{Name='wms-acceptance-bpm'; Module='bpm'}
)
# Stage all backups before stopping any service. Original copies are never overwritten.
foreach($service in $services) {
    $service.Source=Join-Path $projectRoot "txgy-module-$($service.Module)/txgy-module-$($service.Module)-server/target/txgy-module-$($service.Module)-server.jar"
    if (!(Test-Path -LiteralPath $service.Source)) { throw "Missing build: $($service.Module)" }
    $service.Backup=Join-Path $backupRoot "$($service.Name).jar"
    if (!(Test-Path -LiteralPath $service.Backup)) {
        docker cp "$($service.Name):/app/app.jar" $service.Backup
        if($LASTEXITCODE -ne 0){throw "Could not preserve $($service.Name)"}
    }
}
$frontendBackup=Join-Path $backupRoot 'frontend'
if (!(Test-Path -LiteralPath $frontendBackup)) {
    docker cp 'wms-acceptance-frontend:/usr/share/nginx/html' $frontendBackup
    if($LASTEXITCODE -ne 0){throw 'Could not preserve frontend'}
}
foreach($service in $services) {
    docker stop $service.Name | Out-Null
    if($LASTEXITCODE -ne 0){throw "Could not stop $($service.Name)"}
    try {
        docker cp $service.Source "$($service.Name):/app/app.jar"
        if($LASTEXITCODE -ne 0){throw 'Could not copy new jar'}
        docker start $service.Name | Out-Null
        if($LASTEXITCODE -ne 0){throw 'Could not start new jar'}
        Write-Output "Updated $($service.Name)"
    } catch {
        docker cp $service.Backup "$($service.Name):/app/app.jar"
        docker start $service.Name | Out-Null
        throw
    }
}
$dist=Join-Path $projectRoot 'txgy-ui-admin-vben/apps/web-antd/dist/.'
docker cp $dist 'wms-acceptance-frontend:/usr/share/nginx/html/'
if($LASTEXITCODE -ne 0){throw 'Frontend copy failed; restore index/assets from rollback/frontend'}
& (Join-Path $PSScriptRoot 'Enable-LocalWorkTrace.ps1')
Write-Output 'Local artifacts updated. Verify health, permissions, APIs and browser before declaring completion.'
