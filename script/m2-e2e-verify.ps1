# M2 支付模块端到端验证脚本
# 链路：登录 -> 创建支付应用 -> 配置 mock 渠道 -> 创建示例订单 -> 发起支付 -> 验证订单/回调/钱包流水
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$base = 'http://localhost:48080/admin-api'

function Invoke-Api {
    param($Method, $Path, $Headers, $BodyObj)
    $args = @{
        Method      = $Method
        Uri         = ($base + $Path)
        Headers     = $Headers
        ContentType = 'application/json'
    }
    if ($BodyObj) {
        $json = $BodyObj | ConvertTo-Json -Depth 6 -Compress
        $args.Body = [System.Text.Encoding]::UTF8.GetBytes($json)
    }
    $resp = Invoke-RestMethod @args
    if ($resp.code -ne 0) { throw ("API " + $Path + " 失败: code=" + $resp.code + " msg=" + $resp.msg) }
    return $resp.data
}

# ===== 1. 租户与登录 =====
$tenantId = 1
Write-Output ("[1] 使用租户ID = " + $tenantId)

$headers = @{ 'tenant-id' = "$tenantId" }
$login = Invoke-Api -Method Post -Path '/system/auth/login' -Headers $headers -BodyObj @{
    username = 'admin'; password = 'admin123'
}
$headers['Authorization'] = 'Bearer ' + $login.accessToken
Write-Output ("[1] 登录成功, userId = " + $login.userId)

# ===== 2. 支付应用 =====
$apps = Invoke-Api -Method Get -Path '/pay/app/page?pageNo=1&pageSize=10' -Headers $headers
$app = $null
if ($apps.list -and $apps.list.Count -gt 0) {
    $app = $apps.list[0]
    Write-Output ("[2] 已存在支付应用: id=" + $app.id + " name=" + $app.name + " appKey=" + $app.appKey)
} else {
    $appId = Invoke-Api -Method Post -Path '/pay/app/create' -Headers $headers -BodyObj @{
        appKey           = 'txgy-demo'
        name             = '天信测试应用'
        status           = 0
        remark           = 'M2 端到端验证'
        payNotifyUrl     = 'http://127.0.0.1:48080/admin-api/pay/demo-order/update-paid'
        refundNotifyUrl  = 'http://127.0.0.1:48080/admin-api/pay/demo-order/update-refunded'
        transferNotifyUrl = 'http://127.0.0.1:48080/admin-api/pay/demo-withdraw/update-transferred'
    }
    $app = Invoke-Api -Method Get -Path ('/pay/app/get?id=' + $appId) -Headers $headers
    Write-Output ("[2] 新建支付应用: id=" + $app.id + " name=" + $app.name)
}
$appId = $app.id

# ===== 3. mock 渠道 =====
$channels = Invoke-Api -Method Get -Path ('/pay/channel/list?appId=' + $appId) -Headers $headers
$hasMock = $channels | Where-Object { $_.code -eq 'mock' }
if ($hasMock) {
    Write-Output "[3] mock 渠道已存在"
} else {
    Invoke-Api -Method Post -Path '/pay/channel/create' -Headers $headers -BodyObj @{
        appId  = $appId; code = 'mock'; status = 0; config = '{}'; remark = '模拟支付'; feeRate = 0; sort = 1
    } | Out-Null
    Write-Output "[3] 已创建 mock 渠道"
}

# ===== 4. 创建示例订单 =====
$demoOrderId = Invoke-Api -Method Post -Path '/pay/demo-order/create' -Headers $headers -BodyObj @{ spuId = 1 }
Write-Output ("[4] 示例订单已创建: demoOrderId=" + $demoOrderId)
$demoPage = Invoke-Api -Method Get -Path '/pay/demo-order/page?pageNo=1&pageSize=10' -Headers $headers
$demoOrder = $demoPage.list | Where-Object { $_.id -eq $demoOrderId } | Select-Object -First 1
$payOrderId = $demoOrder.payOrderId
Write-Output ("[4] 支付单ID = " + $payOrderId)

# ===== 5. 发起支付（mock 渠道支付即成功）=====
$submit = Invoke-Api -Method Post -Path '/pay/order/submit' -Headers $headers -BodyObj @{
    id = $payOrderId; channelCode = 'mock'
}
Write-Output ("[5] 支付提交: status=" + $submit.status + " displayMode=" + $submit.displayMode)

# ===== 6. 验证支付订单 =====
$orderPage = Invoke-Api -Method Get -Path ('/pay/order/page?pageNo=1&pageSize=10&appId=' + $appId) -Headers $headers
$order = $orderPage.list | Where-Object { $_.id -eq $payOrderId } | Select-Object -First 1
Write-Output ("[6] 支付订单: no=" + $order.no + " status=" + $order.status + "(10=已支付) price=" + $order.price + " channel=" + $order.channelCode)

# ===== 7. 验证回调通知 =====
Start-Sleep -Seconds 3
$notify = Invoke-Api -Method Get -Path '/pay/notify/page?pageNo=1&pageSize=10' -Headers $headers
if ($notify.list -and $notify.list.Count -gt 0) {
    $notify.list | ForEach-Object {
        Write-Output ("[7] 回调通知: id=" + $_.id + " type=" + $_.type + "(1=支付) status=" + $_.status + "(0=成功)")
    }
} else {
    Write-Output "[7] 暂无回调通知记录（可能由 PayNotifyJob 异步处理，稍后重查）"
}

# ===== 8. 钱包与钱包流水（验证新页面后端）=====
Invoke-Api -Method Put -Path '/pay/wallet/update-balance' -Headers $headers -BodyObj @{
    userId = 1; balance = 100000
} | Out-Null
$walletPage = Invoke-Api -Method Get -Path '/pay/wallet/page?pageNo=1&pageSize=10' -Headers $headers
$w = $walletPage.list | Select-Object -First 1
Write-Output ("[8] 会员钱包: walletId=" + $w.id + " userId=" + $w.userId + " balance=" + $w.balance)

$tx = Invoke-Api -Method Get -Path '/pay/wallet-transaction/page?pageNo=1&pageSize=10' -Headers $headers
if ($tx.list -and $tx.list.Count -gt 0) {
    $tx.list | ForEach-Object {
        Write-Output ("[8] 钱包流水: id=" + $_.id + " bizType=" + $_.bizType + " title=" + $_.title + " price=" + $_.price + " balance=" + $_.balance)
    }
} else {
    Write-Output "[8] 钱包流水暂无记录"
}

Write-Output "=== E2E 验证脚本执行完毕 ==="
