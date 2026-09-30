$ErrorActionPreference = 'Continue'
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$log = Join-Path $root '.tools\run-bid.log'
$answerPath = Join-Path $root '.tools\hil-answer.txt'
function Log($m) { Add-Content $log ("[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $m) -Encoding UTF8 }
Set-Content $log '=== run bid (real LLM) ===' -Encoding UTF8

$base = 'http://127.0.0.1:8199'
$existing = if ($args.Count -gt 0) { [string]$args[0] } else { '' }
$src = if ($args.Count -gt 1) { [string]$args[1] } else { '' }
$answer = [IO.File]::ReadAllText($answerPath, [Text.Encoding]::UTF8).Trim()
if (-not $answer) { $answer = 'confirm' }
Log ("existing=" + $existing)
Log ("src=" + $src)

function SendJson($method, $uri, $obj) {
    $json = $obj | ConvertTo-Json -Depth 6
    $bytes = [Text.Encoding]::UTF8.GetBytes($json)
    Invoke-RestMethod -Method $method -Uri $uri -Headers @{ 'X-Tenant-Id' = '1'; 'Content-Type' = 'application/json; charset=utf-8' } -Body $bytes -TimeoutSec 120
}

function WaitTask($tid, $rounds, $label) {
    for ($i = 0; $i -lt $rounds; $i++) {
        Start-Sleep -Seconds 8
        try { $v = Invoke-RestMethod -Uri "$base/agent/tasks/$tid" -TimeoutSec 20 } catch { Log ("  poll err: " + $_.Exception.Message); continue }
        Log ("  [{0}] poll#{1} status={2} phase={3} progress={4}" -f $label, ($i + 1), $v.status, $v.phase, $v.progress)
        if ($v.status -in @('completed', 'failed', 'interrupted')) { return $v }
        if ($v.status -eq 'awaiting_human') {
            $qs = @($v.pendingQuestions)
            if ($qs.Count -gt 0) {
                $qa = @()
                foreach ($q in $qs) { $qa += @{ questionId = $q.questionId; value = $answer } }
                try {
                    SendJson 'Post' "$base/agent/tasks/$tid/confirm" @{ answers = $qa } | Out-Null
                    Log ("  HIL answered x{0}" -f $qs.Count)
                } catch { Log ("  HIL err: " + $_.Exception.Message) }
            } else { Start-Sleep -Seconds 4 }
        }
    }
    Log ("  {0} TIMEOUT" -f $label)
    return $null
}

function RunStep($type, $rounds) {
    $payload = @{}
    if ($src) { $payload.sourceDocName = $src }
    $created = SendJson 'Post' "$base/agent/tasks" @{ projectId = '1'; taskType = $type; payload = $payload }
    Log ("== {0} taskId={1}" -f $type, $created.taskId)
    $v = WaitTask $created.taskId $rounds $type
    return @{ taskId = $created.taskId; view = $v }
}

$parseTaskId = $existing
if ($parseTaskId) {
    Log ("attach parse " + $parseTaskId)
    $pv = WaitTask $parseTaskId 240 'parse'
} else {
    $r1 = RunStep 'parse' 240
    $pv = $r1.view
    $parseTaskId = $r1.taskId
}
Log ("parse => " + $(if ($pv) { $pv.status } else { 'timeout' }))

$r2 = RunStep 'outline' 240
Log ("outline => " + $(if ($r2.view) { $r2.view.status } else { 'timeout' }))

$r3 = RunStep 'draft' 900
Log ("draft => " + $(if ($r3.view) { $r3.view.status } else { 'timeout' }))

try {
    $exp = Invoke-RestMethod -Uri "$base/bid-file/export?tenantId=1&projectId=1" -TimeoutSec 900
    Log ("export ok={0} chapters={1} docx={2} pdf={3}" -f $exp.ok, $exp.chapters, $exp.docxRel, $exp.pdfRel)
} catch { Log ("export err: " + $_.Exception.Message) }

foreach ($t in @($r3.taskId, $r2.taskId, $parseTaskId)) {
    if (-not $t) { continue }
    try {
        $o = Invoke-RestMethod -Uri "$base/agent/tasks/$t/outputs" -TimeoutSec 20
        Log ("outputs of " + $t + ":")
        foreach ($f in @($o.files)) { Log ("  " + $f.rel + "  (" + $f.bytes + " B)") }
    } catch { }
}
Log '=== end ==='
