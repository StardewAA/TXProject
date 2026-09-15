<#
    Initialize-FeedbackApproval.ps1 -- create and deploy the MES feedback-approval BPM model.

    Follows the same contract as script/wms/Initialize-ReceiptApproval.ps1:
      * talks only to the existing /admin-api/bpm/model list/create/update/deploy APIs
      * idempotent -- an existing model is left untouched unless -UpdateExisting is passed
      * the approver id is injected into the BPMN placeholder at deploy time

    WHY THIS SCRIPT EXISTS
      The MES side of the feedback approval (start / callback / async consume) is finished
      and deployed. The only blocker is that the BPM engine has no process definition at all
      (measured: ACT_RE_PROCDEF = 0), so submitting a feedback fails at "create process
      instance". Doing this by hand in the modeler risks three things this script pins down:
      the exact process key, the reject semantics, and the cancel capability.

    WHAT IT PINS DOWN (see docs/mes-feedback-bpm-model-config-20260914.md)
      * process key = mes-feedback-approval
          MES starts by this key (MesProFeedbackServiceImpl.BPM_PROCESS_DEFINITION_KEY) and
          bpm-server filters the callback by the same key (MesFeedbackStatusListener).
          Changing it silently breaks the write-back.
      * rejectHandlerType = 1 in the BPMN -- a rejection ENDS the process instance.
          bpm-server only publishes the status event when the instance COMPLETES, so a
          rejection that leaves the instance running would never notify MES and the feedback
          order would sit in "approving" forever.
      * allowCancelRunningProcess = false
          A cancelled instance ends with status 4 (CANCEL); MES only consumes 2/3, so the
          order would hang. Turn this on only after MES handles status 4.
      * visible = false
          The flow may only be started by MES when a feedback order is submitted. If it were
          listed as user-startable, someone could start an instance with no feedback order and
          the callback would have nothing to update.

    PREREQUISITES
      * A tenant admin access token (permissions bpm:model:create / update / deploy).
        Get one from the login API, e.g.:
          POST /admin-api/system/auth/login   header tenant-id: <id>
          body {"username":"admin","password":"<pwd>","tenantId":"<id>"}  ->  data.accessToken
      * The approver must be a REAL, enabled user of this tenant.

    USAGE
      powershell -ExecutionPolicy Bypass -File script\mes\Initialize-FeedbackApproval.ps1 `
          -BaseUrl http://localhost:48080 -Token $token -TenantId 1 `
          -ManagerUserId 1 -ApproverUserId 1

      # re-deploy after changing the approver (or after editing the .bpmn):
      ... -ApproverUserId 7 -UpdateExisting

    NOTE ON ENCODING
      This file is UTF-8 WITH BOM, unlike the WMS scripts (UTF-8 without BOM). PS 5.1 decodes
      BOM-less UTF-8 as ANSI, which would turn the Chinese model name below into mojibake.
      Keep the BOM if you edit this file.

    NOTE ON THE APPROVER
      -ApproverUserId uses candidateStrategy 30 (a single named user). Role-based approval
      (strategy 10) is the recommendation for production but is still an open decision
      (questions 2 and 8 in the design doc). To switch, copy the multiInstance block from
      script/wms/warehouse-role-approval.bpmn and change the strategy to 10.
#>
param(
    [Parameter(Mandatory)][string]$BaseUrl,
    [Parameter(Mandatory)][string]$Token,
    [Parameter(Mandatory)][long]$TenantId,
    [Parameter(Mandatory)][long]$ManagerUserId,
    [Parameter(Mandatory)][long]$ApproverUserId,
    [string]$Category = 'MAIN_01',
    [switch]$UpdateExisting
)

$ErrorActionPreference = 'Stop'

$ProcessKey = 'mes-feedback-approval'
$ProcessName = '生产报工审批'
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
$existing = @($models | Where-Object key -eq $ProcessKey)
if ($existing.Count -gt 1) { throw "Multiple '$ProcessKey' models found; inspect tenant model data." }
if ($existing.Count -eq 1 -and !$UpdateExisting) {
    Write-Output "Feedback model already exists: $($existing[0].id). Use -UpdateExisting to update and redeploy explicitly."
    return
}

$xml = [System.IO.File]::ReadAllText((Join-Path $PSScriptRoot 'feedback-approval.bpmn'))
if ($xml -notmatch '__APPROVER_USER_ID__') { throw 'Placeholder __APPROVER_USER_ID__ missing from feedback-approval.bpmn.' }
$xml = $xml.Replace('__APPROVER_USER_ID__', "$ApproverUserId")

$model = @{
    key                       = $ProcessKey
    name                      = $ProcessName
    category                  = $Category
    type                      = 10
    formType                  = 20
    visible                   = $false
    managerUserIds            = @($ManagerUserId)
    startUserIds              = @()
    startDeptIds              = @()
    autoApprovalType          = 0
    allowCancelRunningProcess = $false
    allowWithdrawTask         = $false
    # 业务表单（formType=20）部署时要求"创建路径"与"查看路径"都非空，
    # 见 BpmModelServiceImpl#validateFormConfig：任一为空即报 1_009_002_003。
    # 查看路径指向的组件供 BPM 流程详情页动态加载，展示报工单只读信息。
    formCustomCreatePath      = '/mes/pro/feedback'
    formCustomViewPath        = '/mes/pro/feedback/bpm-detail.vue'
    bpmnXml                   = $xml
}

if ($existing.Count -eq 1) {
    $model.id = $existing[0].id
    $null = Invoke-BpmApi 'PUT' "$api/update" $model
    $modelId = $model.id
}
else {
    $modelId = Invoke-BpmApi 'POST' "$api/create" $model
}

$null = Invoke-BpmApi 'POST' "$api/deploy?id=$([uri]::EscapeDataString($modelId))" $null
Write-Output "Deployed ${ProcessKey}: modelId=$modelId tenantId=$TenantId approverUserId=$ApproverUserId"
