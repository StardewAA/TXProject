# Initialize the ruoyi-vue-pro database for local dev.
#
# IMPORTANT: the incremental *.sql files under deploy\mysql\init (0001..0070) are NOT
# self-contained - they assume the schema already exists. The full schema + data lives
# in the "all-databases" dump (e.g. 0069-*-all-databases-*.sql.sql), which also contains
# the mysql system database. We restore ONLY the ruoyi-vue-pro database with
# `mysql --one-database`, so the mysql system tables are left untouched.
#
# If no full dump is found, we fall back to creating the schema from
# sql\00-sql文件\sql\mysql\ruoyi-vue-pro.sql and then applying the incremental files.
param(
    [string]$MysqlContainer = 'txgy-mysql',
    [string]$Database = 'ruoyi-vue-pro'
)

$ErrorActionPreference = 'Stop'

# Resolve the MySQL root password from the running container (set via compose .env),
# falling back to the current process environment.
$rootPw = (docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' $MysqlContainer 2>$null |
    Select-String -Pattern '^MYSQL_ROOT_PASSWORD=' | ForEach-Object { ($_.Line -split '=', 2)[1] })
if ($rootPw) { $env:MYSQL_ROOT_PASSWORD = $rootPw }

$localDir  = $PSScriptRoot
$deployDir = Split-Path -Parent $localDir
$rootDir   = Split-Path -Parent $deployDir

$initDir = Join-Path $deployDir 'mysql\init'
$fullDump = Get-ChildItem -LiteralPath $initDir -Filter '*all-databases*.sql.sql' -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime | Select-Object -Last 1

function Invoke-Mysql {
    param([string]$SqlFile, [string]$ExtraArgs = '')
    $tmp = '/tmp/init_' + [IO.Path]::GetFileNameWithoutExtension($SqlFile) + '.sql'
    docker cp $SqlFile ${MysqlContainer}:$tmp | Out-Null
    $cmd = "mysql -uroot -p'$env:MYSQL_ROOT_PASSWORD' $ExtraArgs $Database < $tmp"
    docker exec $MysqlContainer bash -c $cmd
    docker exec $MysqlContainer rm -f $tmp | Out-Null
}

if ($fullDump) {
    Write-Host "Restoring full '$Database' database from $($fullDump.Name) (--one-database) ..." -ForegroundColor Cyan
    Invoke-Mysql -SqlFile $fullDump.FullName -ExtraArgs '--one-database'
    Write-Host "Done (--one-database keeps mysql system tables untouched)." -ForegroundColor Green
} else {
    Write-Host "Full dump not found - falling back to schema + incremental files." -ForegroundColor Yellow
    $schema = Join-Path $rootDir 'sql\00-sql文件\sql\mysql\ruoyi-vue-pro.sql'
    if (Test-Path -LiteralPath $schema) {
        Write-Host "Creating schema from ruoyi-vue-pro.sql ..." -ForegroundColor Cyan
        Invoke-Mysql -SqlFile $schema
    } else {
        throw "Neither full dump nor schema file found."
    }
    foreach ($f in (Get-ChildItem -LiteralPath $initDir -Filter '*.sql' | Where-Object { $_.Name -notmatch 'all-databases' } | Sort-Object Name)) {
        try { Invoke-Mysql -SqlFile $f.FullName; Write-Host "[OK]   $($f.Name)" }
        catch { Write-Host "[FAIL] $($f.Name) => $_" }
    }
}

# verify
$count = docker exec $MysqlContainer mysql -uroot -p"$env:MYSQL_ROOT_PASSWORD" -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Database'" 2>$null
Write-Host "Tables in $Database: $count" -ForegroundColor Green
