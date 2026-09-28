param(
  [string]$BaseUrl = 'http://127.0.0.1:8080',
  [int]$TenantId = 1,
  [string]$BuyerUser = 'admin',
  [string]$BuyerPass = 'admin123',
  [string]$ExpertUser = 'expert01',
  [string]$ExpertPass = 'admin123'
)
$ErrorActionPreference = 'Stop'
$script:pass = 0; $script:fail = 0

function Step([string]$name, [bool]$ok, [string]$detail) {
  if ($ok) { $script:pass++; Write-Host ("  [PASS] {0} {1}" -f $name, $detail) -ForegroundColor Green }
  else { $script:fail++; Write-Host ("  [FAIL] {0} {1}" -f $name, $detail) -ForegroundColor Red }
}

function Login([string]$username, [string]$password) {
  $body = @{ username = $username; password = $password } | ConvertTo-Json -Compress
  $r = Invoke-RestMethod -Uri "$BaseUrl/admin-api/system/auth/login" -Method Post -Headers @{ 'tenant-id' = "$TenantId" } -ContentType 'application/json' -Body $body
  if ($r.code -ne 0) { throw "login failed for $username : $($r.msg)" }
  return $r.data.accessToken
}

function Call([string]$method, [string]$path, [string]$token, $body) {
  $headers = @{ 'tenant-id' = "$TenantId"; Authorization = "Bearer $token" }
  $args = @{ Uri = "$BaseUrl$path"; Method = $method; Headers = $headers; TimeoutSec = 120 }
  if ($body -ne $null) { $args.ContentType = 'application/json'; $args.Body = ($body | ConvertTo-Json -Compress -Depth 6) }
  return Invoke-RestMethod @args
}

function WaitOrder([string]$token, [long]$orderId, [int[]]$statuses, [int]$seconds = 60) {
  $deadline = (Get-Date).AddSeconds($seconds)
  while ((Get-Date) -lt $deadline) {
    $r = Call 'GET' '/admin-api/cms/expert-consult/order/my' $token $null
    $o = $r.data | Where-Object { $_.id -eq $orderId } | Select-Object -First 1
    if ($o -and ($statuses -contains $o.status)) { return $o }
    Start-Sleep -Seconds 2
  }
  return $null
}

Write-Host "== M3/M4 验收 ==" -ForegroundColor Cyan
$buyer = Login $BuyerUser $BuyerPass
$expert = Login $ExpertUser $ExpertPass
Step '登录买家/专家' ($buyer.Length -gt 10 -and $expert.Length -gt 10) "buyer=$BuyerUser expert=$ExpertUser"

# —— 场景一：完整闭环 ——
Write-Host "`n[场景一] 下单 -> 支付 -> 接单 -> 完成 -> 评价 -> 结算" -ForegroundColor Cyan
$create = Call 'POST' '/admin-api/cms/expert-consult/order/create' $buyer @{ expertId = 1; subject = 'M3 联调-标准咨询'; question = '请解读 GB/T 50126 保温层厚度要求' }
$order = $create.data
Step '创建订单' ($create.code -eq 0 -and $order.status -eq 0) "orderNo=$($order.orderNo) price=$($order.price)"
Step '生成支付单' ($order.payOrderId -ne $null) "payOrderId=$($order.payOrderId)"

$submit = Call 'POST' '/admin-api/pay/order/submit' $buyer @{ id = $order.payOrderId; channelCode = 'mock'; channelExtras = @{} }
Step '提交支付(mock)' ($submit.code -eq 0) "msg=$($submit.msg)"

$paid = WaitOrder $buyer $order.id @(1,2,3,4,5) 60
Step '支付回调生效' ($paid -ne $null -and $paid.status -ge 1) "status=$($paid.status) paidTime=$($paid.paidTime)"
Step 'IM 会话已开通(会话隔离)' ($paid -ne $null -and $paid.imGroupId -ne $null) "imGroupId=$($paid.imGroupId)"

$acc = Call 'POST' "/admin-api/cms/expert-consult/order/action?orderId=$($order.id)" $expert @{ action = 'ACCEPT' }
$sta = Call 'POST' "/admin-api/cms/expert-consult/order/action?orderId=$($order.id)" $expert @{ action = 'START' }
$req = Call 'POST' "/admin-api/cms/expert-consult/order/action?orderId=$($order.id)" $expert @{ action = 'REQUEST_COMPLETE' }
Step '专家接单/开始/请求完成' ($acc.data.status -eq 2 -and $sta.data.status -eq 3 -and $req.data.status -eq 4) "status=$($req.data.status)"

$done = Call 'POST' "/admin-api/cms/expert-consult/order/action?orderId=$($order.id)" $buyer @{ action = 'COMPLETE' }
Step '买家确认完成' ($done.data.status -eq 5) "status=$($done.data.status)"

$eva = Call 'POST' "/admin-api/cms/expert-consult/order/evaluate?orderId=$($order.id)" $buyer @{ rating = 5; content = 'M3 联调评价：专业、及时' }
Step '评价(5星)' ($eva.code -eq 0 -and $eva.data.rating -eq 5) "evaluationId=$($eva.data.id)"

$settleList = Call 'GET' '/admin-api/cms/expert-consult/settlement/list' $buyer $null
$settle = $settleList.data | Where-Object { $_.orderId -eq $order.id } | Select-Object -First 1
Step '自动生成结算单' ($settle -ne $null -and $settle.status -eq 0) "settlementId=$($settle.id) amount=$($settle.settlementAmount)"
$settled = Call 'POST' "/admin-api/cms/expert-consult/settlement/settle?settlementId=$($settle.id)" $buyer @{ proofUrl = 'http://local/transfer-receipt.png'; proofNo = "M3-$($order.orderNo)"; remark = 'M3 联调线下结算' }
Step '人工结算登记' ($settled.code -eq 0 -and $settled.data.status -eq 1) "proofNo=$($settled.data.proofNo)"

# —— 场景二：拒单退款 ——
Write-Host "`n[场景二] 下单 -> 支付 -> 专家拒单 -> 退款闭环" -ForegroundColor Cyan
$c2 = Call 'POST' '/admin-api/cms/expert-consult/order/create' $buyer @{ expertId = 1; subject = 'M3 联调-退款场景'; question = '拒单退款验证' }
$o2 = $c2.data
$submit2 = Call 'POST' '/admin-api/pay/order/submit' $buyer @{ id = $o2.payOrderId; channelCode = 'mock'; channelExtras = @{} }
$paid2 = WaitOrder $buyer $o2.id @(1,2,3,4,5) 60
Step '场景二支付成功' ($paid2 -ne $null -and $paid2.status -ge 1) "orderNo=$($o2.orderNo) status=$($paid2.status)"
$rej = Call 'POST' "/admin-api/cms/expert-consult/order/action?orderId=$($o2.id)" $expert @{ action = 'REJECT'; reason = '专家档期冲突' }
Step '专家拒单进入退款中' ($rej.data.status -eq 9) "status=$($rej.data.status)"
$refunded = WaitOrder $buyer $o2.id @(10) 60
Step '退款回调闭环' ($refunded -ne $null -and $refunded.status -eq 10) "status=$($refunded.status)"

Write-Host "`n== 结果: 通过 $script:pass 项 / 失败 $script:fail 项 ==" -ForegroundColor Cyan
if ($script:fail -gt 0) { exit 1 }