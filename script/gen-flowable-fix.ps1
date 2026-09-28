<#
生成 Flowable 表名大小写归一化脚本（输出到 script/flowable-fix.sql）

背景：容器内 MySQL 的 lower_case_table_names = 0（表名区分大小写），而 Flowable 8.0.0
      一律按大写访问表；初始化 dump 中的小写 act_*/flw_* 表必须先改名为大写。

退出码：
  0 = 已生成脚本
  2 = 库中已无小写表（无需归一化），已中止且未覆盖既有产物

安全约束：产物会无条件 DROP 全部大写表，**仅限【新库初始化】执行**。
          已初始化的库请改用幂等脚本：
          deploy/mysql/repair/2026-09-14-bpm-flowable-flw-uppercase-fix.sql
#>
$ErrorActionPreference = 'Stop'
$db = 'ruoyi-vue-pro'
$tables = docker exec deploy-mysql-1 mysql -uroot -proot --default-character-set=utf8mb4 -N -B $db -e "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='$db' AND (TABLE_NAME LIKE 'ACT\_%' OR TABLE_NAME LIKE 'act\_%' OR TABLE_NAME LIKE 'FLW\_%' OR TABLE_NAME LIKE 'flw\_%');"
if ($LASTEXITCODE -ne 0) {
    throw "查询 Flowable 表名失败（docker exec 退出码 $LASTEXITCODE）。请确认容器 deploy-mysql-1 正在运行。"
}
$upper = @()
$lower = @()
foreach ($t in $tables) {
    if ([string]::IsNullOrWhiteSpace($t)) { continue }
    if ($t -cmatch '^(ACT|FLW)_[A-Z0-9_]+$') { $upper += $t } else { $lower += $t }
}

# 防呆一：库里没有任何 Flowable 表（库未初始化 / 表前缀不符）——拒绝生成
# 否则会产出一个空脚本，看似成功实则什么都没修。
if (($upper.Count + $lower.Count) -eq 0) {
    throw "在库 '$db' 中未查到任何 ACT_*/FLW_* 表。已中止，未覆盖 flowable-fix.sql。"
}

# 防呆二：已无小写表（表名全为大写）——无需归一化，拒绝生成
# 若此时继续生成，产物将只含 DROP、不含任何 RENAME；在已归一化的库上执行会
# 删掉可用的大写表却不恢复，属破坏性操作。
if ($lower.Count -eq 0) {
    Write-Host "UPPER: $($upper.Count), LOWER: 0"
    Write-Host "库 '$db' 的 Flowable 表名已全部为大写，无需归一化。" -ForegroundColor Green
    Write-Host "已中止，未覆盖 flowable-fix.sql（避免生成只 DROP 不 RENAME 的危险脚本）。" -ForegroundColor Yellow
    exit 2
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
