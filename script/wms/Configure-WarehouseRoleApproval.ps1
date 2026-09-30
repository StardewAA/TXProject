param(
    [Parameter(Mandatory)][string]$BaseUrl,
    [Parameter(Mandatory)][string]$Token,
    [Parameter(Mandatory)][long]$TenantId,
    [long]$ApproverRoleId,
    [switch]$UsePermissionCandidates
)
# Updates WMS models only. Role membership is managed separately through the normal user-role API.
$ErrorActionPreference = 'Stop'
if (!$UsePermissionCandidates -and $ApproverRoleId -le 0) { throw 'Specify UsePermissionCandidates or ApproverRoleId' }
$headers = @{ Authorization = "Bearer $Token"; 'tenant-id' = "$TenantId" }
$api = $BaseUrl.TrimEnd('/') + '/admin-api/bpm/model'
function Invoke-ModelApi([string]$Method, [string]$Uri, $Body = $null) {
    $request = @{ Method=$Method; Uri=$Uri; Headers=$headers }
    if ($null -ne $Body) {
        $request.ContentType='application/json; charset=utf-8'
        $request.Body=[Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 40))
    }
    $result = Invoke-RestMethod @request
    if ($result.code -ne 0) { throw "BPM model API: $($result.code) $($result.msg)" }
    return $result.data
}
function Test-DeployedRoleApproval($Definition, [string]$Direction, [string]$ModelId, [string]$CandidateStrategy, [string]$CandidateParam) {
    if (!$Definition -or !$Definition.bpmnXml -or $Definition.modelId -ne $ModelId -or
        $Definition.modelType -ne 10 -or $Definition.autoApprovalType -ne 0 -or
        $Definition.formCustomViewPath -ne "/wms/order/$Direction/bpm-detail.vue") { return $false }
    [xml]$deployedXml = $Definition.bpmnXml
    $tasks = $deployedXml.SelectNodes("//*[local-name()='userTask']")
    if ($tasks.Count -ne 1) { return $false }
    $task = $tasks[0]
    foreach ($expected in @(@('candidateStrategy',$CandidateStrategy),@('candidateParam',$CandidateParam),
        @('approveType','1'),@('approveMethod','3'),@('assignEmptyHandlerType','2'),
        @('assignStartUserHandlerType','1'),@('reasonRequire','true'),@('rejectHandlerType','1'))) {
        $node = $task.SelectSingleNode("./*[local-name()='extensionElements']/*[local-name()='$($expected[0])']")
        if (!$node -or $node.InnerText -ne $expected[1]) { return $false }
    }
    $loop = $task.SelectSingleNode("./*[local-name()='multiInstanceLoopCharacteristics']")
    return $null -ne $loop -and $loop.GetAttribute('isSequential') -eq 'false' -and
        $loop.GetAttribute('collection','http://flowable.org/bpmn') -eq '${coll_userList}' -and
        $loop.SelectSingleNode("./*[local-name()='completionCondition']").InnerText.Trim() -eq '${nrOfCompletedInstances > 0}'
}
$models = @(Invoke-ModelApi GET "$api/list")
$template = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'warehouse-role-approval.bpmn'))
foreach ($direction in @('receipt','shipment')) {
    $key = "wms_${direction}_approval"
    $existing = @($models | Where-Object key -eq $key)
    if ($existing.Count -ne 1) { throw "Expected exactly one existing WMS model for $key" }
    $model = Invoke-ModelApi GET "$api/get?id=$($existing[0].id)"
    $name = if ($direction -eq 'receipt') { '入库' } else { '出库' }
    $candidateStrategy = if ($UsePermissionCandidates) { '60' } else { '10' }
    $candidateParam = if ($UsePermissionCandidates) { '${wmsApprovalCandidates.resolve(''' + $key + ''')}' } else { "$ApproverRoleId" }
    $xml = $template.Replace('__PROCESS_KEY__',$key).Replace('__ORDER_NAME__',$name).Replace('__APPROVER_ROLE_ID__',$candidateParam)
    $xml = $xml.Replace('<flowable:candidateStrategy>10</flowable:candidateStrategy>',"<flowable:candidateStrategy>$candidateStrategy</flowable:candidateStrategy>")
    $definitionApi = $BaseUrl.TrimEnd('/') + "/admin-api/bpm/process-definition/get?key=$key"
    $activeDefinition = Invoke-ModelApi GET $definitionApi
    if ($model.type -eq 10 -and $model.bpmnXml.Trim() -eq $xml.Trim() -and
        $model.formCustomViewPath -eq "/wms/order/$direction/bpm-detail.vue" -and $model.autoApprovalType -eq 0 -and
        $model.name -eq "WMS ${name}审批" -and
        (Test-DeployedRoleApproval $activeDefinition $direction "$($model.id)" $candidateStrategy $candidateParam)) {
        Write-Output "Already deployed ${key}: candidate=$candidateParam, definition=$($activeDefinition.id)"
        continue
    }
    # Existing SIMPLE models regenerate XML from simpleModel; explicitly select BPMN for this template.
    $model.type = 10
    $model.name = "WMS ${name}审批"
    $model.bpmnXml = $xml
    $model.formCustomViewPath = "/wms/order/$direction/bpm-detail.vue"
    $model.autoApprovalType = 0
    $null = Invoke-ModelApi PUT "$api/update" $model
    $null = Invoke-ModelApi POST "$api/deploy?id=$($model.id)"
    $verified = Invoke-ModelApi GET $definitionApi
    if (!(Test-DeployedRoleApproval $verified $direction "$($model.id)" $candidateStrategy $candidateParam)) {
        throw "Deployed role configuration verification failed: $key"
    }
    Write-Output "Deployed ${key}: model=$($model.id), candidate=$candidateParam, tenant=$TenantId"
}
$headers=$null; $Token=$null
