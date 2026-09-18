param(
    [Parameter(Mandatory)][string]$BaseUrl,
    [Parameter(Mandatory)][string]$Token,
    [Parameter(Mandatory)][long]$TenantId,
    [Parameter(Mandatory)][long]$ManagerUserId,
    [Parameter(Mandatory)][long]$ApproverUserId,
    [string]$Category = 'CRM',
    [switch]$UpdateExisting
)
$ErrorActionPreference = 'Stop'
$headers = @{ Authorization = "Bearer $Token"; 'tenant-id' = "$TenantId" }
$api = $BaseUrl.TrimEnd('/') + '/admin-api/bpm/model'
$key = 'crm-contract-audit'

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

# NOTE: read both the BPMN XML and the process name from the file itself.
# .ps1 files without a UTF-8 BOM are decoded as ANSI by Windows PowerShell 5.1,
# so non-ASCII literals inside this script would be corrupted on the way to the API.
$xmlPath = Join-Path $PSScriptRoot 'contract-approval.bpmn'
$xml = [System.IO.File]::ReadAllText($xmlPath)
if ($xml -notmatch ('<process id="' + [regex]::Escape($key) + '" name="([^"]+)"')) {
    throw "Cannot find process id='$key' with a name attribute in $xmlPath"
}
$processName = $Matches[1]
$xml = $xml.Replace('__APPROVER_USER_ID__', "$ApproverUserId")

$models = @(Invoke-BpmApi 'GET' "$api/list" $null)
$existing = @($models | Where-Object key -eq $key)
if ($existing.Count -gt 1) { throw "Multiple $key models found; inspect tenant model data." }
if ($existing.Count -eq 1 -and !$UpdateExisting) {
    Write-Output "$key already exists: $($existing[0].id). Use -UpdateExisting to update and redeploy explicitly."
    return
}

$model = @{
    key = $key; name = $processName; category = $Category
    type = 10; formType = 20; visible = $false; managerUserIds = @($ManagerUserId)
    startUserIds = @(); startDeptIds = @(); autoApprovalType = 0
    allowCancelRunningProcess = $true; allowWithdrawTask = $false
    formCustomCreatePath = '/crm/contract'
    formCustomViewPath = '/crm/contract/detail'
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
Write-Output "Deployed $key : modelId=$modelId name=$processName tenantId=$TenantId approverUserId=$ApproverUserId"
