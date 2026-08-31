# 批量品牌替换：OPENLAB BS -> OPENLAB BS（保留原文件 BOM 状态，UTF-8 严格解码）
$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$exts = @('.java', '.vue', '.ts', '.tsx', '.js', '.jsx', '.json', '.yaml', '.yml', '.properties', '.md', '.html', '.sql', '.txt', '.xml', '.css', '.scss', '.less', '.ini', '.conf', '.kt', '.toml', '.py', '.sh', '.bat', '.ps1', '.http')
$excludeDirs = @('node_modules', 'dist', '.git', 'target', '.idea', '.pnpm-store')

$replacements = [ordered]@{
    '芋道源码'   = 'OPENLAB BS'
    '芋道管理系统' = 'OPENLAB BS 管理系统'
    '芋道 AI'    = 'OPENLAB BS AI'
    '芋道云'     = 'OPENLAB BS 云'
    '芋道'      = 'OPENLAB BS'
}

$utf8 = [System.Text.UTF8Encoding]::new($false, $true)  # 严格 UTF-8，无 BOM
$changed = 0

$files = Get-ChildItem -Path $root -Recurse -File | Where-Object {
    $dir = $_.DirectoryName
    if ($_.FullName -eq (Join-Path $PSScriptRoot 'replace-brand.ps1')) { return $false }
    $skip = $false
    foreach ($ex in $excludeDirs) {
        if ($dir -like "*\$ex\*" -or $dir.EndsWith("\$ex")) { $skip = $true; break }
    }
    -not $skip -and ($exts -contains $_.Extension.ToLower() -or $_.Extension -eq '' -or $_.Name.StartsWith('.'))
}

foreach ($file in $files) {
    try {
        $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
        $hasBom = $bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF
        $text = $utf8.GetString($bytes)
    } catch {
        Write-Host "SKIP(not utf8): $($file.FullName)"
        continue
    }
    $newText = $text
    foreach ($key in $replacements.Keys) {
        $newText = $newText.Replace($key, $replacements[$key])
    }
    if ($newText -ne $text) {
        $enc = [System.Text.UTF8Encoding]::new($hasBom)
        [System.IO.File]::WriteAllText($file.FullName, $newText, $enc)
        $changed++
    }
}

Write-Host "TOTAL CHANGED FILES: $changed"
