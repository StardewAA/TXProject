# 支付管理模块全量 API 健康检查（只读，不改数据）
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$global:base = 'http://localhost:48080/admin-api'

# 登录
$global:h = @{ 'tenant-id' = '1' }
$login = Invoke-RestMethod -Method Post -Uri "$global:base/system/auth/login" -Headers $global:h -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes('{"username":"admin","password":"admin123"}'))
if ($login.code -ne 0) { Write-Output ("LOGIN-FAILED: " + $login.msg); exit 1 }
$global:h['Authorization'] = 'Bearer ' + $login.data.accessToken
Write-Output ("[OK] 登录, token=" + $login.data.accessToken.Substring(0, 12) + "...")

$global:pass = 0
$global:fail = 0
function Check($name, $path) {
    try {
        $r = Invoke-RestMethod ($global:base + $path) -Headers $global:h
        if ($r.code -eq 0) {
            $cnt = ''
            if ($r.data -is [Array]) { $cnt = " (" + @($r.data).Count + " 条)" }
            elseif ($r.data.list) { $cnt = " (共 " + $r.data.total + " 条)" }
            Write-Output ("[PASS] " + $name + $cnt)
            $global:pass++
        } else {
            Write-Output ("[FAIL] " + $name + " code=" + $r.code + " msg=" + $r.msg)
            $global:fail++
        }
    } catch {
        Write-Output ("[FAIL] " + $name + " 异常: " + $_.Exception.Message)
        $global:fail++
    }
}

Check '应用信息列表'      '/pay/app/page?pageNo=1&pageSize=5'
Check '支付渠道列表'      '/pay/channel/list?appId=9'
Check '支付订单分页'      '/pay/order/page?pageNo=1&pageSize=5'
Check '退款订单分页'      '/pay/refund/page?pageNo=1&pageSize=5'
Check '转账订单分页'      '/pay/transfer/page?pageNo=1&pageSize=5'
Check '钱包余额分页'      '/pay/wallet/page?pageNo=1&pageSize=5'
Check '充值套餐分页'      '/pay/wallet-recharge-package/page?pageNo=1&pageSize=5'
Check '回调通知分页'      '/pay/notify/page?pageNo=1&pageSize=5'
Check '钱包流水分页(新)'  '/pay/wallet-transaction/page?pageNo=1&pageSize=5'
Check '示例订单分页'      '/pay/demo-order/page?pageNo=1&pageSize=5'
Check '示例提现单分页'    '/pay/demo-withdraw/page?pageNo=1&pageSize=5'

Write-Output ("=== 冒烟结果: PASS=" + $global:pass + " FAIL=" + $global:fail + " ===")
