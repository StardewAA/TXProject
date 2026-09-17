param(
    [Parameter(Mandatory)][string]$BaseUrl,
    [Parameter(Mandatory)][string]$Token,
    [Parameter(Mandatory)][long]$TenantId,
    [Parameter(Mandatory)][long]$ManagerUserId,
    [Parameter(Mandatory)][long]$ApproverUserId,
    [string]$Category = 'wms',
    [switch]$UpdateExisting
)
$ErrorActionPreference = 'Stop'
$headers = @{ Authorization = "Bearer $Token"; 'tenant-id' = "$TenantId" }
$api = $BaseUrl.TrimEnd('/') + '/admin-api/bpm/model'
function Invoke-BpmApi([string]$Method, [string]$Uri, $Body) {
    $args = @{ Method = $Method; Uri = $Uri; Headers = $headers }
    if ($null -ne $Body) {
        $args.ContentType = 'application/json; charset=utf-8'
        $args.Body = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 30))
    }
    $result = Invoke-RestMethod @args
    if ($result.code -ne 0) { throw "BPM API failed: $($result.code) $($result.msg)" }
    return $result.data
}
$models = @(Invoke-BpmApi 'GET' "$api/list" $null)
$existing = @($models | Where-Object key -eq 'wms_receipt_approval')
if ($existing.Count -gt 1) { throw 'Multiple receipt models found; inspect tenant model data.' }
if ($existing.Count -eq 1 -and !$UpdateExisting) {
    Write-Output "Receipt model already exists: $($existing[0].id). Use -UpdateExisting to update and redeploy explicitly."
    return
}
$xml = [System.IO.File]::ReadAllText((Join-Path $PSScriptRoot 'receipt-approval.bpmn'))
$xml = $xml.Replace('__APPROVER_USER_ID__', "$ApproverUserId")
$model = @{
    key = 'wms_receipt_approval'; name = 'WMS 入库审批'; category = $Category
    type = 10; formType = 20; visible = $false; managerUserIds = @($ManagerUserId)
    startUserIds = @(); startDeptIds = @(); autoApprovalType = 0
    allowCancelRunningProcess = $true; allowWithdrawTask = $false
    formCustomCreatePath = '/wms/order/receipt'
    formCustomViewPath = '/wms/order/receipt/bpm-detail.vue'
    bpmnXml = $xml
}
if ($existing.Count -eq 1) {
    $model.id = $existing[0].id
    $null = Invoke-BpmApi 'PUT' "$api/update" $model
    $modelId = $model.id
} else {
    $modelId = Invoke-BpmApi 'POST' "$api/create" $model
}
$null = Invoke-BpmApi 'POST' "$api/deploy?id=$([uri]::EscapeDataString($modelId))" $null
Write-Output "Deployed wms_receipt_approval: modelId=$modelId tenantId=$TenantId approverUserId=$ApproverUserId"
