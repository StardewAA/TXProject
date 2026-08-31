[CmdletBinding()]
param(
    [switch]$WhatIf
)

$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$sourceSqlDirectory = Join-Path $repositoryRoot 'sql/00-sql文件'
$mysqlUpdateDirectory = Join-Path $repositoryRoot 'sql/mysql'
$initDirectory = Join-Path $PSScriptRoot 'mysql/init'

if (-not (Test-Path -LiteralPath $sourceSqlDirectory -PathType Container)) {
    throw "未找到 SQL 源目录: $sourceSqlDirectory"
}

if (-not (Test-Path -LiteralPath $mysqlUpdateDirectory -PathType Container)) {
    throw "未找到 MySQL 更新目录: $mysqlUpdateDirectory"
}

if ($WhatIf) {
    Write-Output '预检通过：将先处理 sql/00-sql文件，再处理 sql/mysql。'
    exit 0
}

if (Test-Path -LiteralPath $initDirectory) {
    Remove-Item -LiteralPath $initDirectory -Recurse -Force
}

New-Item -ItemType Directory -Path $initDirectory -Force | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem

$script:sequence = 1
$manifest = New-Object System.Collections.Generic.List[string]

function Add-SqlFile {
    param(
        [Parameter(Mandatory)] [string] $SourcePath,
        [Parameter(Mandatory)] [string] $Phase,
        [Parameter(Mandatory)] [string] $Description
    )

    $targetName = '{0:D4}-{1}-{2}.sql' -f $script:sequence, $Phase, ([System.IO.Path]::GetFileName($SourcePath))
    $targetPath = Join-Path $initDirectory $targetName
    [System.IO.File]::Copy($SourcePath, $targetPath, $false)
    $manifest.Add("$targetName`t$Description")
    $script:sequence++
}

Get-ChildItem -LiteralPath $sourceSqlDirectory -File | Sort-Object Name | ForEach-Object {
    if ($_.Extension -ieq '.sql') {
        Add-SqlFile -SourcePath $_.FullName -Phase 'source' -Description $_.Name
        return
    }

    if ($_.Extension -ine '.zip') {
        return
    }

    $archive = [System.IO.Compression.ZipFile]::OpenRead($_.FullName)
    try {
        $archive.Entries |
            Where-Object { $_.FullName -match '\.sql$' -and $_.FullName -notmatch '(^|/)__MACOSX/' } |
            Sort-Object FullName |
            ForEach-Object {
                $targetName = '{0:D4}-source-{1}-{2}.sql' -f $script:sequence, $_.FullName.Replace('/', '_'), $_.Name
                $targetPath = Join-Path $initDirectory $targetName
                [System.IO.Compression.ZipFileExtensions]::ExtractToFile($_, $targetPath, $false)
                $manifest.Add("$targetName`t$($PSItem.Name)::$($_.FullName)")
                $script:sequence++
            }
    }
    finally {
        $archive.Dispose()
    }
}

Get-ChildItem -LiteralPath $mysqlUpdateDirectory -Recurse -File -Filter '*.sql' |
    Sort-Object FullName |
    ForEach-Object {
        Add-SqlFile -SourcePath $_.FullName -Phase 'mysql' -Description $_.FullName.Substring($mysqlUpdateDirectory.Length + 1)
    }

$manifestPath = Join-Path $initDirectory 'MANIFEST.tsv'
[System.IO.File]::WriteAllLines($manifestPath, $manifest, [System.Text.UTF8Encoding]::new($false))
Write-Output "已生成 $($manifest.Count) 个 SQL 初始化脚本：$initDirectory"
