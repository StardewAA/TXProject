$ErrorActionPreference = 'Stop'

$src = 'sql/00-sql文件/sql/mysql/ruoyi-vue-pro.sql'
$hrmOut = 'sql/hrm_menu.sql'
$fmsOut = 'sql/fms_menu.sql'

# 解析 ruoyi-vue-pro.sql 中的 system_menu INSERT 行
$rows = @()
Get-Content $src -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^INSERT INTO `system_menu` \(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`\) VALUES \((.*)\);$') {
        $v = $matches[1]
        $vals = @()
        $i = 0
        $start = 0
        while ($i -lt $v.Length) {
            if ($v[$i] -eq "'") {
                $i++
                while ($i -lt $v.Length -and $v[$i] -ne "'") { $i++ }
            }
            if ($i -ge $v.Length -or $v[$i] -eq ',') {
                $end = if ($i -ge $v.Length) { $i } else { $i }
                $vals += $v.Substring($start, $end - $start).Trim()
                $start = $i + 1
            }
            $i++
        }
        # 补充最后一个值（行尾不以逗号结尾时，循环内不会追加）
        if ($start -lt $v.Length) {
            $vals += $v.Substring($start).Trim()
        }
        $rows += ,@($vals)
    }
}

function Get-SubtreeIds([int]$rootId) {
    $ids = @()
    $frontier = @($rootId)
    while ($frontier.Count -gt 0) {
        $children = @($rows | Where-Object { $frontier -contains [int]$_[5] -and $ids -notcontains [int]$_[0] } | ForEach-Object { [int]$_[0] })
        if ($children.Count -eq 0) { break }
        $ids += $children
        $frontier = $children
    }
    $ids += $rootId
    return $ids
}

function Format-Value([string]$val) {
    if ($val -eq 'NULL') { return 'NULL' }
    if ($val -match '^b''[01]''$') { return $val }
    if ($val -match '^''.*''$') { return $val }
    if ($val -match '^-?\d+$') { return $val }
    return "'$val'"
}

function Emit-MenuLine($row, [int]$idOffset) {
    $id = [int]$row[0]
    $parent = [int]$row[5]
    $newId = if ($idOffset -ne 0) { $id + $idOffset } else { $id }
    $newParent = if ($parent -eq 0) { 0 } else { $parent + $idOffset }
    $parts = @(
        $newId,
        (Format-Value $row[1]),
        (Format-Value $row[2]),
        $row[3],
        $row[4],
        $newParent,
        (Format-Value $row[6]),
        (Format-Value $row[7]),
        (Format-Value $row[8]),
        (Format-Value $row[9]),
        $row[10],
        $row[11],
        $row[12],
        $row[13],
        (Format-Value $row[14]),
        (Format-Value $row[15]),
        (Format-Value $row[16]),
        (Format-Value $row[17]),
        $row[18]
    )
    $cols = '(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)'
    return "INSERT INTO system_menu $cols VALUES ($($parts -join ', '));"
}

$colDef = '-- 列：id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted'

# ============ HRM：官方菜单（ID 1476~1636，与运行库无冲突，原样保留） ============
$hrmIds = Get-SubtreeIds 1476
$hrmRows = $rows | Where-Object { $hrmIds -contains [int]$_[0] } | Sort-Object { [int]$_[0] }
$hrmLines = @()
$hrmLines += '-- ====================================================================='
$hrmLines += '-- HRM 人力资源官方菜单（提取自 sql/00-sql文件/sql/mysql/ruoyi-vue-pro.sql）'
$hrmLines += '-- 官方 ID 段：1476 ~ 1636（与当前运行库无冲突）'
$hrmLines += '-- 说明：超级管理员(super_admin)自动拥有全部菜单，无需绑定 system_role_menu'
$hrmLines += $colDef
$hrmLines += '-- ====================================================================='
$hrmLines += ''
$hrmLines += '-- 清理旧的手工菜单（ID 30001~30060），防止重复'
$hrmLines += 'DELETE FROM system_menu WHERE id BETWEEN 30001 AND 30060;'
$hrmLines += ''
foreach ($r in $hrmRows) { $hrmLines += Emit-MenuLine $r 0 }
$hrmLines -join "`r`n" | Set-Content -Path $hrmOut -Encoding UTF8

# ============ FMS：官方菜单（ID 1894~2026 与运行库商城菜单冲突，重映射到 32000~32132） ============
$fmsIds = Get-SubtreeIds 1894
$fmsRows = $rows | Where-Object { $fmsIds -contains [int]$_[0] } | Sort-Object { [int]$_[0] }
$offset = 32000 - 1894  # 30106
$fmsLines = @()
$fmsLines += '-- ====================================================================='
$fmsLines += '-- FMS 财务管理官方菜单（提取自 sql/00-sql文件/sql/mysql/ruoyi-vue-pro.sql）'
$fmsLines += '-- 官方 ID 段 1894~2026 与运行库商城菜单冲突，重映射为 32000~32132'
$fmsLines += '-- 说明：超级管理员(super_admin)自动拥有全部菜单，无需绑定 system_role_menu'
$fmsLines += $colDef
$fmsLines += '-- ====================================================================='
$fmsLines += ''
foreach ($r in $fmsRows) { $fmsLines += Emit-MenuLine $r $offset }
$fmsLines -join "`r`n" | Set-Content -Path $fmsOut -Encoding UTF8

Write-Host "HRM menu lines: $($hrmRows.Count), ids: $($hrmRows[0][0]) ~ $($hrmRows[-1][0])"
Write-Host "FMS menu lines: $($fmsRows.Count), remapped to 32000 ~ $(([int]$fmsRows[-1][0]) + $offset)"
