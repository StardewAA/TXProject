param(
  [string]$BaseUrl = 'http://127.0.0.1:8080',
  [int]$TenantId = 1,
  [string]$BuyerUser = 'admin',
  [string]$BuyerPass = 'admin123',
  [string]$ExpertUser = 'expert01',
  [string]$ExpertPass = 'admin123',
  [long]$ExpertId = 1
)
$ErrorActionPreference = 'Stop'
$pass = 0
$fail = 0
function Step([string]$name, [bool]$ok, [string]$detail) {
  if ($ok) { $script:pass++; Write-Host "[PASS] $name $detail" -ForegroundColor Green }
  else { $script:fail++; Write-Host "[FAIL] $name $detail" -ForegroundColor Red }
}
function Login([string]$user, [string]$password) {
  $body = @{ username=$user; password=$password } | ConvertTo-Json -Compress
  $r = Invoke-RestMethod -Uri "$BaseUrl/admin-api/system/auth/login" -Method Post -Headers @{'tenant-id'="$TenantId";'Content-Type'='application/json'} -Body $body -TimeoutSec 30
  if ($r.code -ne 0) { throw "login failed: $user $($r.msg)" }
  return $r.data.accessToken
}
function Call([string]$method, [string]$path, [string]$token, $body=$null) {
  $args = @{ Uri="$BaseUrl$path"; Method=$method; Headers=@{'tenant-id'="$TenantId"; Authorization="Bearer $token"}; TimeoutSec=120 }
  if ($null -ne $body) { $args.ContentType='application/json'; $args.Body=($body | ConvertTo-Json -Compress -Depth 8) }
  try { return Invoke-RestMethod @args }
  catch { $raw=$_.ErrorDetails.Message; if($raw){ try{return $raw|ConvertFrom-Json}catch{} }; throw }
}
function WaitOrder([string]$token,[long]$id,[int[]]$statuses,[int]$seconds=90) {
  $deadline=(Get-Date).AddSeconds($seconds)
  while((Get-Date)-lt$deadline){
    $r=Call GET '/admin-api/cms/expert-consult/order/my' $token
    $o=$r.data|Where-Object {$_.id -eq $id}|Select-Object -First 1
    if($o -and $statuses -contains $o.status){return $o}
    Start-Sleep 2
  }
  return $null
}
Write-Host '=== Expert management closure ===' -ForegroundColor Cyan
$buyer=Login $BuyerUser $BuyerPass
$expert=Login $ExpertUser $ExpertPass
Step 'Login buyer/expert' ($buyer.Length -gt 10 -and $expert.Length -gt 10) "expertId=$ExpertId"
$appointment=(Get-Date).AddHours(2).ToString('yyyy-MM-ddTHH:mm:ss')
$create=Call POST '/admin-api/cms/expert-consult/order/create' $buyer @{expertId=$ExpertId;subject='Expert closure test';question='Verify payment IM completion readonly evaluation settlement';appointmentTime=$appointment}
Step 'Create order' ($create.code -eq 0 -and $create.data.status -eq 0) "orderNo=$($create.data.orderNo)"
$order=$create.data
$pay=Call POST '/admin-api/pay/order/submit' $buyer @{id=$order.payOrderId;channelCode='mock';channelExtras=@{}}
Step 'Submit payment' ($pay.code -eq 0) "payOrderId=$($order.payOrderId)"
$paid=WaitOrder $buyer $order.id @(1,2,3,4,5) 90
Step 'Payment callback' ($paid -and $paid.status -ge 1) "status=$($paid.status)"
$accept=Call POST "/admin-api/cms/expert-consult/order/action?orderId=$($order.id)" $expert @{action='ACCEPT'}
Step 'Expert accept' ($accept.code -eq 0 -and $accept.data.status -eq 2) "status=$($accept.data.status)"
$session=Call GET "/admin-api/cms/expert-consult/order/im-session?orderId=$($order.id)" $expert
Step 'Create IM session' ($session.code -eq 0 -and $session.data.imReady) "imGroupId=$($session.data.imGroupId)"
$groupId=[long]$session.data.imGroupId
$start=Call POST "/admin-api/cms/expert-consult/order/action?orderId=$($order.id)" $expert @{action='START'}
$req=Call POST "/admin-api/cms/expert-consult/order/action?orderId=$($order.id)" $expert @{action='REQUEST_COMPLETE'}
Step 'Start and request complete' ($start.data.status -eq 3 -and $req.data.status -eq 4) "status=$($req.data.status)"
$done=Call POST "/admin-api/cms/expert-consult/order/action?orderId=$($order.id)" $buyer @{action='COMPLETE'}
Step 'Buyer complete' ($done.code -eq 0 -and $done.data.status -eq 5) "status=$($done.data.status)"
Step 'IM read only time set' ($null -ne $done.data.imReadOnlyTime) "imReadOnlyTime=$($done.data.imReadOnlyTime)"
$message=Call POST '/admin-api/im/message/group/send' $buyer @{clientMessageId=[guid]::NewGuid().ToString();groupId=$groupId;type=101;content='{"content":"should be blocked"}';receipt=$false}
Step 'Completed group blocks send' ($message.code -ne 0) "code=$($message.code) msg=$($message.msg)"
$evaluation=Call POST "/admin-api/cms/expert-consult/order/evaluate?orderId=$($order.id)" $buyer @{rating=5;content='closure test'}
Step 'Evaluation' ($evaluation.code -eq 0 -and $evaluation.data.rating -eq 5) "evaluationId=$($evaluation.data.id)"
$settlements=Call GET '/admin-api/cms/expert-consult/settlement/list' $buyer
$settlement=$settlements.data|Where-Object {$_.orderId -eq $order.id}|Select-Object -First 1
Step 'Settlement generated' ($settlement -and $settlement.status -eq 0) "settlementId=$($settlement.id)"
$appointment2=(Get-Date).AddHours(3).ToString('yyyy-MM-ddTHH:mm:ss')
$create2=Call POST '/admin-api/cms/expert-consult/order/create' $buyer @{expertId=$ExpertId;subject='Refund test';question='Verify reject refund';appointmentTime=$appointment2}
$order2=$create2.data
$pay2=Call POST '/admin-api/pay/order/submit' $buyer @{id=$order2.payOrderId;channelCode='mock';channelExtras=@{}}
$paid2=WaitOrder $buyer $order2.id @(1,2,3,4,5) 90
Step 'Refund order paid' ($paid2 -and $paid2.status -ge 1) "status=$($paid2.status)"
$reject=Call POST "/admin-api/cms/expert-consult/order/action?orderId=$($order2.id)" $expert @{action='REJECT';reason='schedule conflict'}
Step 'Reject enters refunding' ($reject.code -eq 0 -and $reject.data.status -eq 9) "status=$($reject.data.status)"
$refunded=WaitOrder $buyer $order2.id @(10) 120
Step 'Refund closed' ($refunded -and $refunded.status -eq 10) "status=$($refunded.status) refundOrderId=$($refunded.refundOrderId)"
Write-Host "=== Result: pass=$pass fail=$fail ===" -ForegroundColor Cyan
if($fail -gt 0){exit 1}