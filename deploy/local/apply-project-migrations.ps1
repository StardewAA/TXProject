# Apply only project-owned menu and permission migrations to an existing local database.
param(
    [string]$MysqlContainer = 'txgy-mysql',
    [string]$Database = 'ruoyi-vue-pro'
)

$ErrorActionPreference = 'Stop'

$localDir = $PSScriptRoot
$deployDir = Split-Path -Parent $localDir
$rootDir = Split-Path -Parent $deployDir
$sqlDir = Join-Path $rootDir 'sql'
$dockerExe = (Get-Command docker -ErrorAction SilentlyContinue).Source
if (-not $dockerExe) {
    $dockerExe = 'C:\Users\35360\AppData\Local\Programs\DockerDesktop\resources\bin\docker.exe'
}
if (-not (Test-Path -LiteralPath $dockerExe)) {
    throw "Docker CLI not found: $dockerExe"
}

if (-not (& $dockerExe inspect $MysqlContainer 2>$null)) {
    throw "MySQL container not found: $MysqlContainer"
}

$rootPw = (& $dockerExe inspect -f '{{range .Config.Env}}{{println .}}{{end}}' $MysqlContainer 2>$null |
    Select-String -Pattern '^MYSQL_ROOT_PASSWORD=' |
    ForEach-Object { ($_.Line -split '=', 2)[1] })
if (-not $rootPw) {
    throw "MYSQL_ROOT_PASSWORD was not found in container $MysqlContainer"
}

function Invoke-MysqlFile {
    param([Parameter(Mandatory)][string]$SqlFile)

    $tmp = '/tmp/project_' + [IO.Path]::GetFileName($SqlFile)
    & $dockerExe cp $SqlFile "${MysqlContainer}:$tmp"
    if ($LASTEXITCODE -ne 0) { throw "Failed to copy $SqlFile into $MysqlContainer" }

    try {
        & $dockerExe exec -e "MYSQL_PWD=$rootPw" $MysqlContainer `
            mysql -uroot $Database -e "source $tmp"
        if ($LASTEXITCODE -ne 0) { throw "Failed to apply $SqlFile" }
    } finally {
        & $dockerExe exec $MysqlContainer rm -f $tmp | Out-Null
    }
}

$migrationNames = @(
    'mall-migrated-wms-shared-warehouse-2026-09-11.sql',
    'mall-migrated-erp-approval-2026-09-20.sql'
)
$migrations = foreach ($migrationName in $migrationNames) {
    $migration = Get-ChildItem -LiteralPath $sqlDir -Recurse -File -Filter $migrationName |
        Select-Object -First 1
    if ($migration) {
        $migration.FullName
    } else {
        throw "Migration not found under $sqlDir`: $migrationName"
    }
}

foreach ($migration in $migrations) {
    Write-Host "Applying $([IO.Path]::GetFileName($migration)) ..." -ForegroundColor Cyan
    Invoke-MysqlFile -SqlFile $migration
    Write-Host "[OK] $([IO.Path]::GetFileName($migration))" -ForegroundColor Green
}

Write-Host 'Project menu migrations applied. Please log out and sign in again to reload menus.' -ForegroundColor Green
