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

# The full dump predates the HRM module and therefore has no hrm_* tables.
# Restore the HRM base schema only when the database has no HRM tables yet;
# the base file contains DROP TABLE statements and must not run on an existing
# database that already contains HRM data.
$hrmTableCount = docker exec $MysqlContainer mysql -uroot -p"$env:MYSQL_ROOT_PASSWORD" -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Database' AND table_name LIKE 'hrm_%'" 2>$null
if ([int]([string]$hrmTableCount).Trim() -eq 0) {
    $hrmBaseSql = Get-ChildItem -LiteralPath $initDir -Filter '0031*hrm*.sql.sql' -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($hrmBaseSql) {
        Write-Host "Applying HRM base schema from $($hrmBaseSql.Name) ..." -ForegroundColor Cyan
        Invoke-Mysql -SqlFile $hrmBaseSql.FullName
        Write-Host "HRM base schema applied." -ForegroundColor Green
    }
}

# Recruitment portal tables and menus are additive and idempotent. Applying
# them here keeps a fresh local database aligned with the checked-in HRM code.
$hrmRecruitmentScripts = @(
    (Join-Path $rootDir 'sql\2026-09-03-hrm-recruit-group-one.sql'),
    (Join-Path $rootDir 'sql\2026-09-03-hrm-recruit-groups-integration.sql'),
    (Join-Path $rootDir 'sql\2026-09-09-qinchuan-hrm-recruit-200.sql')
)
foreach ($hrmSql in $hrmRecruitmentScripts) {
    if (Test-Path -LiteralPath $hrmSql) {
        Invoke-Mysql -SqlFile $hrmSql
        Write-Host "[OK]   $([IO.Path]::GetFileName($hrmSql))" -ForegroundColor Green
    }
}

# Apply the idempotent promotion-threshold extension after either restore path.
# This keeps an existing local database in sync with the Excel-driven growth module.
$promotionScripts = @(
    (Join-Path $rootDir 'sql\training_promotion_threshold.sql'),
    (Join-Path $rootDir 'sql\training_analytics_menu.sql')
)
foreach ($featureSql in $promotionScripts) {
    if (Test-Path -LiteralPath $featureSql) {
        Invoke-Mysql -SqlFile $featureSql
        Write-Host "[OK]   $([IO.Path]::GetFileName($featureSql))" -ForegroundColor Green
    }
}

# The full database dump predates the portal resume feature and is intentionally
# not regenerated for every incremental change. Apply the additive migration
# explicitly so an existing volume receives the table as well as a fresh one.
$personalResumeSql = Join-Path $rootDir 'sql\2026-09-04-hrm-personal-resume.sql'
if (Test-Path -LiteralPath $personalResumeSql) {
    Write-Host "Applying HRM personal resume migration ..." -ForegroundColor Cyan
    Invoke-Mysql -SqlFile $personalResumeSql
    Write-Host "HRM personal resume migration applied." -ForegroundColor Green
}

# Additive resource-center enhancement (document page_count + question category_id).
# Idempotent, so it is safe to run against an existing local database.
$resourceEnhanceSql = Join-Path $rootDir 'sql\2026-09-11-training-doc-page-count-question-category.sql'
if (Test-Path -LiteralPath $resourceEnhanceSql) {
    Write-Host "Applying resource-center page-count / question-category migration ..." -ForegroundColor Cyan
    Invoke-Mysql -SqlFile $resourceEnhanceSql
    Write-Host "Resource-center enhancement migration applied." -ForegroundColor Green
}

# verify
$count = docker exec $MysqlContainer mysql -uroot -p"$env:MYSQL_ROOT_PASSWORD" -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Database'" 2>$null
Write-Host "Tables in ${Database}: $count" -ForegroundColor Green
