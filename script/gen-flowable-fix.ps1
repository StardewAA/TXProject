$ErrorActionPreference = 'Stop'
$db = 'ruoyi-vue-pro'
$tables = docker exec deploy-mysql-1 mysql -uroot -proot --default-character-set=utf8mb4 -N -B $db -e "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='$db' AND (TABLE_NAME LIKE 'ACT\_%' OR TABLE_NAME LIKE 'act\_%' OR TABLE_NAME LIKE 'FLW\_%' OR TABLE_NAME LIKE 'flw\_%');"
$upper = @()
$lower = @()
foreach ($t in $tables) {
    if ($t -cmatch '^(ACT|FLW)_[A-Z0-9_]+$') { $upper += $t } else { $lower += $t }
}
$lines = @()
$lines += '-- Flowable 表名大小写修复：删除残缺的大写表，将完整的小写表改名为大写（ACT_* / FLW_*）'
$lines += '--'
$lines += '-- !!! 仅限【新库初始化】执行 !!!'
$lines += '-- 本脚本会无条件 DROP 全部大写 ACT_*/FLW_* 表，再 RENAME 小写表。'
$lines += '-- 对已归一化（表名已为大写）的库执行时，会先删掉可用的大写表、随后因小写表不存在'
$lines += '-- 而报错中断，反而把库弄坏（若已有流程实例将造成数据丢失）。'
$lines += '-- 已初始化的库如需修复 FLW_* 大小写，请改用幂等脚本：'
$lines += '--   deploy/mysql/repair/2026-09-14-bpm-flowable-flw-uppercase-fix.sql'
$lines += '--'
foreach ($t in ($upper | Sort-Object)) {
    $lines += "DROP TABLE IF EXISTS ``$t``;"
}
foreach ($t in ($lower | Sort-Object)) {
    $up = $t.ToUpperInvariant()
    $lines += "RENAME TABLE ``$t`` TO ``$up``;"
}
$lines += ''
$sql = ($lines -join "`r`n") + "`r`n"
[System.IO.File]::WriteAllText((Join-Path $PSScriptRoot 'flowable-fix.sql'), $sql, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "UPPER: $($upper.Count), LOWER: $($lower.Count)"
Write-Host "SQL written: script/flowable-fix.sql"
