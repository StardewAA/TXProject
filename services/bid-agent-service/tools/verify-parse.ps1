$ErrorActionPreference = 'Continue'
$log = 'D:\招投标项目\.tools\verify-parse.log'
function Log($m) { Add-Content $log ("[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $m) -Encoding UTF8 }
Set-Content $log '=== verify parse ===' -Encoding UTF8
$base = 'http://127.0.0.1:8199'
$hdr = @{ 'X-Tenant-Id' = '1'; 'Idempotency-Key' = ('parse-demo-' + (Get-Date).ToString('HHmmss')) }

# 1. create
$body = @{ projectId = '1'; taskType = 'parse'; payload = @{} } | ConvertTo-Json -Depth 3
$created = Invoke-RestMethod -Method Post -Uri "$base/agent/tasks" -Headers $hdr -ContentType 'application/json' -Body $body -TimeoutSec 30
$tid = $created.taskId
Log "taskId=$tid"

# 2. poll until terminal or awaiting_human
$final = $null
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 4
    $v = Invoke-RestMethod -Uri "$base/agent/tasks/$tid" -TimeoutSec 15
    Log ("poll#{0} status={1} phase={2} progress={3}" -f ($i + 1), $v.status, $v.phase, $v.progress)
    if ($v.status -in @('completed', 'failed')) { $final = $v; break }
    if ($v.status -eq 'awaiting_human') {
        $questions = @($v.pendingQuestions)
        Log ("HIL questions={0}" -f $questions.Count)
        if ($questions.Count -gt 0) {
            $qa = @()
            foreach ($q in $questions) { $qa += @{ questionId = $q.questionId; value = 'ok' } }
            $confirmBody = @{ answers = $qa } | ConvertTo-Json -Depth 3
            Invoke-RestMethod -Method Post -Uri "$base/agent/tasks/$tid/confirm" -Headers $hdr -ContentType 'application/json' -Body $confirmBody -TimeoutSec 30 | Out-Null
            Log 'confirm sent'
        }
    }
}
if ($null -eq $final) { Log 'TIMEOUT waiting'; exit 1 }
Log ("final status={0} summary={1}" -f $final.status, $final.summary)

# 3. outputs
try {
    $outs = Invoke-RestMethod -Uri "$base/agent/tasks/$tid/outputs" -TimeoutSec 15
    Log ("outputs files={0}" -f @($outs.files).Count)
    foreach ($f in @($outs.files)) { Log ("  {0}  ({1} B)" -f $f.rel, $f.bytes) }
} catch { Log "outputs err: $($_.Exception.Message)" }
Log '=== end ==='
