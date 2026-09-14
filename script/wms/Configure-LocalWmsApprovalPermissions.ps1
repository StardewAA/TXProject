# Local-only WMS permission metadata migration. Existing role grants are preserved.
# Credentials come from the already-authorized acceptance helper and remain in memory.
param([long]$TenantId = 2005, [long]$PackageId = 120, [long[]]$ReceiptRoleIds = @(60096))
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'LocalAcceptanceTools.ps1')
if ($TenantId -ne 2005 -or $PackageId -ne 120) { throw 'This acceptance migration is limited to tenant 2005/package 120' }
$headers = Get-WmsLocalHeaders
function Invoke-PermissionSetupApi([string]$Method, [string]$Path, $Body = $null) {
    $request = @{ Method=$Method; Uri=('http://localhost:5173/admin-api/system'+$Path); Headers=$headers }
    if ($null -ne $Body) {
        $request.ContentType='application/json; charset=utf-8'
        $request.Body=[Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 30))
    }
    $result=Invoke-RestMethod @request
    if ($result.code -ne 0) { throw "WMS permission setup: $($result.code) $($result.msg)" }
    return $result.data
}
$tenantPackageUse = Invoke-WmsLocalSql "SELECT COUNT(*) FROM system_tenant WHERE package_id=$PackageId AND deleted=0;"
if ([int]$tenantPackageUse -ne 1) { throw 'Package is shared; this migration must not change other tenants' }
$tenantPackageMatch = Invoke-WmsLocalSql "SELECT COUNT(*) FROM system_tenant WHERE id=$TenantId AND package_id=$PackageId AND deleted=0;"
if ([int]$tenantPackageMatch -ne 1) { throw 'Tenant/package mismatch' }
$menus = @(Invoke-PermissionSetupApi GET '/menu/list')
$receiptQuery = @($menus | Where-Object permission -eq 'wms:receipt-order:query')
if ($receiptQuery.Count -ne 1) { throw 'Receipt menu root cannot be resolved unambiguously' }
$receiptApproval = @($menus | Where-Object permission -eq 'wms:receipt-order:approve-warehouse')
if ($receiptApproval.Count -gt 1) { throw 'Duplicate receipt approval permissions' }
if (!$receiptApproval.Count) {
    $receiptApprovalId = Invoke-PermissionSetupApi POST '/menu/create' @{
        name='入库单仓库审批'; permission='wms:receipt-order:approve-warehouse'; type=3; sort=9
        parentId=$receiptQuery[0].parentId; status=0; visible=$true; keepAlive=$true; alwaysShow=$true
    }
} else { $receiptApprovalId=$receiptApproval[0].id }
$required = @(
    @{permission='wms:receipt-order:submit-approval';name='入库单提交审批'},
    @{permission='wms:receipt-order:approve-warehouse';name='入库单仓库审批'},
    @{permission='wms:shipment-order:submit-approval';name='出库单提交审批'},
    @{permission='wms:shipment-order:approve-supervisor';name='出库单仓库审批'}
)
$menus = @(Invoke-PermissionSetupApi GET '/menu/list')
$requiredIds = @()
foreach ($item in $required) {
    $matches = @($menus | Where-Object permission -eq $item.permission)
    if ($matches.Count -ne 1) { throw "Expected one menu: $($item.permission)" }
    $menu = Invoke-PermissionSetupApi GET "/menu/get?id=$($matches[0].id)"
    $requiredIds += [long]$menu.id
    if ($menu.name -ne $item.name -or $menu.status -ne 0) {
        $menu.name=$item.name; $menu.status=0
        $null=Invoke-PermissionSetupApi PUT '/menu/update' $menu
    }
}
$package = Invoke-PermissionSetupApi GET "/tenant-package/get?id=$PackageId"
$nextMenuIds = @(@($package.menuIds) + $requiredIds | Sort-Object -Unique)
if (@(Compare-Object @($package.menuIds) $nextMenuIds).Count) {
    # The general package update API reassigns every role and removes existing grants outside
    # the package. Update only this WMS package's menu list; no role/menu grant is deleted.
    $json = ConvertTo-Json -InputObject $nextMenuIds -Compress
    $oldJson = ConvertTo-Json -InputObject @($package.menuIds) -Compress
    $sql="UPDATE system_tenant_package SET menu_ids='$json' WHERE id=$PackageId AND deleted=0 AND JSON_LENGTH(menu_ids)=$(@($package.menuIds).Count) AND JSON_CONTAINS(menu_ids,'$oldJson'); SELECT ROW_COUNT();"
    $updated=Invoke-WmsLocalSql $sql
    if ([int]$updated -ne 1) { throw 'Package update failed' }
}
$receiptSubmitId = ($menus | Where-Object permission -eq 'wms:receipt-order:submit-approval').id
foreach ($roleId in $ReceiptRoleIds) {
    $valid=Invoke-WmsLocalSql "SELECT COUNT(*) FROM system_role WHERE id=$roleId AND tenant_id=$TenantId AND status=0 AND deleted=0;"
    if ([int]$valid -ne 1) { throw "Invalid existing receipt role: $roleId" }
    $existing=@(Invoke-PermissionSetupApi GET "/permission/list-role-menus?roleId=$roleId")
    $outsidePackage=@($existing | Where-Object { $_ -notin $nextMenuIds })
    if ($outsidePackage.Count) { throw "Role $roleId has existing grants outside the package; refusing a lossy full-role update" }
    $next=@($existing + @([long]$receiptApprovalId,[long]$receiptSubmitId) | Sort-Object -Unique)
    if (@(Compare-Object $existing $next).Count) {
        $null=Invoke-PermissionSetupApi POST '/permission/assign-role-menu' @{roleId=$roleId;menuIds=$next}
    }
    Write-Output "Preserved role $roleId grants; receipt submit/approval configured"
}
$visible=@(Invoke-PermissionSetupApi GET '/menu/simple-list')
foreach ($id in $requiredIds) {
    if ($id -notin $visible.id) { throw "WMS permission is not visible in tenant menu tree: $id" }
}
Write-Output "WMS permission menu IDs: $($requiredIds -join ','); tenant=$TenantId package=$PackageId"
$headers=$null
