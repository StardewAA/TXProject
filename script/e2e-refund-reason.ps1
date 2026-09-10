# 端到端验证：创建订单 -> 支付 -> 带自定义原因退款 -> 验证退款单原因
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$base = 'http://localhost:48080/admin-api'

$h = @{ 'tenant-id' = '1' }
$login = Invoke-RestMethod -Method Post -Uri "$base/system/auth/login" -Headers $h -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes('{"username":"admin","password":"admin123"}'))
$h['Authorization'] = 'Bearer ' + $login.data.accessToken

# 1. 创建示例订单（苹果手表 1.00 元）
$demoOrderId = (Invoke-RestMethod -Method Post -Uri "$base/pay/demo-order/create" -Headers $h -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes('{"spuId":3}'))).data
Write-Output ("[1] 示例订单已创建: " + $demoOrderId)

# 2. 找到支付单并发起支付
$page = (Invoke-RestMethod "$base/pay/demo-order/page?pageNo=1&pageSize=5" -Headers $h).data
$order = $page.list | Where-Object { $_.id -eq $demoOrderId }
$payOrderId = $order.payOrderId
$submit = Invoke-RestMethod -Method Post -Uri "$base/pay/order/submit" -Headers $h -ContentType 'application/json' -Body ('{"id":' + $payOrderId + ',"channelCode":"mock"}')
Write-Output ("[2] 支付提交: 支付单=" + $payOrderId + " status=" + $submit.data.status + " (10=已支付)")

# 3. 带自定义原因退款
Start-Sleep -Seconds 2
$reason = [uri]::EscapeDataString('商品质量问题-测试自定义原因')
$refund = Invoke-RestMethod -Method Put -Uri "$base/pay/demo-order/refund?id=$demoOrderId&reason=$reason" -Headers $h
Write-Output ("[3] 退款发起: code=" + $refund.code)

# 4. 验证退款单原因与状态
Start-Sleep -Seconds 3
$refundPage = (Invoke-RestMethod "$base/pay/refund/page?pageNo=1&pageSize=5" -Headers $h).data
$latest = $refundPage.list | Select-Object -First 1
Write-Output ("[4] 最新退款单: 退款原因=""" + $latest.reason + """  状态=" + $latest.status + " (1=成功)  金额=" + $latest.price + " 分")

# 5. 验证示例订单退款状态
Start-Sleep -Seconds 2
$page2 = (Invoke-RestMethod "$base/pay/demo-order/page?pageNo=1&pageSize=5" -Headers $h).data
$order2 = $page2.list | Where-Object { $_.id -eq $demoOrderId }
Write-Output ("[5] 示例订单: payStatus=" + $order2.payStatus + " refundPrice=" + $order2.refundPrice + " 分")

if ($latest.reason -eq '商品质量问题-测试自定义原因' -and $latest.status -eq 1) {
    Write-Output "=== E2E 退款原因验证: 通过 ==="
} else {
    Write-Output "=== E2E 退款原因验证: 未通过，请检查 ==="
}
