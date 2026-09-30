$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead((Join-Path $env:TEMP 'report-app.jar'))
$libDir = Join-Path $env:TEMP 'report-libs'
if (-not (Test-Path $libDir)) { New-Item -ItemType Directory -Path $libDir | Out-Null }
$zip.Entries | Where-Object { $_.FullName -match '^BOOT-INF/lib/[^/]+\.jar$' } | ForEach-Object {
    $dest = Join-Path $libDir ([System.IO.Path]::GetFileName($_.FullName))
    if (-not (Test-Path $dest)) { [System.IO.Compression.ZipFileExtensions]::ExtractToFile($_, $dest) }
}
$zip.Dispose()
$src = 'txgy-module-report\txgy-module-report-server\src\main\java\org\jeecg\modules\jmreport\ai\service\a\a.java'
$out = Join-Path $env:TEMP 'shadow-out'
if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Path $out | Out-Null
$cp = (Get-ChildItem $libDir -Filter *.jar | ForEach-Object { $_.FullName }) -join ';'
javac -encoding UTF-8 -cp $cp -d $out $src 2>&1
if ($LASTEXITCODE -eq 0) { Write-Host 'COMPILE OK' } else { Write-Host "COMPILE FAILED exit=$LASTEXITCODE" }
