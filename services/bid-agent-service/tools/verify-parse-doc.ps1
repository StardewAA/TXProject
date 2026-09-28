$ErrorActionPreference = 'Continue'
$src = $args[0]
$log = 'D:\招投标项目\.tools\verify-parse-doc.log'
function Log($m) { Add-Content $log ("[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $m) -Encoding UTF8 }
Set-Content $log '=== verify parse doc-scoped ===' -Encoding UTF8
$base = 'http://127.0.0.1:8199'
$body = @{ projectId = '1'; taskType = 'parse'; payload = @{ sourceDocName = $src } } | ConvertTo-Json -Depth 4
Log "create with sourceDocName=$src"
$created = Invoke-RestMethod -Method Post -Uri "$base/agent/tasks" -Headers @{ 'X-Tenant-Id' = '1'; 'Idempotency-Key' = ('parse-doc-' + (Get-Date).ToString('HHmmss')) } -ContentType 'application/json' -Body $body -TimeoutSec 30
$tid = $created.taskId
Log "taskId=$tid"
$done = $false
for ($i = 0; $i -lt 40; $i++) {
    Start-Sleep -Seconds 4
    $v = Invoke-RestMethod -Uri "$base/agent/tasks/$tid" -TimeoutSec 15
    if ($i % 3 -eq 0) { Log ("poll status={0} phase={1}" -f $v.status, $v.phase) }
    if ($v.status -in @('completed', 'failed')) { $done = $true; break }
    if ($v.status -eq 'awaiting_human') {
        $qs = @($v.pendingQuestions)
        if ($qs.Count -gt 0) {
            $ans = @($qs | ForEach-Object { @{ questionId = $_.questionId; value = 'ok' } })
            Invoke-RestMethod -Method Post -Uri "$base/agent/tasks/$tid/confirm" -ContentType 'application/json' -Body (@{ answers = $ans } | ConvertTo-Json -Depth 4) -TimeoutSec 30 | Out-Null
            Log 'confirm ok sent'
        }
    }
}
if (-not $done) { Log 'TIMEOUT'; exit 1 }
Log "final status=$($v.status) summary=$($v.summary)"
if ($v.status -eq 'completed') {
    try {
        $raw = (Invoke-WebRequest -Uri "$base/bid-file/download?tenantId=1&projectId=1&rel=tenderParse.json" -UseBasicParsing -TimeoutSec 15).Content
        $j = $raw | ConvertFrom-Json
        $clauses = @($j.invalidBidClauses)
        Log ("invalidBidClauses={0}" -f $clauses.Count)
        $nonTarget = @($j.globalFacts | Where-Object { $_.sourceDocName -and $_.sourceDocName -ne $src })
        Log ("globalFacts={0} 引用非目标文档条数={1}" -f @($j.globalFacts).Count, @($nonTarget).Count)
        $refs = $raw | Select-String -Pattern $src -AllMatches
        Log ("tenderParse 内目标文档引用次数={0}" -f $refs.Matches.Count)
    } catch { Log "read tenderParse err: $($_.Exception.Message)" }
}
Log '=== end ==='
