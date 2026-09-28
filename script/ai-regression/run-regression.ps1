# ============================================================
# MES-AI 助手回答质量回归执行器
#
# 用法（在 script/ai-regression 目录执行）：
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\run-regression.ps1
#   powershell ... -File .\run-regression.ps1 -CasesFile .\regression-cases.json -Report .\regression-report.txt
#
# 说明：
# 1. 每条用例独立新建会话并在结束时删除，用例之间零上下文污染
# 2. 全部用例幂等（只读 + 校验路径），不产生脏数据
# 3. 断言类型：contains_all（含全部关键词）/ contains_any（含任一）/ and+not 组合
# 4. 输出逐条 PASS/FAIL + 通过率 + 报告文件；退出码：全部通过=0，有失败=1（可接入 CI）
# ============================================================
param(
    [string]$CasesFile = "$PSScriptRoot\regression-cases.json",
    [string]$Report = ""
)

$ErrorActionPreference = 'Stop'
if (-not $Report) { $Report = "$PSScriptRoot\regression-report-$(Get-Date -Format 'yyyyMMdd-HHmmss').txt" }
$log = @()

function Write-Log($text) {
    [Console]::Out.WriteLine($text)
    Write-Output $text
    $script:log += $text
}

function Read-Body($cid, $q) {
    return @{
        conversationId = "$cid"
        content         = $q
        useContext      = $true
        useSearch       = $false
        attachmentUrls  = @()
    } | ConvertTo-Json -Compress
}

# ---------- 0. 加载用例 ----------
$cfg = Get-Content $CasesFile -Raw -Encoding UTF8 | ConvertFrom-Json
$baseUrl = $cfg.baseUrl
$tenantId = $cfg.tenantId
Write-Log ("用例集: " + $cfg.name + "（" + $cfg.cases.Count + " 条）")
Write-Log ("目标: " + $baseUrl + "  租户: " + $tenantId)
Write-Log ("=" * 70)

# ---------- 1. 登录 ----------
$loginBody = @{ username = $cfg.username; password = $cfg.password } | ConvertTo-Json -Compress
$login = Invoke-RestMethod -Uri "$baseUrl/admin-api/system/auth/login" -Method Post `
    -Headers @{ 'tenant-id' = $tenantId } -ContentType 'application/json;charset=UTF-8' `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($loginBody)) -TimeoutSec 30
if ($login.code -ne 0) { throw ("登录失败: " + $login.msg) }
$token = $login.data.accessToken
$headers = @{ 'tenant-id' = $tenantId; 'visit-tenant-id' = $tenantId; 'Authorization' = "Bearer $token" }
Write-Log "登录成功"
Write-Log ("")

# ---------- 2. 遍历用例 ----------
$pass = 0; $fail = 0; $index = 0
$createdConversations = @()

