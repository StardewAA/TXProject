# Local acceptance helpers. Secrets remain in process memory; never print or persist headers/config.
$ErrorActionPreference = 'Stop'
function Invoke-WmsLocalSql([string]$Sql) {
    $mysqlConfig = @(docker inspect deploy-mysql-1 | ConvertFrom-Json)[0]
    $passwordEntry = $mysqlConfig.Config.Env | Where-Object { $_.StartsWith('MYSQL_ROOT_PASSWORD=') } | Select-Object -First 1
    if (!$passwordEntry) { throw 'Local MySQL credential unavailable' }
    $secret = $passwordEntry.Substring('MYSQL_ROOT_PASSWORD='.Length)
    $Sql | docker exec -i --env "MYSQL_PWD=$secret" deploy-mysql-1 mysql --default-character-set=utf8mb4 -uroot -N -B ruoyi-vue-pro
    if ($LASTEXITCODE -ne 0) { throw 'Local SQL failed' }
    $secret = $null; $passwordEntry = $null; $mysqlConfig = $null
}
function Get-WmsLocalHeaders {
    $token = Invoke-WmsLocalSql "SELECT access_token FROM system_oauth2_access_token WHERE user_id=80168 AND tenant_id=2005 AND deleted=0 AND expires_time>NOW() ORDER BY expires_time DESC LIMIT 1;"
    if (!$token) { throw '宋凯的本机登录已过期，请在浏览器登录后重试' }
    return @{ Authorization = "Bearer $token"; 'tenant-id' = '2005' }
}
function Invoke-WmsLocalApi([string]$Method, [string]$Path, $Body = $null) {
    if (!$Path.StartsWith('/wms/') -and !$Path.StartsWith('/bpm/')) { throw 'Only WMS/BPM acceptance APIs are allowed' }
    $args = @{ Method=$Method; Uri=('http://localhost:5173/admin-api'+$Path); Headers=(Get-WmsLocalHeaders) }
    if ($null -ne $Body) {
        $args.ContentType = 'application/json; charset=utf-8'
        $args.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 40))
    }
    $result = Invoke-RestMethod @args
    if ($result.code -ne 0) { throw "WMS/BPM API failed: $($result.code) $($result.msg)" }
    return $result.data
}
