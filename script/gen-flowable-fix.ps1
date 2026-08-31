$ErrorActionPreference = 'Stop'
$db = 'ruoyi-vue-pro'
$tables = docker exec deploy-mysql-1 mysql -uroot -proot --default-character-set=utf8mb4 -N -B $db -e "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='$db' AND (TABLE_NAME LIKE 'ACT\_%' OR TABLE_NAME LIKE 'act\_%');"
$upper = @()
$lower = @()
foreach ($t in $tables) {
    if ($t -cmatch '^ACT_[A-Z0-9_]+$') { $upper += $t } else { $lower += $t }
}
$lines = @()
$lines += '-- Flowable 表名大小写修复：删除残缺的大写表，将完整的小写表改名为大写'
foreach ($t in ($upper | Sort-Object)) {
    $lines += "DROP TABLE IF EXISTS ``$t``;"
}
foreach ($t in ($lower | Sort-Object)) {
    $up = $t.ToUpperInvariant()
    $lines += "RENAME TABLE ``$t`` TO ``$up``;"
}
$lines += ''
$lines -join "`r`n" | Set-Content -Path (Join-Path $PSScriptRoot 'flowable-fix.sql') -Encoding UTF8
Write-Host "UPPER: $($upper.Count), LOWER: $($lower.Count)"
Write-Host "SQL written: script/flowable-fix.sql"