foreach ($case in $cfg.cases) {
    $index++
    $name = $case.name
    $question = $case.question
    $answer = ''
    $errorText = ''
    try {
        # 每条用例独立会话，避免上下文污染
        $nc = Invoke-RestMethod -Uri "$baseUrl/admin-api/ai/chat/conversation/create-my" -Method Post `
            -Headers $headers -ContentType 'application/json;charset=UTF-8' `
            -Body ('{"roleId":' + $cfg.roleId + '}') -TimeoutSec 30
        $cid = $nc.data
        $createdConversations += $cid

        $body = Read-Body $cid $question
        $tmp = [System.IO.Path]::GetTempFileName()
        [IO.File]::WriteAllText($tmp, $body, (New-Object System.Text.UTF8Encoding($false)))
        curl.exe -s -o $tmp -H "tenant-id: $tenantId" -H "visit-tenant-id: $tenantId" `
            -H "Authorization: Bearer $token" -H 'Content-Type: application/json' `
            --max-time 240 --data-binary "@$tmp" "$baseUrl/admin-api/ai/chat/message/send-stream" | Out-Null
        $raw = Get-Content $tmp -Raw -Encoding UTF8
        Remove-Item $tmp -Force -ErrorAction SilentlyContinue
        # 前端语义：按帧拼接 content
        foreach ($line in ($raw -split "`n")) {
            if ($line -match '^data:(\{.*\})$') {
                $frame = $Matches[1] | ConvertFrom-Json
                if ($frame.code -ne 0) { $errorText = $frame.msg; continue }
                $answer += $frame.data.receive.content
            }
        }
    } catch {
        $errorText = $_.Exception.Message + ' @ ' + $_.InvocationInfo.PositionMessage
    }

    # ---------- 断言 ----------
    $passCase = $false
    $reason = ''
    if ($errorText) {
        $reason = "接口异常: $errorText"
    } elseif (-not $answer.Trim()) {
        $reason = '回复为空'
    } else {
        $expect = $case.expect
        switch ($expect.type) {
            'contains_all' {
                $miss = @($expect.keywords | Where-Object { $answer -notmatch [regex]::Escape($_) })
                $passCase = ($miss.Count -eq 0)
                if (-not $passCase) { $reason = '缺少关键词: ' + ($miss -join '、') }
            }
            'contains_any' {
                $hit = @($expect.keywords | Where-Object { $answer -match [regex]::Escape($_) })
                $passCase = ($hit.Count -gt 0)
                if (-not $passCase) { $reason = '未命中任一关键词: ' + ($expect.keywords -join '、') }
            }
            'and' {
                $passCase = $true
                foreach ($sub in @($expect.all) + @($expect.not)) {
                    if ($null -eq $sub) { continue }
                    if ($sub.type -eq 'contains_any') {
                        $hit = @($sub.keywords | Where-Object { $answer -match [regex]::Escape($_) })
                        $ok = ($hit.Count -gt 0)
                    } elseif ($sub.type -eq 'contains_all') {
                        $miss = @($sub.keywords | Where-Object { $answer -notmatch [regex]::Escape($_) })
                        $ok = ($miss.Count -eq 0)
                        if (-not $ok) { $reason = '缺少关键词: ' + ($miss -join '、') }
                    } else { continue }
                    if ($expect.all -contains $sub -and -not $ok) { $passCase = $false }
                    if ($expect.not -contains $sub -and $ok) { $passCase = $false; $reason = '出现禁止内容' }
                }
            }
            default { $passCase = $answer.Trim().Length -gt 0 }
        }
    }

    if ($passCase) { $pass++ } else { $fail++ }
    $status = if ($passCase) { 'PASS' } else { 'FAIL' }
    Write-Log ("[$index/$($cfg.cases.Count)] $status  $name")
    if (-not $passCase) {
        Write-Log ("      问题: " + $question)
        $ansPreview = $answer.Trim() -replace "`r?`n", ' | '
        if ($ansPreview.Length -gt 160) { $ansPreview = $ansPreview.Substring(0, 160) + '…' }
        Write-Log ("      原因: " + $reason)
        Write-Log ("      回复: " + $ansPreview)
    }
}

# ---------- 3. 清理测试会话 ----------
foreach ($cid in $createdConversations) {
    try {
        Invoke-RestMethod -Uri "$baseUrl/admin-api/ai/chat/conversation/delete-my?id=$cid" -Method Delete `
            -Headers $headers -TimeoutSec 15 | Out-Null
    } catch { }
}

# ---------- 4. 汇总 ----------
$total = $cfg.cases.Count
$rate = if ($total -gt 0) { [math]::Round($pass * 100.0 / $total, 1) } else { 0 }
Write-Log ("" )
Write-Log ("=" * 70)
Write-Log ("回归结果: $pass/$total 通过（通过率 $rate%）  失败 $fail 条")
Write-Log ("报告文件: " + $Report)
$log | Out-File $Report -Encoding UTF8
if ($fail -gt 0) { exit 1 } else { exit 0 }
